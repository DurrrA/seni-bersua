package com.durrr.first

import com.durrr.first.network.dto.DailyRecapResponse
import com.durrr.first.network.dto.PaymentBreakdownDto
import com.durrr.first.network.dto.ProductMovementDto
import com.durrr.first.network.dto.RecapRangeDto
import com.durrr.first.network.dto.RecapSummaryResponse
import com.durrr.first.network.dto.TransactionBatchRequest
import com.durrr.first.network.dto.TransactionBatchResponse
import com.durrr.first.network.dto.TransaksiDto
import com.durrr.first.network.dto.UpsertMenuItemRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
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
    val outletId: String = DEFAULT_OUTLET_ID,
)

@Serializable
data class OrderLineInput(
    val menuId: String,
    val qty: Int,
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
data class OrderLine(
    val id: String,
    val menuId: String,
    val itemName: String,
    val qty: Int,
    val price: Long,
    val lineTotal: Long,
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
    private val dbPath: String = EnvConfig
        .get("SUCASH_SERVER_DB_PATH", "data/sucash-server.db")
        .orEmpty()

    private val seededCustomers = listOf(
        Customer("9a8a7f0e-95d8-4b5b-83ec-2ef5dd4fe1d1", "Customer 01"),
        Customer("d6d2356d-e47a-4f58-bfdb-8a6846dce2bf", "Customer 02"),
        Customer("3f4f7b48-8769-4dc0-a6db-0226117f98aa", "Customer 03"),
        Customer("eb80fb6c-5f63-4a3a-91f0-c0404a6085f1", "Customer 04"),
        Customer("89f95c88-88af-4d78-b85c-8f9f9efdcca5", "Customer 05"),
        Customer("d47eb565-199f-4ddf-9f81-26a3d8d590e7", "Customer 06"),
        Customer("826f3685-3de6-40cd-9518-36ae1335cc74", "Customer 07"),
        Customer("8bb5f952-b8c7-4954-b41e-0188344e5d31", "Customer 08"),
        Customer("0d0a6a44-5765-4236-89ef-26fdbe419416", "Customer 09"),
        Customer("0f14dc88-c26d-4313-a4c5-6a27cfaec8e0", "Customer 10"),
    )

    private val seededMenu = listOf(
        MenuItem("menu-espresso", "Espresso", 18000),
        MenuItem("menu-cappuccino", "Cappuccino", 25000),
        MenuItem("menu-latte", "Cafe Latte", 26000),
        MenuItem("menu-iced-tea", "Iced Tea", 15000),
        MenuItem("menu-fried-rice", "Fried Rice", 32000),
        MenuItem("menu-french-fries", "French Fries", 22000),
        MenuItem("menu-chicken-bowl", "Chicken Bowl", 35000),
        MenuItem("menu-cheesecake", "Cheesecake", 28000),
    )

    private data class DateFilterSpec(
        val sql: String,
        val bind: (PreparedStatement, Int) -> Int,
    )

    fun init() {
        val parent = Path.of(dbPath).parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        withConnection { connection ->
            createSchema(connection)
            seedCustomersIfNeeded(connection)
            seedMenuIfNeeded(connection)
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
            SELECT id, name, price, outlet_id
            FROM menu_item
            WHERE outlet_id = ?
            ORDER BY name ASC
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, scopedOutletId)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            MenuItem(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                price = rs.getLong("price"),
                                outletId = rs.getString("outlet_id") ?: DEFAULT_OUTLET_ID,
                            )
                        )
                    }
                }
            }
        }
    }

    fun upsertMenu(request: UpsertMenuItemRequest): MenuItem? = withConnection { connection ->
        val name = request.name.trim()
        if (name.isBlank() || request.price < 0) return@withConnection null
        val outletId = normalizeOutletId(request.outletId)
        val id = request.id?.trim().takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString()
        val now = Instant.now().toString()
        connection.prepareStatement(
            """
            INSERT INTO menu_item(id, name, price, created_at, outlet_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                price = excluded.price,
                outlet_id = excluded.outlet_id
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, name)
            statement.setLong(3, request.price)
            statement.setString(4, now)
            statement.setString(5, outletId)
            statement.executeUpdate()
        }
        MenuItem(id = id, name = name, price = request.price, outletId = outletId)
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

    fun createOrder(request: CreateOrderRequest): OrderView? = withConnection { connection ->
        connection.autoCommit = false
        try {
            val outletId = normalizeOutletId(request.outletId)
            val customer = findCustomerTransactional(connection, request.customerUuid) ?: return@withConnection null

            val menuById = listMenuTransactional(connection, outletId).associateBy { it.id }
            if (request.items.isEmpty()) {
                return@withConnection null
            }

            val orderLines = buildList {
                request.items.forEach { input ->
                    val menu = menuById[input.menuId] ?: return@withConnection null
                    if (input.qty <= 0) return@withConnection null
                    add(
                        OrderLine(
                            id = UUID.randomUUID().toString(),
                            menuId = menu.id,
                            itemName = menu.name,
                            qty = input.qty,
                            price = menu.price,
                            lineTotal = menu.price * input.qty,
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
                INSERT INTO order_item(id, order_id, menu_id, item_name, qty, price, line_total)
                VALUES (?, ?, ?, ?, ?, ?, ?)
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
                    statement.addBatch()
                }
                statement.executeBatch()
            }

            connection.commit()
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
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
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
        connection.autoCommit = false
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
                connection.rollback()
                return@withConnection null
            }

            connection.commit()
            findOrderByIdTransactional(connection, orderId, scopedOutletId)
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
        }
    }

    fun syncTransactionsBatch(request: TransactionBatchRequest): TransactionBatchResponse = withConnection { connection ->
        connection.autoCommit = false
        try {
            val outletId = normalizeOutletId(request.outletId)
            val accepted = mutableListOf<String>()
            request.outboxIds.forEachIndexed { index, outboxId ->
                val transaksi = request.transaksi.getOrNull(index) ?: return@forEachIndexed
                val scopedEventId = "$outletId::$outboxId"
                if (isProcessedTransactional(connection, scopedEventId, outletId)) {
                    accepted += outboxId
                    return@forEachIndexed
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
                accepted += outboxId
            }
            connection.commit()
            val rejected = request.outboxIds.filterNot { it in accepted }
            TransactionBatchResponse(accepted = accepted, rejected = rejected)
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
        }
    }

    fun dailyRecap(date: String, outletId: String = DEFAULT_OUTLET_ID): DailyRecapResponse = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        val safeDate = normalizeAnchorDate(date)
        connection.prepareStatement(
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
                DailyRecapResponse(
                    date = safeDate,
                    transaksiCount = count,
                    grossTotal = gross,
                )
            }
        }
    }

    fun recapSummary(
        range: String,
        date: String,
        outletId: String = DEFAULT_OUTLET_ID,
    ): RecapSummaryResponse = withConnection { connection ->
        val scopedOutletId = normalizeOutletId(outletId)
        val safeRange = parseRecapRange(range)
        val safeAnchorDate = normalizeAnchorDate(date)
        val dateFilter = buildDateFilter(
            columnExpression = "t.created_at",
            range = safeRange,
            anchorDate = safeAnchorDate,
        )

        val aggregate = connection.prepareStatement(
            """
            SELECT COUNT(*) AS transaksi_count,
                   COALESCE(SUM(t.total), 0) AS gross_total,
                   COALESCE(SUM(t.discount_plus), 0) AS total_discount
            FROM transaksi_header t
            WHERE t.outlet_id = ?
              AND ${dateFilter.sql}
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, scopedOutletId)
            dateFilter.bind(statement, 2)
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
            dateFilter = dateFilter,
        )
        val topItems = queryProductMovers(
            connection = connection,
            outletId = scopedOutletId,
            dateFilter = dateFilter,
            ascending = false,
            limit = 5,
        )
        val slowItems = queryProductMovers(
            connection = connection,
            outletId = scopedOutletId,
            dateFilter = dateFilter,
            ascending = true,
            limit = 5,
        )

        val transaksiCount = aggregate.first
        val grossTotal = aggregate.second
        val totalDiscount = aggregate.third
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
                    outlet_id TEXT NOT NULL DEFAULT 'default'
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
                    FOREIGN KEY (order_id) REFERENCES order_header(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS transaksi_header (
                    id TEXT PRIMARY KEY,
                    created_at TEXT NOT NULL,
                    meja TEXT,
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
            statement.execute("CREATE INDEX IF NOT EXISTS idx_order_header_outlet_status_date ON order_header(outlet_id, status, created_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transaksi_header_outlet_date ON transaksi_header(outlet_id, created_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_pembayaran_outlet_date ON pembayaran(outlet_id, paid_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_processed_event_outlet_event ON processed_event(outlet_id, event_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_balance_outlet_qty ON stock_item_balance(outlet_id, qty_on_hand)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_ledger_outlet_item_date ON stock_ledger(outlet_id, item_id, created_at)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_threshold_outlet_item ON stock_threshold(outlet_id, item_id)")
        }
    }

    private fun seedCustomersIfNeeded(connection: Connection) {
        val count = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) AS c FROM customer").use { rs ->
                if (rs.next()) rs.getInt("c") else 0
            }
        }
        if (count > 0) return

        val now = Instant.now().toString()
        connection.prepareStatement(
            """
            INSERT INTO customer(uuid, name, created_at)
            VALUES (?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            seededCustomers.forEach { customer ->
                statement.setString(1, customer.uuid)
                statement.setString(2, customer.name)
                statement.setString(3, now)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun seedMenuIfNeeded(connection: Connection) {
        val count = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) AS c FROM menu_item").use { rs ->
                if (rs.next()) rs.getInt("c") else 0
            }
        }
        if (count > 0) return

        val now = Instant.now().toString()
        connection.prepareStatement(
            """
            INSERT INTO menu_item(id, name, price, created_at, outlet_id)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            seededMenu.forEach { item ->
                statement.setString(1, item.id)
                statement.setString(2, item.name)
                statement.setLong(3, item.price)
                statement.setString(4, now)
                statement.setString(5, DEFAULT_OUTLET_ID)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            return block(connection)
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

    private fun listMenuTransactional(connection: Connection, outletId: String): List<MenuItem> {
        connection.prepareStatement(
            """
            SELECT id, name, price, outlet_id
            FROM menu_item
            WHERE outlet_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, outletId)
            statement.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) {
                        add(
                            MenuItem(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                price = rs.getLong("price"),
                                outletId = rs.getString("outlet_id") ?: DEFAULT_OUTLET_ID,
                            )
                        )
                    }
                }
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
            SELECT id, menu_id, item_name, qty, price, line_total
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
                            )
                        )
                    }
                }
            }
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
                id, created_at, meja, discount_plus, tax, service_charge, rounding, total, outlet_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                created_at = excluded.created_at,
                meja = excluded.meja,
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
            statement.setLong(4, transaksi.discountPlus)
            statement.setLong(5, transaksi.tax)
            statement.setLong(6, transaksi.serviceCharge)
            statement.setLong(7, transaksi.rounding)
            statement.setLong(8, transaksi.total)
            statement.setString(9, outletId)
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
                statement.addBatch()
            }
            statement.executeBatch()
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
    ): DateFilterSpec {
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
        }
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
}
