package com.durrr.first

import com.durrr.first.network.dto.DailyRecapResponse
import com.durrr.first.network.dto.AssignProductModifiersRequest
import com.durrr.first.network.dto.PaymentBreakdownDto
import com.durrr.first.network.dto.ProductMovementDto
import com.durrr.first.network.dto.RecapRangeDto
import com.durrr.first.network.dto.RecapSummaryResponse
import com.durrr.first.network.dto.ServerMenuCatalogDto
import com.durrr.first.network.dto.ServerMenuItemDto
import com.durrr.first.network.dto.ServerModifierGroupDto
import com.durrr.first.network.dto.ServerModifierOptionDto
import com.durrr.first.network.dto.ServerProductModifierLinkDto
import com.durrr.first.network.dto.TransactionEventAckDto
import com.durrr.first.network.dto.TransactionBatchRequest
import com.durrr.first.network.dto.TransactionBatchResponse
import com.durrr.first.network.dto.TransactionSyncEventDto
import com.durrr.first.network.dto.TransaksiDto
import com.durrr.first.network.dto.UpsertMenuItemRequest
import com.durrr.first.network.dto.UpsertModifierGroupRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val DEFAULT_OUTLET_ID = "default"

@Serializable
data class Customer(
    val uuid: String,
    val name: String,
)

@Serializable
data class MenuItem(
    val id: String,
    val name: String,
    val price: Long,
    val groupId: String? = null,
    val groupName: String? = null,
    val outletId: String = DEFAULT_OUTLET_ID,
)

@Serializable
data class OrderLineModifierInput(
    val optionId: String,
    val optionName: String? = null,
)

@Serializable
data class OrderLineInput(
    val menuId: String,
    val qty: Int,
    val note: String? = null,
    val modifiers: List<OrderLineModifierInput> = emptyList(),
)

@Serializable
data class CreateOrderRequest(
    val customerUuid: String,
    val items: List<OrderLineInput>,
    @SerialName("outlet_id")
    val outletId: String? = null,
    val paymentConfirmation: String? = null,
    val note: String? = null,
)

@Serializable
data class UpdateOrderStatusRequest(
    val status: String,
    @SerialName("outlet_id")
    val outletId: String? = null,
)

@Serializable
data class OrderLineModifier(
    val optionId: String,
    val groupId: String,
    val groupName: String,
    val optionName: String,
    val priceDelta: Long = 0,
)

@Serializable
data class OrderLine(
    val id: String,
    val menuId: String,
    val itemName: String,
    val qty: Int,
    val price: Long,
    val lineTotal: Long,
    val note: String? = null,
    val modifiers: List<OrderLineModifier> = emptyList(),
)

@Serializable
data class OrderView(
    val id: String,
    val customerUuid: String,
    val customerName: String,
    val status: String,
    val outletId: String = DEFAULT_OUTLET_ID,
    val paymentConfirmation: String? = null,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val total: Long,
    val items: List<OrderLine>,
)

object ServerDatabase {
    private const val LIBSQL_DRIVER = "com.dbeaver.jdbc.driver.libsql.LibSqlDriver"
    private const val SQLITE_DRIVER = "org.sqlite.JDBC"

    private val dbPath: String = EnvConfig
        .get("SUCASH_SERVER_DB_PATH", "data/sucash-server.db")
        .orEmpty()
    private val tursoDatabaseUrl: String = EnvConfig
        .get("TURSO_DATABASE_URL", "")
        .orEmpty()
        .trim()
    private val tursoAuthToken: String = EnvConfig
        .get("TURSO_AUTH_TOKEN", "")
        .orEmpty()
        .trim()
    private val migrateLocalSqliteToTurso: Boolean = EnvConfig
        .get("SUCASH_MIGRATE_LOCAL_SQLITE_TO_TURSO", "false")
        .orEmpty()
        .trim()
        .let { it.equals("true", ignoreCase = true) || it == "1" }
    private val migrationSourceSqlitePath: String = EnvConfig
        .get("SUCASH_MIGRATION_SOURCE_DB_PATH", dbPath)
        .orEmpty()
        .trim()
    private val usingTurso: Boolean = tursoDatabaseUrl.isNotBlank()
    private val jdbcUrl: String = if (usingTurso) toLibsqlJdbcUrl(tursoDatabaseUrl) else "jdbc:sqlite:$dbPath"

    private val syncJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private data class DateFilterSpec(
        val sql: String,
        val bind: (PreparedStatement, Int) -> Int,
    )

    private data class ModifierGroupRow(
        val id: String,
        val name: String,
        val selectionType: String,
        val isRequired: Boolean,
        val maxSelection: Int,
    )

    private data class ModifierOptionRow(
        val id: String,
        val groupId: String,
        val name: String,
        val priceDelta: Long,
        val order: Int,
        val isDefault: Boolean,
    )

    fun init() {
        println("ServerDatabase init -> usingTurso=$usingTurso, jdbcUrl=${jdbcUrl.maskJdbcSecrets()}")
        if (!usingTurso) {
            val parent = Path.of(dbPath).parent
            if (parent != null) {
                Files.createDirectories(parent)
            }
        }
        runCatching {
            Class.forName(if (usingTurso) LIBSQL_DRIVER else SQLITE_DRIVER)
        }
        withConnection { connection ->
            createSchema(connection)
            migrateLocalSqliteDataToTursoIfEnabled(connection)
            migrateCompletedOrdersToTransaksiTransactional(connection)
            seedTableCustomersTransactional(connection, count = 10, outletId = DEFAULT_OUTLET_ID)
        }
    }

    fun listCustomers(): List<Customer> = withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT uuid, name
            FROM customer
            ORDER BY name ASC
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            Customer(
                                uuid = rs.getString("uuid"),
                                name = rs.getString("name"),
                            )
                        )
                    }
                }
            }
        }
    }

    fun seedTableCustomers(
        count: Int = 10,
        outletId: String = DEFAULT_OUTLET_ID,
    ): List<Customer> = withConnection { connection ->
        seedTableCustomersTransactional(connection, count, normalizeOutletId(outletId))
    }

    fun resetOutletData(outletId: String = DEFAULT_OUTLET_ID): Boolean = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        if (!usingTurso) connection.autoCommit = false
        try {
            connection.prepareStatement(
                "DELETE FROM order_item WHERE order_id IN (SELECT id FROM order_header WHERE outlet_id = ?)"
            ).use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM order_header WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }

            connection.prepareStatement("DELETE FROM pembayaran WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM transaksi_detail WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM transaksi_header WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }

            connection.prepareStatement("DELETE FROM product_modifier_group WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM modifier_option WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM modifier_group WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }

            connection.prepareStatement("DELETE FROM menu_item WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement(
                "DELETE FROM customer WHERE uuid NOT IN (SELECT DISTINCT customer_uuid FROM order_header)"
            ).use { statement ->
                executeWrite(statement)
            }

            connection.prepareStatement("DELETE FROM processed_event WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM stock_ledger WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM stock_threshold WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM stock_item_balance WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWrite(statement)
            }
            connection.prepareStatement("DELETE FROM cash_movement WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWriteAllowMissingTable(statement, "cash_movement")
            }
            connection.prepareStatement("DELETE FROM cash_session WHERE outlet_id = ?").use { statement ->
                statement.setString(1, scopedOutletId)
                executeWriteAllowMissingTable(statement, "cash_session")
            }

            seedTableCustomersTransactional(connection, count = 10, outletId = scopedOutletId)

            if (!usingTurso) connection.commit()
            true
        } catch (t: Throwable) {
            if (!usingTurso) connection.rollback()
            throw t
        } finally {
            if (!usingTurso) connection.autoCommit = true
        }
    }

    fun findCustomer(uuid: String): Customer? = withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT uuid, name
            FROM customer
            WHERE uuid = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, uuid)
            statement.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                Customer(
                    uuid = rs.getString("uuid"),
                    name = rs.getString("name"),
                )
            }
        }
    }

    fun listMenu(outletId: String = DEFAULT_OUTLET_ID): List<MenuItem> = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        connection.prepareStatement(
            """
            SELECT id, name, price, group_id, group_name, outlet_id
            FROM menu_item
            WHERE outlet_id = ?
            ORDER BY name ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, scopedOutletId)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val itemName = rs.getString("name")
                        val (resolvedGroupId, resolvedGroupName) = normalizeMenuGroup(
                            groupId = rs.getString("group_id"),
                            groupName = rs.getString("group_name"),
                            itemName = itemName,
                        )
                        add(
                            MenuItem(
                                id = rs.getString("id"),
                                name = itemName,
                                price = rs.getLong("price"),
                                groupId = resolvedGroupId,
                                groupName = resolvedGroupName,
                                outletId = rs.getString("outlet_id") ?: DEFAULT_OUTLET_ID,
                            )
                        )
                    }
                }
            }
        }
    }

    fun menuCatalog(outletId: String = DEFAULT_OUTLET_ID): ServerMenuCatalogDto = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        val items = listMenuTransactional(connection, scopedOutletId).map {
            ServerMenuItemDto(
                id = it.id,
                name = it.name,
                price = it.price,
                groupId = it.groupId,
                groupName = it.groupName,
                outletId = it.outletId,
            )
        }
        val groups = listModifierGroupsTransactional(connection, scopedOutletId)
        val optionsByGroup = listModifierOptionsTransactional(connection, scopedOutletId).groupBy { it.groupId }
        val links = listProductModifierLinksTransactional(connection, scopedOutletId)
            .map { (itemId, groupIds) ->
                ServerProductModifierLinkDto(
                    itemId = itemId,
                    modifierGroupIds = groupIds.sorted(),
                )
            }
            .sortedBy { it.itemId }
        ServerMenuCatalogDto(
            items = items,
            modifierGroups = groups.map { group ->
                ServerModifierGroupDto(
                    id = group.id,
                    name = group.name,
                    selectionType = group.selectionType,
                    isRequired = group.isRequired,
                    maxSelection = group.maxSelection,
                    options = optionsByGroup[group.id].orEmpty()
                        .sortedBy { it.order }
                        .map { option ->
                            ServerModifierOptionDto(
                                id = option.id,
                                name = option.name,
                                priceDelta = option.priceDelta,
                                order = option.order,
                                isDefault = option.isDefault,
                            )
                        },
                )
            },
            productModifierLinks = links,
        )
    }

    fun upsertMenu(request: UpsertMenuItemRequest): MenuItem? = withConnection { connection ->
        val name = request.name.trim()
        if (name.isBlank() || request.price < 0) return@withConnection null
        val outletId = normalizeOutletId(request.outletId)
        val id = request.id?.trim().takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString()
        val groupId = request.groupId?.trim()?.takeIf { it.isNotBlank() }
        val groupName = request.groupName?.trim()?.takeIf { it.isNotBlank() }
        val now = Instant.now().toString()
        connection.prepareStatement(
            """
            INSERT INTO menu_item(id, name, price, created_at, group_id, group_name, outlet_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                price = excluded.price,
                group_id = excluded.group_id,
                group_name = excluded.group_name,
                outlet_id = excluded.outlet_id
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, name)
            statement.setLong(3, request.price)
            statement.setString(4, now)
            statement.setString(5, groupId)
            statement.setString(6, groupName)
            statement.setString(7, outletId)
            statement.executeUpdate()
        }
        MenuItem(id = id, name = name, price = request.price, groupId = groupId, groupName = groupName, outletId = outletId)
    }

    fun deleteMenu(menuId: String, outletId: String = DEFAULT_OUTLET_ID): Boolean = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        connection.prepareStatement(
            "DELETE FROM menu_item WHERE id = ? AND outlet_id = ?"
        ).use { statement ->
            statement.setString(1, menuId)
            statement.setString(2, scopedOutletId)
            statement.executeUpdate() > 0
        }
    }

    fun upsertModifierGroup(request: UpsertModifierGroupRequest): Boolean = withConnection { connection ->
        val groupId = request.id.trim()
        val name = request.name.trim()
        if (groupId.isBlank() || name.isBlank()) return@withConnection false
        val outletId = normalizeOutletId(request.outletId)
        val selectionType = request.selectionType.trim().uppercase().ifBlank { "SINGLE" }
        val maxSelection = request.maxSelection.coerceAtLeast(1)
        if (!usingTurso) connection.autoCommit = false
        try {
            connection.prepareStatement(
                """
                INSERT INTO modifier_group(
                    id_modifier_group, nama, selection_type, is_required, max_selection, outlet_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id_modifier_group) DO UPDATE SET
                    nama = excluded.nama,
                    selection_type = excluded.selection_type,
                    is_required = excluded.is_required,
                    max_selection = excluded.max_selection,
                    outlet_id = excluded.outlet_id
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, groupId)
                statement.setString(2, name)
                statement.setString(3, selectionType)
                statement.setLong(4, if (request.isRequired) 1L else 0L)
                statement.setLong(5, maxSelection.toLong())
                statement.setString(6, outletId)
                statement.executeUpdate()
            }

            connection.prepareStatement(
                "DELETE FROM modifier_option WHERE id_modifier_group = ? AND outlet_id = ?"
            ).use { statement ->
                statement.setString(1, groupId)
                statement.setString(2, outletId)
                statement.executeUpdate()
            }

            connection.prepareStatement(
                """
                INSERT INTO modifier_option(
                    id_modifier_option, id_modifier_group, nama, price_delta, urutan, is_default, outlet_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                request.options.forEachIndexed { index, option ->
                    val optionId = option.id.trim().ifBlank { "$groupId-opt-$index" }
                    statement.setString(1, optionId)
                    statement.setString(2, groupId)
                    statement.setString(3, option.name.trim().ifBlank { "Option ${index + 1}" })
                    statement.setLong(4, option.priceDelta)
                    statement.setLong(5, option.order.toLong())
                    statement.setLong(6, if (option.isDefault) 1L else 0L)
                    statement.setString(7, outletId)
                    if (usingTurso) {
                        statement.executeUpdate()
                    } else {
                        statement.addBatch()
                    }
                }
                if (!usingTurso && request.options.isNotEmpty()) {
                    statement.executeBatch()
                }
            }
            if (!usingTurso) connection.commit()
            true
        } catch (t: Throwable) {
            if (!usingTurso) connection.rollback()
            throw t
        } finally {
            if (!usingTurso) connection.autoCommit = true
        }
    }

    fun assignProductModifiers(
        itemId: String,
        request: AssignProductModifiersRequest,
    ): Boolean = withConnection { connection ->
        val scopedItemId = itemId.trim()
        if (scopedItemId.isBlank()) return@withConnection false
        val outletId = normalizeOutletId(request.outletId)
        if (!hasMenuItemTransactional(connection, scopedItemId, outletId)) return@withConnection false
        if (!usingTurso) connection.autoCommit = false
        try {
            connection.prepareStatement(
                "DELETE FROM product_modifier_group WHERE id_item = ? AND outlet_id = ?"
            ).use { statement ->
                statement.setString(1, scopedItemId)
                statement.setString(2, outletId)
                statement.executeUpdate()
            }

            connection.prepareStatement(
                "INSERT OR REPLACE INTO product_modifier_group(id_item, id_modifier_group, outlet_id) VALUES (?, ?, ?)"
            ).use { statement ->
                request.modifierGroupIds
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { groupId ->
                        statement.setString(1, scopedItemId)
                        statement.setString(2, groupId)
                        statement.setString(3, outletId)
                        if (usingTurso) {
                            statement.executeUpdate()
                        } else {
                            statement.addBatch()
                        }
                    }
                if (!usingTurso) {
                    statement.executeBatch()
                }
            }
            if (!usingTurso) connection.commit()
            true
        } catch (t: Throwable) {
            if (!usingTurso) connection.rollback()
            throw t
        } finally {
            if (!usingTurso) connection.autoCommit = true
        }
    }

    fun createOrder(request: CreateOrderRequest): OrderView? = withConnection { connection ->
        if (!usingTurso) connection.autoCommit = false
        try {
            val outletId = normalizeOutletId(request.outletId)
            if (request.customerUuid.isBlank()) return@withConnection null
            val customer = ensureCustomerTransactional(connection, request.customerUuid)

            val menuById = listMenuTransactional(connection, outletId).associateBy { it.id }
            val modifierGroupsById = listModifierGroupsTransactional(connection, outletId).associateBy { it.id }
            val modifierOptionsById = listModifierOptionsTransactional(connection, outletId).associateBy { it.id }
            val linkedGroupIdsByItem = listProductModifierLinksTransactional(connection, outletId)
            if (request.items.isEmpty()) {
                return@withConnection null
            }

            val orderLines = buildList {
                request.items.forEach { input ->
                    val menu = menuById[input.menuId] ?: return@withConnection null
                    if (input.qty <= 0) return@withConnection null
                    val linkedGroupIds = linkedGroupIdsByItem[menu.id].orEmpty().toSet()
                    val resolvedModifiers = input.modifiers.map { raw ->
                        val optionId = raw.optionId.trim()
                        if (optionId.isBlank()) return@withConnection null
                        val option = modifierOptionsById[optionId] ?: return@withConnection null
                        if (option.groupId !in linkedGroupIds) return@withConnection null
                        val group = modifierGroupsById[option.groupId] ?: return@withConnection null
                        OrderLineModifier(
                            optionId = option.id,
                            groupId = group.id,
                            groupName = group.name,
                            optionName = option.name,
                            priceDelta = option.priceDelta,
                        )
                    }
                    val modifiersByGroup = resolvedModifiers.groupBy { it.groupId }
                    linkedGroupIds.forEach { groupId ->
                        val group = modifierGroupsById[groupId] ?: return@withConnection null
                        val selected = modifiersByGroup[groupId].orEmpty()
                        if (group.isRequired && selected.isEmpty()) return@withConnection null
                        if (group.selectionType.equals("SINGLE", ignoreCase = true) && selected.size > 1) {
                            return@withConnection null
                        }
                        if (selected.size > group.maxSelection.coerceAtLeast(1)) return@withConnection null
                    }
                    val modifierTotal = resolvedModifiers.sumOf { it.priceDelta }
                    val linePrice = menu.price + modifierTotal
                    val lineNote = buildString {
                        val rawNote = input.note?.trim().orEmpty()
                        if (rawNote.isNotBlank()) append(rawNote)
                        val modifierSummary = resolvedModifiers.joinToString(", ") { "${it.groupName}: ${it.optionName}" }
                        if (modifierSummary.isNotBlank()) {
                            if (isNotBlank()) append(" | ")
                            append(modifierSummary)
                        }
                    }.ifBlank { null }
                    add(
                        OrderLine(
                            id = UUID.randomUUID().toString(),
                            menuId = menu.id,
                            itemName = menu.name,
                            qty = input.qty,
                            price = linePrice,
                            lineTotal = linePrice * input.qty,
                            note = lineNote,
                            modifiers = resolvedModifiers,
                        )
                    )
                }
            }

            val now = Instant.now().toString()
            val orderId = UUID.randomUUID().toString()
            val total = orderLines.sumOf { it.lineTotal }
            val paymentConfirmation = request.paymentConfirmation
                ?.trim()
                ?.uppercase()
                ?.takeIf { it == "CASHIER" }

            connection.prepareStatement(
                """
                INSERT INTO order_header(
                    id, customer_uuid, status, payment_confirmation, note, created_at, updated_at, total, outlet_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, orderId)
                statement.setString(2, customer.uuid)
                statement.setString(3, "NEW")
                statement.setString(4, paymentConfirmation)
                statement.setString(5, request.note)
                statement.setString(6, now)
                statement.setString(7, now)
                statement.setLong(8, total)
                statement.setString(9, outletId)
                statement.executeUpdate()
            }

            connection.prepareStatement(
                """
                INSERT INTO order_item(id, order_id, menu_id, item_name, qty, price, line_total, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                orderLines.forEach { line ->
                    statement.setString(1, line.id)
                    statement.setString(2, orderId)
                    statement.setString(3, line.menuId)
                    statement.setString(4, line.itemName)
                    statement.setInt(5, line.qty)
                    statement.setLong(6, line.price)
                    statement.setLong(7, line.lineTotal)
                    statement.setString(8, line.note)
                    if (usingTurso) {
                        statement.executeUpdate()
                    } else {
                        statement.addBatch()
                    }
                }
                if (!usingTurso) {
                    statement.executeBatch()
                }
            }

            if (!usingTurso) connection.commit()
            OrderView(
                id = orderId,
                customerUuid = customer.uuid,
                customerName = customer.name,
                status = "NEW",
                outletId = outletId,
                paymentConfirmation = paymentConfirmation,
                note = request.note,
                createdAt = now,
                updatedAt = now,
                total = total,
                items = orderLines,
            )
        } catch (t: Throwable) {
            if (!usingTurso) connection.rollback()
            throw t
        } finally {
            if (!usingTurso) connection.autoCommit = true
        }
    }

    fun listOrders(
        statuses: Set<String>,
        outletId: String = DEFAULT_OUTLET_ID,
    ): List<OrderView> = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        val statusFilter = statuses
            .filter { it.isNotBlank() }
            .map { it.trim().uppercase() }
            .toSet()

        val sql = buildString {
            append(
                """
                SELECT oh.id,
                       oh.customer_uuid,
                       c.name AS customer_name,
                       oh.status,
                       oh.outlet_id,
                       oh.payment_confirmation,
                       oh.note,
                       oh.created_at,
                       oh.updated_at,
                       oh.total
                FROM order_header oh
                INNER JOIN customer c ON c.uuid = oh.customer_uuid
                """.trimIndent()
            )
            append(" WHERE oh.outlet_id = ?")
            if (statusFilter.isNotEmpty()) {
                append(" AND oh.status IN (${statusFilter.joinToString(",") { "?" }})")
            }
            append(" ORDER BY oh.created_at DESC")
        }

        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, scopedOutletId)
            statusFilter.forEachIndexed { index, status ->
                statement.setString(index + 2, status)
            }
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val orderId = rs.getString("id")
                        add(
                            OrderView(
                                id = orderId,
                                customerUuid = rs.getString("customer_uuid"),
                                customerName = rs.getString("customer_name"),
                                status = rs.getString("status"),
                                outletId = rs.getString("outlet_id") ?: DEFAULT_OUTLET_ID,
                                paymentConfirmation = rs.getString("payment_confirmation"),
                                note = rs.getString("note"),
                                createdAt = rs.getString("created_at"),
                                updatedAt = rs.getString("updated_at"),
                                total = rs.getLong("total"),
                                items = listOrderLinesTransactional(connection, orderId),
                            )
                        )
                    }
                }
            }
        }
    }

    fun updateOrderStatus(
        orderId: String,
        status: String,
        outletId: String = DEFAULT_OUTLET_ID,
    ): OrderView? = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        if (!usingTurso) connection.autoCommit = false
        try {
            val normalizedStatus = status.trim().uppercase()
            if (normalizedStatus !in setOf("NEW", "ACCEPTED", "PREPARING", "SERVED", "DONE", "CANCELLED")) {
                return@withConnection null
            }

            val now = Instant.now().toString()
            val updated = connection.prepareStatement(
                """
                UPDATE order_header
                SET status = ?, updated_at = ?
                WHERE id = ? AND outlet_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, normalizedStatus)
                statement.setString(2, now)
                statement.setString(3, orderId)
                statement.setString(4, scopedOutletId)
                statement.executeUpdate()
            }

            if (updated == 0) {
                if (!usingTurso) connection.rollback()
                return@withConnection null
            }
            if (normalizedStatus in COMPLETED_ORDER_STATUSES) {
                materializeCompletedOrderToTransaksiTransactional(connection, orderId, scopedOutletId)
            }

            if (!usingTurso) connection.commit()
            findOrderByIdTransactional(connection, orderId, scopedOutletId)
        } catch (t: Throwable) {
            if (!usingTurso) connection.rollback()
            throw t
        } finally {
            if (!usingTurso) connection.autoCommit = true
        }
    }

    fun syncTransactionsBatch(request: TransactionBatchRequest): TransactionBatchResponse = withConnection { connection ->
        if (!usingTurso) connection.autoCommit = false
        try {
            val outletId = normalizeOutletId(request.outletId)
            val acks = mutableListOf<TransactionEventAckDto>()
            val events = normalizeTransactionSyncEvents(request)
            events.forEach { event ->
                val eventId = event.eventId.trim()
                if (eventId.isBlank()) {
                    acks += TransactionEventAckDto(eventId = "", status = "REJECTED", reason = "Missing event_id")
                    return@forEach
                }
                val entityType = event.entityType.trim().uppercase()
                if (entityType != "TRANSAKSI_CHECKOUT") {
                    acks += TransactionEventAckDto(eventId = eventId, status = "REJECTED", reason = "Unsupported entity_type")
                    return@forEach
                }
                val operation = event.op.trim().uppercase()
                if (operation !in setOf("UPSERT", "CREATE", "SYNC")) {
                    acks += TransactionEventAckDto(eventId = eventId, status = "REJECTED", reason = "Unsupported op")
                    return@forEach
                }
                val transaksi = runCatching {
                    syncJson.decodeFromString<TransaksiDto>(event.payloadJson)
                }.getOrNull()
                if (transaksi == null) {
                    acks += TransactionEventAckDto(eventId = eventId, status = "REJECTED", reason = "Invalid payload_json")
                    return@forEach
                }
                val scopedEventId = "$outletId::$eventId"
                if (isProcessedTransactional(connection, scopedEventId, outletId)) {
                    acks += TransactionEventAckDto(eventId = eventId, status = "ACCEPTED", reason = "Already processed")
                    return@forEach
                }
                connection.prepareStatement(
                    "INSERT INTO processed_event(event_id, outlet_id, processed_at) VALUES (?, ?, ?)"
                ).use { statement ->
                    statement.setString(1, scopedEventId)
                    statement.setString(2, outletId)
                    statement.setString(3, Instant.now().toString())
                    statement.executeUpdate()
                }
                upsertTransaksiTransactional(connection, transaksi, outletId)
                acks += TransactionEventAckDto(eventId = eventId, status = "ACCEPTED")
            }
            if (!usingTurso) connection.commit()
            val accepted = acks.filter { it.status == "ACCEPTED" }.map { it.eventId }
            val rejected = acks.filter { it.status == "REJECTED" }.map { it.eventId }
            TransactionBatchResponse(accepted = accepted, rejected = rejected, acks = acks)
        } catch (t: Throwable) {
            if (!usingTurso) connection.rollback()
            throw t
        } finally {
            if (!usingTurso) connection.autoCommit = true
        }
    }

    fun dailyRecap(date: String, outletId: String = DEFAULT_OUTLET_ID): DailyRecapResponse = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        val safeDate = normalizeAnchorDate(date)
        val transaksiAggregate = connection.prepareStatement(
            """
            SELECT COUNT(*) AS transaksi_count,
                   COALESCE(SUM(total), 0) AS gross_total
            FROM transaksi_header
            WHERE substr(created_at, 1, 10) = ?
              AND outlet_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, safeDate)
            statement.setString(2, scopedOutletId)
            statement.executeQuery().use { rs ->
                var count = 0
                var gross = 0L
                if (rs.next()) {
                    count = rs.getInt("transaksi_count")
                    gross = rs.getLong("gross_total")
                }
                count to gross
            }
        }
        DailyRecapResponse(
            date = safeDate,
            transaksiCount = transaksiAggregate.first,
            grossTotal = transaksiAggregate.second,
        )
    }

    fun recapSummary(
        range: String,
        date: String,
        fromDate: String? = null,
        toDate: String? = null,
        outletId: String = DEFAULT_OUTLET_ID,
    ): RecapSummaryResponse = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        val safeRange = parseRecapRange(range)
        val safeAnchorDate = normalizeAnchorDate(date)
        val transaksiDateFilter = buildDateFilter(
            columnExpression = "t.created_at",
            range = safeRange,
            anchorDate = safeAnchorDate,
            fromDate = fromDate,
            toDate = toDate,
        )

        val transaksiAggregate = connection.prepareStatement(
            """
            SELECT COUNT(*) AS transaksi_count,
                   COALESCE(SUM(t.total), 0) AS gross_total,
                   COALESCE(SUM(t.discount_plus), 0) AS total_discount
            FROM transaksi_header t
            WHERE t.outlet_id = ?
              AND ${transaksiDateFilter.sql}
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, scopedOutletId)
            transaksiDateFilter.bind(statement, 2)
            statement.executeQuery().use { rs ->
                if (!rs.next()) {
                    Triple(0, 0L, 0L)
                } else {
                    Triple(
                        rs.getInt("transaksi_count"),
                        rs.getLong("gross_total"),
                        rs.getLong("total_discount"),
                    )
                }
            }
        }

        val paymentBreakdown = queryPaymentBreakdown(
            connection = connection,
            outletId = scopedOutletId,
            dateFilter = transaksiDateFilter,
        )

        val topItems = queryProductMovers(
            connection = connection,
            outletId = scopedOutletId,
            dateFilter = transaksiDateFilter,
            ascending = false,
            limit = 5,
        )

        val slowItems = queryProductMovers(
            connection = connection,
            outletId = scopedOutletId,
            dateFilter = transaksiDateFilter,
            ascending = true,
            limit = 5,
        )

        val transaksiCount = transaksiAggregate.first
        val grossTotal = transaksiAggregate.second
        val totalDiscount = transaksiAggregate.third
        val averageTicket = if (transaksiCount > 0) grossTotal / transaksiCount else 0L
        RecapSummaryResponse(
            anchorDate = safeAnchorDate,
            range = safeRange,
            transaksiCount = transaksiCount,
            grossTotal = grossTotal,
            totalDiscount = totalDiscount,
            averageTicket = averageTicket,
            paymentBreakdown = paymentBreakdown,
            topItems = topItems,
            slowItems = slowItems,
        )
    }

    private fun createSchema(connection: Connection) {
        if (usingTurso) {
            createSchemaForTurso(connection)
            return
        }
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS customer (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS menu_item (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    price INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    group_id TEXT,
                    group_name TEXT,
                    outlet_id TEXT NOT NULL DEFAULT 'default'
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS modifier_group (
                    id_modifier_group TEXT PRIMARY KEY,
                    nama TEXT NOT NULL,
                    selection_type TEXT NOT NULL DEFAULT 'SINGLE',
                    is_required INTEGER NOT NULL DEFAULT 0,
                    max_selection INTEGER NOT NULL DEFAULT 1,
                    outlet_id TEXT NOT NULL DEFAULT 'default'
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS modifier_option (
                    id_modifier_option TEXT PRIMARY KEY,
                    id_modifier_group TEXT NOT NULL,
                    nama TEXT NOT NULL,
                    price_delta INTEGER NOT NULL DEFAULT 0,
                    urutan INTEGER NOT NULL DEFAULT 0,
                    is_default INTEGER NOT NULL DEFAULT 0,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    FOREIGN KEY (id_modifier_group) REFERENCES modifier_group(id_modifier_group) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS product_modifier_group (
                    id_item TEXT NOT NULL,
                    id_modifier_group TEXT NOT NULL,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    PRIMARY KEY (id_item, id_modifier_group, outlet_id),
                    FOREIGN KEY (id_item) REFERENCES menu_item(id) ON DELETE CASCADE,
                    FOREIGN KEY (id_modifier_group) REFERENCES modifier_group(id_modifier_group) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS order_header (
                    id TEXT PRIMARY KEY,
                    customer_uuid TEXT NOT NULL,
                    status TEXT NOT NULL,
                    payment_confirmation TEXT,
                    note TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    total INTEGER NOT NULL,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    FOREIGN KEY (customer_uuid) REFERENCES customer(uuid)
                )
                """.trimIndent()
            )
            // Backward-compatible migration for existing dev DBs.
            runCatching {
                statement.execute("ALTER TABLE order_header ADD COLUMN payment_confirmation TEXT")
            }
            runCatching {
                statement.execute("ALTER TABLE menu_item ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'")
            }
            runCatching {
                statement.execute("ALTER TABLE menu_item ADD COLUMN group_id TEXT")
            }
            runCatching {
                statement.execute("ALTER TABLE menu_item ADD COLUMN group_name TEXT")
            }
            runCatching {
                statement.execute("ALTER TABLE order_header ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'")
            }
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS order_item (
                    id TEXT PRIMARY KEY,
                    order_id TEXT NOT NULL,
                    menu_id TEXT NOT NULL,
                    item_name TEXT NOT NULL,
                    qty INTEGER NOT NULL,
                    price INTEGER NOT NULL,
                    line_total INTEGER NOT NULL,
                    note TEXT,
                    FOREIGN KEY (order_id) REFERENCES order_header(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            runCatching {
                statement.execute("ALTER TABLE order_item ADD COLUMN note TEXT")
            }
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS transaksi_header (
                    id TEXT PRIMARY KEY,
                    created_at TEXT NOT NULL,
                    meja TEXT,
                    cashier_id TEXT,
                    cashier_name TEXT,
                    discount_plus INTEGER NOT NULL,
                    tax INTEGER NOT NULL,
                    service_charge INTEGER NOT NULL,
                    rounding INTEGER NOT NULL,
                    total INTEGER NOT NULL,
                    outlet_id TEXT NOT NULL DEFAULT 'default'
                )
                """.trimIndent()
            )
            runCatching {
                statement.execute("ALTER TABLE transaksi_header ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'")
            }
            runCatching {
                statement.execute("ALTER TABLE transaksi_header ADD COLUMN cashier_id TEXT")
            }
            runCatching {
                statement.execute("ALTER TABLE transaksi_header ADD COLUMN cashier_name TEXT")
            }
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS transaksi_detail (
                    id TEXT PRIMARY KEY,
                    transaksi_id TEXT NOT NULL,
                    item_id TEXT,
                    item_name TEXT NOT NULL,
                    qty INTEGER NOT NULL,
                    price INTEGER NOT NULL,
                    discount INTEGER NOT NULL,
                    total INTEGER NOT NULL,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    FOREIGN KEY (transaksi_id) REFERENCES transaksi_header(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            runCatching {
                statement.execute("ALTER TABLE transaksi_detail ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'")
            }
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS pembayaran (
                    id TEXT PRIMARY KEY,
                    transaksi_id TEXT NOT NULL,
                    paid_at TEXT NOT NULL,
                    amount_paid INTEGER NOT NULL,
                    change_amount INTEGER NOT NULL,
                    payment_type_id TEXT NOT NULL,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    FOREIGN KEY (transaksi_id) REFERENCES transaksi_header(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            runCatching {
                statement.execute("ALTER TABLE pembayaran ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'")
            }
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS stock_item_balance (
                    item_id TEXT NOT NULL,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    qty_on_hand INTEGER NOT NULL DEFAULT 0,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (item_id, outlet_id)
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS stock_ledger (
                    ledger_id TEXT PRIMARY KEY,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    item_id TEXT NOT NULL,
                    movement_type TEXT NOT NULL,
                    qty_delta INTEGER NOT NULL,
                    reference_type TEXT,
                    reference_id TEXT,
                    reason TEXT,
                    created_by TEXT,
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS stock_threshold (
                    item_id TEXT NOT NULL,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    min_qty INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (item_id, outlet_id)
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS processed_event (
                    event_id TEXT PRIMARY KEY,
                    outlet_id TEXT NOT NULL DEFAULT 'default',
                    processed_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            runCatching {
                statement.execute("ALTER TABLE processed_event ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'")
            }
            statement.execute("CREATE INDEX IF NOT EXISTS idx_menu_item_outlet_name ON menu_item(outlet_id, name)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_modifier_group_outlet_name ON modifier_group(outlet_id, nama)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_modifier_option_group_order ON modifier_option(id_modifier_group, outlet_id, urutan)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_product_modifier_item_outlet ON product_modifier_group(id_item, outlet_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_order_header_outlet_status_date ON order_header(outlet_id, status, created_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transaksi_header_outlet_date ON transaksi_header(outlet_id, created_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_pembayaran_outlet_date ON pembayaran(outlet_id, paid_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_processed_event_outlet_event ON processed_event(outlet_id, event_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_balance_outlet_qty ON stock_item_balance(outlet_id, qty_on_hand)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_ledger_outlet_item_date ON stock_ledger(outlet_id, item_id, created_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_threshold_outlet_item ON stock_threshold(outlet_id, item_id)")
        }
    }

    private fun createSchemaForTurso(connection: Connection) {
        // DBeaver libsql driver (1.0.x) can fail on Statement.execute(...) with legacy HTTP
        // when schema bootstrap is sent as interactive-style batches. Use prepared statements
        // one-by-one to keep startup compatible with Turso/libSQL remote.
        val createStatements = listOf(
            """
            CREATE TABLE IF NOT EXISTS customer (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS menu_item (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                price INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                group_id TEXT,
                group_name TEXT,
                outlet_id TEXT NOT NULL DEFAULT 'default'
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS modifier_group (
                id_modifier_group TEXT PRIMARY KEY,
                nama TEXT NOT NULL,
                selection_type TEXT NOT NULL DEFAULT 'SINGLE',
                is_required INTEGER NOT NULL DEFAULT 0,
                max_selection INTEGER NOT NULL DEFAULT 1,
                outlet_id TEXT NOT NULL DEFAULT 'default'
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS modifier_option (
                id_modifier_option TEXT PRIMARY KEY,
                id_modifier_group TEXT NOT NULL,
                nama TEXT NOT NULL,
                price_delta INTEGER NOT NULL DEFAULT 0,
                urutan INTEGER NOT NULL DEFAULT 0,
                is_default INTEGER NOT NULL DEFAULT 0,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                FOREIGN KEY (id_modifier_group) REFERENCES modifier_group(id_modifier_group) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS product_modifier_group (
                id_item TEXT NOT NULL,
                id_modifier_group TEXT NOT NULL,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                PRIMARY KEY (id_item, id_modifier_group, outlet_id),
                FOREIGN KEY (id_item) REFERENCES menu_item(id) ON DELETE CASCADE,
                FOREIGN KEY (id_modifier_group) REFERENCES modifier_group(id_modifier_group) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS order_header (
                id TEXT PRIMARY KEY,
                customer_uuid TEXT NOT NULL,
                status TEXT NOT NULL,
                payment_confirmation TEXT,
                note TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                total INTEGER NOT NULL,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                FOREIGN KEY (customer_uuid) REFERENCES customer(uuid)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS order_item (
                id TEXT PRIMARY KEY,
                order_id TEXT NOT NULL,
                menu_id TEXT NOT NULL,
                item_name TEXT NOT NULL,
                qty INTEGER NOT NULL,
                price INTEGER NOT NULL,
                line_total INTEGER NOT NULL,
                note TEXT,
                FOREIGN KEY (order_id) REFERENCES order_header(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS transaksi_header (
                id TEXT PRIMARY KEY,
                created_at TEXT NOT NULL,
                meja TEXT,
                cashier_id TEXT,
                cashier_name TEXT,
                discount_plus INTEGER NOT NULL,
                tax INTEGER NOT NULL,
                service_charge INTEGER NOT NULL,
                rounding INTEGER NOT NULL,
                total INTEGER NOT NULL,
                outlet_id TEXT NOT NULL DEFAULT 'default'
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS transaksi_detail (
                id TEXT PRIMARY KEY,
                transaksi_id TEXT NOT NULL,
                item_id TEXT,
                item_name TEXT NOT NULL,
                qty INTEGER NOT NULL,
                price INTEGER NOT NULL,
                discount INTEGER NOT NULL,
                total INTEGER NOT NULL,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                FOREIGN KEY (transaksi_id) REFERENCES transaksi_header(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS pembayaran (
                id TEXT PRIMARY KEY,
                transaksi_id TEXT NOT NULL,
                paid_at TEXT NOT NULL,
                amount_paid INTEGER NOT NULL,
                change_amount INTEGER NOT NULL,
                payment_type_id TEXT NOT NULL,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                FOREIGN KEY (transaksi_id) REFERENCES transaksi_header(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS stock_item_balance (
                item_id TEXT NOT NULL,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                qty_on_hand INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL,
                PRIMARY KEY (item_id, outlet_id)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS stock_ledger (
                ledger_id TEXT PRIMARY KEY,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                item_id TEXT NOT NULL,
                movement_type TEXT NOT NULL,
                qty_delta INTEGER NOT NULL,
                reference_type TEXT,
                reference_id TEXT,
                reason TEXT,
                created_by TEXT,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS stock_threshold (
                item_id TEXT NOT NULL,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                min_qty INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (item_id, outlet_id)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS processed_event (
                event_id TEXT PRIMARY KEY,
                outlet_id TEXT NOT NULL DEFAULT 'default',
                processed_at TEXT NOT NULL
            )
            """.trimIndent(),
        )

        val migrationStatements = listOf(
            "ALTER TABLE order_header ADD COLUMN payment_confirmation TEXT",
            "ALTER TABLE menu_item ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE menu_item ADD COLUMN group_id TEXT",
            "ALTER TABLE menu_item ADD COLUMN group_name TEXT",
            "ALTER TABLE order_header ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE order_item ADD COLUMN note TEXT",
            "ALTER TABLE transaksi_header ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE transaksi_header ADD COLUMN cashier_id TEXT",
            "ALTER TABLE transaksi_header ADD COLUMN cashier_name TEXT",
            "ALTER TABLE transaksi_detail ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE pembayaran ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE processed_event ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
        )

        val indexStatements = listOf(
            "CREATE INDEX IF NOT EXISTS idx_menu_item_outlet_name ON menu_item(outlet_id, name)",
            "CREATE INDEX IF NOT EXISTS idx_modifier_group_outlet_name ON modifier_group(outlet_id, nama)",
            "CREATE INDEX IF NOT EXISTS idx_modifier_option_group_order ON modifier_option(id_modifier_group, outlet_id, urutan)",
            "CREATE INDEX IF NOT EXISTS idx_product_modifier_item_outlet ON product_modifier_group(id_item, outlet_id)",
            "CREATE INDEX IF NOT EXISTS idx_order_header_outlet_status_date ON order_header(outlet_id, status, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_transaksi_header_outlet_date ON transaksi_header(outlet_id, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_pembayaran_outlet_date ON pembayaran(outlet_id, paid_at)",
            "CREATE INDEX IF NOT EXISTS idx_processed_event_outlet_event ON processed_event(outlet_id, event_id)",
            "CREATE INDEX IF NOT EXISTS idx_stock_balance_outlet_qty ON stock_item_balance(outlet_id, qty_on_hand)",
            "CREATE INDEX IF NOT EXISTS idx_stock_ledger_outlet_item_date ON stock_ledger(outlet_id, item_id, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_stock_threshold_outlet_item ON stock_threshold(outlet_id, item_id)",
        )

        connection.createStatement().use { statement ->
            createStatements.forEach { sql ->
                statement.execute(sql)
            }
            migrationStatements.forEach { sql ->
                runCatching {
                    statement.execute(sql)
                }
            }
            indexStatements.forEach { sql ->
                statement.execute(sql)
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        openConnection().use { connection ->
            if (!usingTurso) {
                connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            }
            return block(connection)
        }
    }

    private fun executeWrite(statement: PreparedStatement) {
        if (usingTurso) {
            statement.execute()
        } else {
            statement.executeUpdate()
        }
    }

    private fun executeWriteAllowMissingTable(statement: PreparedStatement, tableName: String) {
        try {
            executeWrite(statement)
        } catch (e: SQLException) {
            if (usingTurso && isMissingTableError(e, tableName)) return
            throw e
        }
    }

    private fun isMissingTableError(cause: Throwable, tableName: String): Boolean {
        val message = cause.message?.lowercase().orEmpty()
        return message.contains("no such table") && message.contains(tableName.lowercase())
    }

    private fun normalizeMenuGroup(groupId: String?, groupName: String?, itemName: String): Pair<String, String> {
        val normalizedName = groupName?.trim().orEmpty()
        val validName = normalizedName.takeIf {
            it.isNotBlank() && !it.equals("Kategori", ignoreCase = true)
        } ?: inferMenuGroupName(itemName)

        val normalizedId = groupId?.trim().orEmpty()
        val validId = normalizedId.takeIf { it.isNotBlank() } ?: inferMenuGroupId(validName)
        return validId to validName
    }

    private fun inferMenuGroupName(itemName: String): String {
        val text = itemName.lowercase()
        return when {
            text.contains("coffee") || text.contains("kopi") || text.contains("latte") ||
                text.contains("espresso") || text.contains("cappuccino") || text.contains("macchiato") -> "Coffee"
            else -> "Non-Coffee"
        }
    }

    private fun inferMenuGroupId(groupName: String): String {
        val slug = groupName.lowercase()
            .replace("&", " dan ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return if (slug.isBlank()) "grp-umum" else "grp-$slug"
    }

    private fun openConnection(): Connection {
        if (!usingTurso) {
            return DriverManager.getConnection(jdbcUrl)
        }
        return if (tursoAuthToken.isBlank()) {
            DriverManager.getConnection(jdbcUrl)
        } else {
            DriverManager.getConnection(jdbcUrl, null, tursoAuthToken)
        }
    }

    private fun findCustomerTransactional(connection: Connection, uuid: String): Customer? {
        connection.prepareStatement(
            """
            SELECT uuid, name
            FROM customer
            WHERE uuid = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, uuid)
            statement.executeQuery().use { rs ->
                if (!rs.next()) return null
                return Customer(
                    uuid = rs.getString("uuid"),
                    name = rs.getString("name"),
                )
            }
        }
    }

    private fun ensureCustomerTransactional(connection: Connection, uuid: String): Customer {
        val normalizedUuid = uuid.trim()
        findCustomerTransactional(connection, normalizedUuid)?.let { return it }
        val now = Instant.now().toString()
        val generatedName = "Customer ${normalizedUuid.take(8).ifBlank { "Guest" }}"
        connection.prepareStatement(
            """
            INSERT INTO customer(uuid, name, created_at)
            VALUES (?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, normalizedUuid)
            statement.setString(2, generatedName)
            statement.setString(3, now)
            statement.executeUpdate()
        }
        return Customer(
            uuid = normalizedUuid,
            name = generatedName,
        )
    }

    private fun seedTableCustomersTransactional(
        connection: Connection,
        count: Int,
        outletId: String,
    ): List<Customer> {
        val normalizedOutletId = normalizeOutletId(outletId)
        val clampedCount = count.coerceIn(1, 200)
        val seeded = ArrayList<Customer>(clampedCount)
        for (index in 1..clampedCount) {
            val tableName = "Table ${index.toString().padStart(2, '0')}"
            val tableUuid = UUID.nameUUIDFromBytes("table:$normalizedOutletId:$index".toByteArray()).toString()
            val existing = findCustomerTransactional(connection, tableUuid)
            if (existing == null) {
                val now = Instant.now().toString()
                connection.prepareStatement(
                    """
                    INSERT INTO customer(uuid, name, created_at)
                    VALUES (?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, tableUuid)
                    statement.setString(2, tableName)
                    statement.setString(3, now)
                    executeWrite(statement)
                }
            } else if (existing.name != tableName) {
                connection.prepareStatement(
                    """
                    UPDATE customer
                    SET name = ?
                    WHERE uuid = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, tableName)
                    statement.setString(2, tableUuid)
                    executeWrite(statement)
                }
            }
            seeded.add(
                Customer(
                    uuid = tableUuid,
                    name = tableName,
                )
            )
        }
        return seeded
    }

    private fun listMenuTransactional(connection: Connection, outletId: String): List<MenuItem> {
        connection.prepareStatement(
            """
            SELECT id, name, price, group_id, group_name, outlet_id
            FROM menu_item
            WHERE outlet_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            statement.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) {
                        val itemName = rs.getString("name")
                        val (resolvedGroupId, resolvedGroupName) = normalizeMenuGroup(
                            groupId = rs.getString("group_id"),
                            groupName = rs.getString("group_name"),
                            itemName = itemName,
                        )
                        add(
                            MenuItem(
                                id = rs.getString("id"),
                                name = itemName,
                                price = rs.getLong("price"),
                                groupId = resolvedGroupId,
                                groupName = resolvedGroupName,
                                outletId = rs.getString("outlet_id") ?: DEFAULT_OUTLET_ID,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun hasMenuItemTransactional(connection: Connection, itemId: String, outletId: String): Boolean {
        connection.prepareStatement(
            "SELECT id FROM menu_item WHERE id = ? AND outlet_id = ? LIMIT 1"
        ).use { statement ->
            statement.setString(1, itemId)
            statement.setString(2, outletId)
            statement.executeQuery().use { rs -> return rs.next() }
        }
    }

    private fun listModifierGroupsTransactional(connection: Connection, outletId: String): List<ModifierGroupRow> {
        connection.prepareStatement(
            """
            SELECT id_modifier_group, nama, selection_type, is_required, max_selection
            FROM modifier_group
            WHERE outlet_id = ?
            ORDER BY nama ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            statement.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) {
                        add(
                            ModifierGroupRow(
                                id = rs.getString("id_modifier_group"),
                                name = rs.getString("nama"),
                                selectionType = rs.getString("selection_type").orEmpty().ifBlank { "SINGLE" },
                                isRequired = rs.getLong("is_required") == 1L,
                                maxSelection = rs.getInt("max_selection").coerceAtLeast(1),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun listModifierOptionsTransactional(connection: Connection, outletId: String): List<ModifierOptionRow> {
        connection.prepareStatement(
            """
            SELECT id_modifier_option, id_modifier_group, nama, price_delta, urutan, is_default
            FROM modifier_option
            WHERE outlet_id = ?
            ORDER BY id_modifier_group ASC, urutan ASC, nama ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            statement.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) {
                        add(
                            ModifierOptionRow(
                                id = rs.getString("id_modifier_option"),
                                groupId = rs.getString("id_modifier_group"),
                                name = rs.getString("nama"),
                                priceDelta = rs.getLong("price_delta"),
                                order = rs.getInt("urutan"),
                                isDefault = rs.getLong("is_default") == 1L,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun listProductModifierLinksTransactional(
        connection: Connection,
        outletId: String,
    ): Map<String, Set<String>> {
        connection.prepareStatement(
            """
            SELECT id_item, id_modifier_group
            FROM product_modifier_group
            WHERE outlet_id = ?
            ORDER BY id_item ASC, id_modifier_group ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            statement.executeQuery().use { rs ->
                val result = mutableMapOf<String, MutableSet<String>>()
                while (rs.next()) {
                    val itemId = rs.getString("id_item")
                    val groupId = rs.getString("id_modifier_group")
                    if (!itemId.isNullOrBlank() && !groupId.isNullOrBlank()) {
                        result.getOrPut(itemId) { linkedSetOf() }.add(groupId)
                    }
                }
                return result
            }
        }
    }

    private fun findOrderByIdTransactional(connection: Connection, orderId: String, outletId: String): OrderView? {
        connection.prepareStatement(
            """
            SELECT oh.id,
                   oh.customer_uuid,
                   c.name AS customer_name,
                   oh.status,
                   oh.outlet_id,
                   oh.payment_confirmation,
                   oh.note,
                   oh.created_at,
                   oh.updated_at,
                   oh.total
            FROM order_header oh
            INNER JOIN customer c ON c.uuid = oh.customer_uuid
            WHERE oh.id = ? AND oh.outlet_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, orderId)
            statement.setString(2, outletId)
            statement.executeQuery().use { rs ->
                if (!rs.next()) return null
                return OrderView(
                    id = rs.getString("id"),
                    customerUuid = rs.getString("customer_uuid"),
                    customerName = rs.getString("customer_name"),
                    status = rs.getString("status"),
                    outletId = rs.getString("outlet_id") ?: DEFAULT_OUTLET_ID,
                    paymentConfirmation = rs.getString("payment_confirmation"),
                    note = rs.getString("note"),
                    createdAt = rs.getString("created_at"),
                    updatedAt = rs.getString("updated_at"),
                    total = rs.getLong("total"),
                    items = listOrderLinesTransactional(connection, orderId),
                )
            }
        }
    }

    private fun listOrderLinesTransactional(connection: Connection, orderId: String): List<OrderLine> {
        connection.prepareStatement(
            """
            SELECT id, menu_id, item_name, qty, price, line_total, note
            FROM order_item
            WHERE order_id = ?
            ORDER BY item_name ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, orderId)
            statement.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) {
                        add(
                            OrderLine(
                                id = rs.getString("id"),
                                menuId = rs.getString("menu_id"),
                                itemName = rs.getString("item_name"),
                                qty = rs.getInt("qty"),
                                price = rs.getLong("price"),
                                lineTotal = rs.getLong("line_total"),
                                note = rs.getString("note"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun migrateCompletedOrdersToTransaksiTransactional(connection: Connection) {
        connection.prepareStatement(
            """
            SELECT id, outlet_id
            FROM order_header
            WHERE status IN ('DONE', 'SERVED')
            ORDER BY created_at ASC
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    val orderId = rs.getString("id")
                    val outletId = rs.getString("outlet_id") ?: DEFAULT_OUTLET_ID
                    runCatching {
                        materializeCompletedOrderToTransaksiTransactional(connection, orderId, outletId)
                    }
                }
            }
        }
    }

    private fun materializeCompletedOrderToTransaksiTransactional(
        connection: Connection,
        orderId: String,
        outletId: String,
    ) {
        val transaksiId = toOrderTransaksiId(orderId)
        if (hasTransaksiHeaderTransactional(connection, transaksiId, outletId)) return

        data class OrderMaterialize(
            val createdAt: String,
            val meja: String?,
            val total: Long,
            val paymentConfirmation: String?,
        )

        val order = connection.prepareStatement(
            """
            SELECT oh.created_at,
                   oh.updated_at,
                   oh.total,
                   oh.payment_confirmation,
                   c.name AS customer_name
            FROM order_header oh
            INNER JOIN customer c ON c.uuid = oh.customer_uuid
            WHERE oh.id = ? AND oh.outlet_id = ?
            LIMIT 1
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, orderId)
            statement.setString(2, outletId)
            statement.executeQuery().use { rs ->
                if (!rs.next()) return
                val createdAt = rs.getString("updated_at")
                    ?.takeIf { it.isNotBlank() }
                    ?: rs.getString("created_at")
                OrderMaterialize(
                    createdAt = createdAt,
                    meja = rs.getString("customer_name"),
                    total = rs.getLong("total"),
                    paymentConfirmation = rs.getString("payment_confirmation"),
                )
            }
        }

        connection.prepareStatement(
            """
            INSERT INTO transaksi_header(
                id, created_at, meja, cashier_id, cashier_name, discount_plus, tax, service_charge, rounding, total, outlet_id
            ) VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, transaksiId)
            statement.setString(2, order.createdAt)
            statement.setString(3, order.meja)
            statement.setString(4, "system")
            statement.setString(5, "System")
            statement.setLong(6, order.total)
            statement.setString(7, outletId)
            statement.executeUpdate()
        }

        data class OrderItemMaterialize(
            val menuId: String?,
            val itemName: String,
            val qty: Int,
            val price: Long,
            val lineTotal: Long,
        )

        val orderItems = connection.prepareStatement(
            """
            SELECT menu_id, item_name, qty, price, line_total
            FROM order_item
            WHERE order_id = ?
            ORDER BY item_name ASC, id ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, orderId)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            OrderItemMaterialize(
                                menuId = rs.getString("menu_id"),
                                itemName = rs.getString("item_name"),
                                qty = rs.getInt("qty"),
                                price = rs.getLong("price"),
                                lineTotal = rs.getLong("line_total"),
                            )
                        )
                    }
                }
            }
        }

        connection.prepareStatement(
            """
            INSERT INTO transaksi_detail(
                id, transaksi_id, item_id, item_name, qty, price, discount, total, outlet_id
            ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
            """.trimIndent()
        ).use { statement ->
            orderItems.forEachIndexed { index, item ->
                statement.setString(1, toOrderTransaksiDetailId(orderId, index))
                statement.setString(2, transaksiId)
                statement.setString(3, item.menuId)
                statement.setString(4, item.itemName)
                statement.setInt(5, item.qty)
                statement.setLong(6, item.price)
                statement.setLong(7, item.lineTotal)
                statement.setString(8, outletId)
                if (usingTurso) {
                    statement.executeUpdate()
                } else {
                    statement.addBatch()
                }
            }
            if (!usingTurso && orderItems.isNotEmpty()) {
                statement.executeBatch()
            }
        }

        connection.prepareStatement(
            """
            INSERT INTO pembayaran(
                id, transaksi_id, paid_at, amount_paid, change_amount, payment_type_id, outlet_id
            ) VALUES (?, ?, ?, ?, 0, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, toOrderPembayaranId(orderId))
            statement.setString(2, transaksiId)
            statement.setString(3, order.createdAt)
            statement.setLong(4, order.total)
            statement.setString(5, mapOrderPaymentToType(order.paymentConfirmation))
            statement.setString(6, outletId)
            statement.executeUpdate()
        }
    }

    private fun isProcessedTransactional(connection: Connection, outboxId: String, outletId: String): Boolean {
        connection.prepareStatement(
            "SELECT event_id FROM processed_event WHERE event_id = ? AND outlet_id = ? LIMIT 1"
        ).use { statement ->
            statement.setString(1, outboxId)
            statement.setString(2, outletId)
            statement.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    private fun upsertTransaksiTransactional(connection: Connection, transaksi: TransaksiDto, outletId: String) {
        val existedBefore = hasTransaksiHeaderTransactional(connection, transaksi.id, outletId)
        connection.prepareStatement(
            """
            INSERT INTO transaksi_header(
                id, created_at, meja, cashier_id, cashier_name, discount_plus, tax, service_charge, rounding, total, outlet_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                created_at = excluded.created_at,
                meja = excluded.meja,
                cashier_id = excluded.cashier_id,
                cashier_name = excluded.cashier_name,
                discount_plus = excluded.discount_plus,
                tax = excluded.tax,
                service_charge = excluded.service_charge,
                rounding = excluded.rounding,
                total = excluded.total,
                outlet_id = excluded.outlet_id
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, transaksi.id)
            statement.setString(2, transaksi.createdAt)
            statement.setString(3, transaksi.meja)
            statement.setString(4, transaksi.cashierId)
            statement.setString(5, transaksi.cashierName)
            statement.setLong(6, transaksi.discountPlus)
            statement.setLong(7, transaksi.tax)
            statement.setLong(8, transaksi.serviceCharge)
            statement.setLong(9, transaksi.rounding)
            statement.setLong(10, transaksi.total)
            statement.setString(11, outletId)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            "DELETE FROM transaksi_detail WHERE transaksi_id = ? AND outlet_id = ?"
        ).use { statement ->
            statement.setString(1, transaksi.id)
            statement.setString(2, outletId)
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "DELETE FROM pembayaran WHERE transaksi_id = ? AND outlet_id = ?"
        ).use { statement ->
            statement.setString(1, transaksi.id)
            statement.setString(2, outletId)
            statement.executeUpdate()
        }

        connection.prepareStatement(
            """
            INSERT INTO transaksi_detail(
                id, transaksi_id, item_id, item_name, qty, price, discount, total, outlet_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            transaksi.details.forEach { detail ->
                statement.setString(1, detail.id)
                statement.setString(2, transaksi.id)
                statement.setString(3, detail.itemId)
                statement.setString(4, detail.itemName)
                statement.setLong(5, detail.qty)
                statement.setLong(6, detail.price)
                statement.setLong(7, detail.discount)
                statement.setLong(8, detail.total)
                statement.setString(9, outletId)
                if (usingTurso) {
                    statement.executeUpdate()
                } else {
                    statement.addBatch()
                }
            }
            if (!usingTurso) {
                statement.executeBatch()
            }
        }

        connection.prepareStatement(
            """
            INSERT INTO pembayaran(
                id, transaksi_id, paid_at, amount_paid, change_amount, payment_type_id, outlet_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, transaksi.pembayaran.id)
            statement.setString(2, transaksi.id)
            statement.setString(3, transaksi.pembayaran.paidAt)
            statement.setLong(4, transaksi.pembayaran.amountPaid)
            statement.setLong(5, transaksi.pembayaran.change)
            statement.setString(6, transaksi.pembayaran.paymentTypeId)
            statement.setString(7, outletId)
            statement.executeUpdate()
        }

        if (!existedBefore) {
            applySaleStockTransactional(
                connection = connection,
                transaksi = transaksi,
                outletId = outletId,
            )
        }
    }

    private fun hasTransaksiHeaderTransactional(
        connection: Connection,
        transaksiId: String,
        outletId: String,
    ): Boolean {
        connection.prepareStatement(
            "SELECT id FROM transaksi_header WHERE id = ? AND outlet_id = ? LIMIT 1"
        ).use { statement ->
            statement.setString(1, transaksiId)
            statement.setString(2, outletId)
            statement.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    private fun applySaleStockTransactional(
        connection: Connection,
        transaksi: TransaksiDto,
        outletId: String,
    ) {
        val createdAt = transaksi.pembayaran.paidAt.ifBlank { transaksi.createdAt }
        transaksi.details.forEach { detail ->
            val itemId = detail.itemId ?: return@forEach
            if (detail.qty <= 0L) return@forEach
            val qtyDelta = -detail.qty
            val currentQty = getStockQtyTransactional(connection, outletId, itemId)
            val nextQty = currentQty + qtyDelta
            upsertStockBalanceTransactional(
                connection = connection,
                outletId = outletId,
                itemId = itemId,
                qtyOnHand = nextQty,
                updatedAt = createdAt,
            )
            connection.prepareStatement(
                """
                INSERT INTO stock_ledger(
                    ledger_id,
                    outlet_id,
                    item_id,
                    movement_type,
                    qty_delta,
                    reference_type,
                    reference_id,
                    reason,
                    created_by,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, outletId)
                statement.setString(3, itemId)
                statement.setString(4, "SALE")
                statement.setLong(5, qtyDelta)
                statement.setString(6, "TRANSAKSI")
                statement.setString(7, transaksi.id)
                statement.setString(8, "Checkout sync")
                statement.setString(9, "sync")
                statement.setString(10, createdAt)
                statement.executeUpdate()
            }
        }
    }

    private fun getStockQtyTransactional(
        connection: Connection,
        outletId: String,
        itemId: String,
    ): Long {
        connection.prepareStatement(
            """
            SELECT qty_on_hand
            FROM stock_item_balance
            WHERE outlet_id = ? AND item_id = ?
            LIMIT 1
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            statement.setString(2, itemId)
            statement.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong("qty_on_hand") else 0L
            }
        }
    }

    private fun upsertStockBalanceTransactional(
        connection: Connection,
        outletId: String,
        itemId: String,
        qtyOnHand: Long,
        updatedAt: String,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO stock_item_balance(item_id, outlet_id, qty_on_hand, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(item_id, outlet_id) DO UPDATE SET
                qty_on_hand = excluded.qty_on_hand,
                updated_at = excluded.updated_at
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, itemId)
            statement.setString(2, outletId)
            statement.setLong(3, qtyOnHand)
            statement.setString(4, updatedAt)
            statement.executeUpdate()
        }
    }

    private fun normalizeOutletId(outletId: String?): String {
        return outletId?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_OUTLET_ID
    }

    private fun normalizeTransactionSyncEvents(request: TransactionBatchRequest): List<TransactionSyncEventDto> {
        if (request.events.isNotEmpty()) return request.events
        if (request.outboxIds.isEmpty() || request.transaksi.isEmpty()) return emptyList()
        val paired = minOf(request.outboxIds.size, request.transaksi.size)
        return buildList {
            repeat(paired) { index ->
                add(
                    TransactionSyncEventDto(
                        eventId = request.outboxIds[index],
                        entityType = "TRANSAKSI_CHECKOUT",
                        op = "UPSERT",
                        payloadJson = syncJson.encodeToString(TransaksiDto.serializer(), request.transaksi[index]),
                        createdAt = request.transaksi[index].createdAt,
                    )
                )
            }
            if (request.outboxIds.size > paired) {
                request.outboxIds.drop(paired).forEach { orphanId ->
                    add(
                        TransactionSyncEventDto(
                            eventId = orphanId,
                            entityType = "TRANSAKSI_CHECKOUT",
                            op = "UPSERT",
                            payloadJson = "",
                            createdAt = "",
                        )
                    )
                }
            }
        }
    }

    private fun parseRecapRange(value: String?): RecapRangeDto {
        return runCatching {
            RecapRangeDto.valueOf(value.orEmpty().trim().uppercase())
        }.getOrDefault(RecapRangeDto.TODAY)
    }

    private fun normalizeAnchorDate(value: String?): String {
        val fallback = LocalDate.now().toString()
        return runCatching { LocalDate.parse(value.orEmpty().trim()) }
            .map { it.toString() }
            .getOrDefault(fallback)
    }

    private fun buildDateFilter(
        columnExpression: String,
        range: RecapRangeDto,
        anchorDate: String,
        fromDate: String? = null,
        toDate: String? = null,
    ): DateFilterSpec {
        val normalizedFrom = normalizeAnchorDateOrNull(fromDate)
        val normalizedTo = normalizeAnchorDateOrNull(toDate)
        if (normalizedFrom != null && normalizedTo != null) {
            val (fromValue, toValue) = if (normalizedFrom <= normalizedTo) {
                normalizedFrom to normalizedTo
            } else {
                normalizedTo to normalizedFrom
            }
            return DateFilterSpec(
                sql = "substr($columnExpression, 1, 10) BETWEEN ? AND ?",
                bind = { statement, startIndex ->
                    statement.setString(startIndex, fromValue)
                    statement.setString(startIndex + 1, toValue)
                    startIndex + 2
                },
            )
        }
        if (normalizedFrom != null) {
            return DateFilterSpec(
                sql = "substr($columnExpression, 1, 10) >= ?",
                bind = { statement, startIndex ->
                    statement.setString(startIndex, normalizedFrom)
                    startIndex + 1
                },
            )
        }
        if (normalizedTo != null) {
            return DateFilterSpec(
                sql = "substr($columnExpression, 1, 10) <= ?",
                bind = { statement, startIndex ->
                    statement.setString(startIndex, normalizedTo)
                    startIndex + 1
                },
            )
        }
        return when (range) {
            RecapRangeDto.TODAY -> DateFilterSpec(
                sql = "substr($columnExpression, 1, 10) = ?",
                bind = { statement, startIndex ->
                    statement.setString(startIndex, anchorDate)
                    startIndex + 1
                },
            )

            RecapRangeDto.WEEK -> DateFilterSpec(
                sql = "substr($columnExpression, 1, 10) BETWEEN date(?, '-6 day') AND date(?)",
                bind = { statement, startIndex ->
                    statement.setString(startIndex, anchorDate)
                    statement.setString(startIndex + 1, anchorDate)
                    startIndex + 2
                },
            )

            RecapRangeDto.MONTH -> DateFilterSpec(
                sql = "substr($columnExpression, 1, 7) = substr(?, 1, 7)",
                bind = { statement, startIndex ->
                    statement.setString(startIndex, anchorDate)
                    startIndex + 1
                },
            )

            RecapRangeDto.ALL -> DateFilterSpec(
                sql = "1 = 1",
                bind = { _, startIndex -> startIndex },
            )
        }
    }

    private fun normalizeAnchorDateOrNull(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { LocalDate.parse(raw) }
            .map { it.toString() }
            .getOrNull()
    }

    private fun queryPaymentBreakdown(
        connection: Connection,
        outletId: String,
        dateFilter: DateFilterSpec,
    ): List<PaymentBreakdownDto> {
        return connection.prepareStatement(
            """
            SELECT p.payment_type_id AS method_id,
                   COUNT(*) AS transaction_count,
                   COALESCE(SUM(p.amount_paid), 0) AS total
            FROM pembayaran p
            INNER JOIN transaksi_header t
                ON t.id = p.transaksi_id
               AND t.outlet_id = p.outlet_id
            WHERE t.outlet_id = ?
              AND ${dateFilter.sql}
            GROUP BY p.payment_type_id
            ORDER BY total DESC, method_id ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            dateFilter.bind(statement, 2)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val methodId = rs.getString("method_id").orEmpty().ifBlank { "UNKNOWN" }
                        add(
                            PaymentBreakdownDto(
                                methodId = methodId,
                                methodName = toPaymentMethodName(methodId),
                                transactionCount = rs.getInt("transaction_count"),
                                total = rs.getLong("total"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun queryProductMovers(
        connection: Connection,
        outletId: String,
        dateFilter: DateFilterSpec,
        ascending: Boolean,
        limit: Int,
    ): List<ProductMovementDto> {
        val orderBy = if (ascending) {
            "qty_sold ASC, revenue ASC, d.item_name ASC"
        } else {
            "qty_sold DESC, revenue DESC, d.item_name ASC"
        }
        return connection.prepareStatement(
            """
            SELECT d.item_id,
                   d.item_name,
                   COALESCE(SUM(d.qty), 0) AS qty_sold,
                   COALESCE(SUM(d.total), 0) AS revenue
            FROM transaksi_detail d
            INNER JOIN transaksi_header t
                ON t.id = d.transaksi_id
               AND t.outlet_id = d.outlet_id
            WHERE t.outlet_id = ?
              AND ${dateFilter.sql}
            GROUP BY d.item_id, d.item_name
            HAVING COALESCE(SUM(d.qty), 0) > 0
            ORDER BY $orderBy
            LIMIT ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            val nextIndex = dateFilter.bind(statement, 2)
            statement.setInt(nextIndex, limit.coerceAtLeast(1))
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ProductMovementDto(
                                itemId = rs.getString("item_id"),
                                itemName = rs.getString("item_name"),
                                qtySold = rs.getLong("qty_sold"),
                                revenue = rs.getLong("revenue"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun queryDoneOrderPaymentBreakdown(
        connection: Connection,
        outletId: String,
        dateFilter: DateFilterSpec,
    ): List<PaymentBreakdownDto> {
        return connection.prepareStatement(
            """
            SELECT COALESCE(o.payment_confirmation, 'UNKNOWN') AS method_id,
                   COUNT(*) AS transaction_count,
                   COALESCE(SUM(o.total), 0) AS total
            FROM order_header o
            WHERE o.outlet_id = ?
              AND o.status IN ('DONE', 'SERVED', 'PREPARING', 'COOKING')
              AND ${dateFilter.sql}
            GROUP BY COALESCE(o.payment_confirmation, 'UNKNOWN')
            ORDER BY total DESC, method_id ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            dateFilter.bind(statement, 2)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val methodId = rs.getString("method_id").orEmpty().ifBlank { "UNKNOWN" }
                        add(
                            PaymentBreakdownDto(
                                methodId = methodId,
                                methodName = toPaymentMethodName(methodId),
                                transactionCount = rs.getInt("transaction_count"),
                                total = rs.getLong("total"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun queryDoneOrderProductMovers(
        connection: Connection,
        outletId: String,
        dateFilter: DateFilterSpec,
        ascending: Boolean,
        limit: Int,
    ): List<ProductMovementDto> {
        val orderBy = if (ascending) {
            "qty_sold ASC, revenue ASC, oi.item_name ASC"
        } else {
            "qty_sold DESC, revenue DESC, oi.item_name ASC"
        }
        return connection.prepareStatement(
            """
            SELECT oi.menu_id AS item_id,
                   oi.item_name AS item_name,
                   COALESCE(SUM(oi.qty), 0) AS qty_sold,
                   COALESCE(SUM(oi.line_total), 0) AS revenue
            FROM order_item oi
            INNER JOIN order_header o
                ON o.id = oi.order_id
            WHERE o.outlet_id = ?
              AND o.status IN ('DONE', 'SERVED', 'PREPARING', 'COOKING')
              AND ${dateFilter.sql}
            GROUP BY oi.menu_id, oi.item_name
            HAVING COALESCE(SUM(oi.qty), 0) > 0
            ORDER BY $orderBy
            LIMIT ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            val nextIndex = dateFilter.bind(statement, 2)
            statement.setInt(nextIndex, limit.coerceAtLeast(1))
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ProductMovementDto(
                                itemId = rs.getString("item_id"),
                                itemName = rs.getString("item_name"),
                                qtySold = rs.getLong("qty_sold"),
                                revenue = rs.getLong("revenue"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun mergePaymentBreakdown(
        primary: List<PaymentBreakdownDto>,
        secondary: List<PaymentBreakdownDto>,
    ): List<PaymentBreakdownDto> {
        if (secondary.isEmpty()) return primary
        val merged = linkedMapOf<String, PaymentBreakdownDto>()
        (primary + secondary).forEach { row ->
            val key = row.methodId.ifBlank { "UNKNOWN" }
            val current = merged[key]
            merged[key] = if (current == null) {
                row.copy(methodId = key, methodName = toPaymentMethodName(key))
            } else {
                current.copy(
                    transactionCount = current.transactionCount + row.transactionCount,
                    total = current.total + row.total,
                )
            }
        }
        return merged.values.sortedByDescending { it.total }
    }

    private fun mergeProductMovers(
        primary: List<ProductMovementDto>,
        secondary: List<ProductMovementDto>,
        ascending: Boolean,
        limit: Int,
    ): List<ProductMovementDto> {
        if (secondary.isEmpty()) return primary
        val merged = linkedMapOf<String, ProductMovementDto>()
        (primary + secondary).forEach { row ->
            val key = "${row.itemId ?: ""}|${row.itemName}"
            val current = merged[key]
            merged[key] = if (current == null) {
                row
            } else {
                current.copy(
                    qtySold = current.qtySold + row.qtySold,
                    revenue = current.revenue + row.revenue,
                )
            }
        }
        val sorted = if (ascending) {
            merged.values
                .filter { it.qtySold > 0 }
                .sortedWith(compareBy<ProductMovementDto> { it.qtySold }.thenBy { it.revenue }.thenBy { it.itemName })
        } else {
            merged.values
                .filter { it.qtySold > 0 }
                .sortedWith(compareByDescending<ProductMovementDto> { it.qtySold }.thenByDescending { it.revenue }.thenBy { it.itemName })
        }
        return sorted.take(limit.coerceAtLeast(1))
    }

    private fun toPaymentMethodName(methodId: String): String {
        val normalized = methodId.trim().uppercase()
        return when {
            normalized == "CASH" -> "Cash"
            normalized.contains("QRIS") -> "QRIS"
            normalized.contains("DEBIT") -> "Debit"
            normalized.contains("CREDIT") -> "Credit Card"
            normalized.contains("TRANSFER") -> "Bank Transfer"
            normalized.isBlank() || normalized == "UNKNOWN" -> "Unknown"
            else -> methodId
        }
    }

    private fun mapOrderPaymentToType(raw: String?): String {
        val normalized = raw.orEmpty().trim().uppercase()
        return when {
            normalized.contains("QRIS") -> "QRIS"
            normalized.contains("TRANSFER") -> "TRANSFER"
            normalized.contains("DEBIT") -> "DEBIT"
            normalized.contains("CREDIT") -> "CREDIT"
            else -> "CASH"
        }
    }

    private fun toOrderTransaksiId(orderId: String): String = "ordtrx_$orderId"
    private fun toOrderTransaksiDetailId(orderId: String, index: Int): String = "ordtd_${orderId}_$index"
    private fun toOrderPembayaranId(orderId: String): String = "ordpay_$orderId"

    private val COMPLETED_ORDER_STATUSES = setOf("DONE", "SERVED")

    private fun migrateLocalSqliteDataToTursoIfEnabled(targetConnection: Connection) {
        if (!usingTurso || !migrateLocalSqliteToTurso) return
        if (migrationSourceSqlitePath.isBlank()) return
        val sourcePath = Path.of(migrationSourceSqlitePath)
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) return
        if (hasAnyBusinessData(targetConnection)) return

        runCatching {
            DriverManager.getConnection("jdbc:sqlite:${sourcePath.toAbsolutePath()}").use { sourceConnection ->
                sourceConnection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
                val tableNames = listMigratableTableNames(sourceConnection)
                if (tableNames.isEmpty()) return

                val previousAutoCommit = if (!usingTurso) targetConnection.autoCommit else true
                if (!usingTurso) {
                    targetConnection.autoCommit = false
                }
                try {
                    tableNames.forEach { tableName ->
                        copyTableData(sourceConnection, targetConnection, tableName)
                    }
                    if (!usingTurso) {
                        targetConnection.commit()
                    }
                } catch (t: Throwable) {
                    if (!usingTurso) {
                        targetConnection.rollback()
                    }
                    throw t
                } finally {
                    if (!usingTurso) {
                        targetConnection.autoCommit = previousAutoCommit
                    }
                }
            }
        }.onFailure { cause ->
            println(
                "Skipping SQLite->Turso migration due to driver limitation: " +
                    "${cause::class.simpleName}: ${cause.message}"
            )
        }
    }

    private fun hasAnyBusinessData(connection: Connection): Boolean {
        val tableChecks = listOf("menu_item", "order_header", "transaksi_header", "customer")
        return tableChecks.any { table ->
            connection.prepareStatement("SELECT 1 FROM \"$table\" LIMIT 1").use { statement ->
                statement.executeQuery().use { rs -> rs.next() }
            }
        }
    }

    private fun listMigratableTableNames(connection: Connection): List<String> {
        connection.prepareStatement(
            """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'sqlite_%'
            ORDER BY name ASC
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) {
                        val table = rs.getString("name")?.trim().orEmpty()
                        if (table.isNotBlank()) add(table)
                    }
                }
            }
        }
    }

    private fun listTableColumns(connection: Connection, tableName: String): List<String> {
        val safeTableName = tableName.replace("\"", "\"\"")
        connection.prepareStatement("PRAGMA table_info(\"$safeTableName\")").use { statement ->
            statement.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) {
                        val columnName = rs.getString("name")?.trim().orEmpty()
                        if (columnName.isNotBlank()) add(columnName)
                    }
                }
            }
        }
    }

    private fun copyTableData(source: Connection, target: Connection, tableName: String) {
        val columns = listTableColumns(source, tableName)
        if (columns.isEmpty()) return
        val safeTableName = tableName.replace("\"", "\"\"")
        val quotedColumns = columns.joinToString(", ") { "\"${it.replace("\"", "\"\"")}\"" }
        val placeholders = columns.joinToString(", ") { "?" }

        val selectSql = "SELECT $quotedColumns FROM \"$safeTableName\""
        val insertSql = "INSERT OR REPLACE INTO \"$safeTableName\" ($quotedColumns) VALUES ($placeholders)"

        source.prepareStatement(selectSql).use { readStatement ->
            readStatement.executeQuery().use { rows ->
                target.prepareStatement(insertSql).use { writeStatement ->
                    while (rows.next()) {
                        columns.forEachIndexed { index, columnName ->
                            writeStatement.setObject(index + 1, rows.getObject(columnName))
                        }
                        if (usingTurso) {
                            writeStatement.execute()
                        } else {
                            writeStatement.addBatch()
                        }
                    }
                    if (!usingTurso) {
                        writeStatement.executeBatch()
                    }
                }
            }
        }
    }

    private fun toLibsqlJdbcUrl(value: String): String {
        val raw = value.trim()
        if (raw.startsWith("jdbc:dbeaver:libsql:", ignoreCase = true)) {
            val payload = raw.removePrefix("jdbc:dbeaver:libsql:").trim()
            return normalizeLibsqlPayload(payload)
        }
        if (raw.startsWith("jdbc:", ignoreCase = true)) return raw
        return when {
            else -> normalizeLibsqlPayload(raw)
        }
    }

    private fun normalizeLibsqlPayload(value: String): String {
        val raw = value.trim()
        if (raw.startsWith("libsql://", ignoreCase = true)) {
            return "jdbc:dbeaver:libsql:$raw"
        }
        if (raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true)) {
            val uri = runCatching { URI(raw) }.getOrNull()
            val authority = uri?.rawAuthority.orEmpty()
            val path = uri?.rawPath.orEmpty()
            val query = uri?.rawQuery?.let { "?$it" }.orEmpty()
            return if (authority.isNotBlank()) {
                "jdbc:dbeaver:libsql:libsql://$authority$path$query"
            } else {
                val withoutScheme = raw.substringAfter("://", missingDelimiterValue = raw)
                "jdbc:dbeaver:libsql:libsql://$withoutScheme"
            }
        }
        return "jdbc:dbeaver:libsql:libsql://$raw"
    }

    private fun String.maskJdbcSecrets(): String {
        return replace(Regex("([?&](?:authToken|token|password)=)[^&]+", RegexOption.IGNORE_CASE), "$1***")
    }
}
