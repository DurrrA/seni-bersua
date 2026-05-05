package com.durrr.first.features.recommendation.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.durrr.first.core.utils.formatNumber
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.features.product.data.BundleStore
import com.durrr.first.features.product.domain.BundleDraft
import com.durrr.first.features.product.domain.BundleItemInput
import com.durrr.first.features.recommendation.data.PromoStore
import com.durrr.first.features.recommendation.domain.PromoDraft
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppSectionHeader
import kotlinx.coroutines.launch

private enum class RecommendationTab(val title: String) {
    BUNDLE("Bundle"),
    PROMO("Promo"),
}

private val ActionBlue = Color(0xFF273BBF)
private const val BUNDLE_GROUP_ID = "grp-rekomendasi-bundle"
private const val BUNDLE_GROUP_NAME = "Bundle"
private const val PROMO_GROUP_ID = "grp-rekomendasi-promo"
private const val PROMO_GROUP_NAME = "Promo"
private const val KEY_RECOMMENDATION_BUNDLES = "recommendation_bundles_v1"
private const val KEY_RECOMMENDATION_PROMOS = "recommendation_promos_v1"
private const val RECORD_SEPARATOR = "\u001E"
private const val FIELD_SEPARATOR = "\u001F"
private const val ITEM_SEPARATOR = "\u001D"
private const val ITEM_FIELD_SEPARATOR = "\u001C"

@Composable
fun RecommendationScreen(
    menuRepository: MenuRepository,
    settingsRepository: SettingsRepository,
    menuSyncRepository: MenuSyncRepository,
    pickDate: (initialIso: String?, onPicked: (String) -> Unit) -> Unit = { _, _ -> },
) {
    val bundles = BundleStore.bundles
    val promos = PromoStore.promos
    var menuItems by remember { mutableStateOf(emptyList<Item>()) }
    var selectedTab by remember { mutableStateOf(RecommendationTab.BUNDLE) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var bundleName by remember { mutableStateOf("") }
    var bundlePrice by remember { mutableStateOf("") }
    var bundleStartDate by remember { mutableStateOf("") }
    var bundleEndDate by remember { mutableStateOf("") }
    val selectedBundleQty = remember { mutableStateMapOf<String, Int>() }

    var promoName by remember { mutableStateOf("") }
    var promoProductId by remember { mutableStateOf<String?>(null) }
    var promoPrice by remember { mutableStateOf("") }
    var promoDiscountPercent by remember { mutableStateOf("") }
    var promoStartDate by remember { mutableStateOf("") }
    var promoEndDate by remember { mutableStateOf("") }

    fun currentOutletId(): String = settingsRepository
        .getValue(SettingsRepository.KEY_OUTLET_ID)
        .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }

    fun persistRecommendationDrafts() {
        settingsRepository.upsert(KEY_RECOMMENDATION_BUNDLES, encodeBundles(bundles))
        settingsRepository.upsert(KEY_RECOMMENDATION_PROMOS, encodePromos(promos))
    }

    fun hydrateRecommendationDraftsFromSettings() {
        if (bundles.isEmpty()) {
            val storedBundles = decodeBundles(settingsRepository.getValue(KEY_RECOMMENDATION_BUNDLES))
            if (storedBundles.isNotEmpty()) bundles.addAll(storedBundles)
        }
        if (promos.isEmpty()) {
            val storedPromos = decodePromos(settingsRepository.getValue(KEY_RECOMMENDATION_PROMOS))
            if (storedPromos.isNotEmpty()) promos.addAll(storedPromos)
        }
    }

    fun syncDraftsWithMenu() {
        val bundleItems = menuItems.filter { it.groupId == BUNDLE_GROUP_ID || it.id.startsWith("menu_bundle_") }
        val promoItems = menuItems.filter { it.groupId == PROMO_GROUP_ID || it.id.startsWith("menu_promo_") }

        val existingBundleById = bundles.associateBy { it.id }
        val syncedBundles = bundleItems.sortedBy { it.name.lowercase() }.map { item ->
            val bundleId = extractBundleId(item.id)
            val existing = existingBundleById[bundleId]
            if (existing != null) {
                existing.copy(name = item.name, price = item.price)
            } else {
                BundleDraft(
                    id = bundleId,
                    name = item.name,
                    startDate = "",
                    endDate = "",
                    price = item.price,
                    items = emptyList(),
                )
            }
        }
        bundles.clear()
        bundles.addAll(syncedBundles)

        val existingPromoById = promos.associateBy { it.id }
        val syncedPromos = promoItems.sortedBy { it.name.lowercase() }.map { item ->
            val promoId = extractPromoId(item.id)
            val existing = existingPromoById[promoId]
            if (existing != null) {
                existing.copy(name = item.name, promoPrice = item.price)
            } else {
                PromoDraft(
                    id = promoId,
                    name = item.name,
                    itemId = item.id,
                    itemName = item.name,
                    discountPercent = 0,
                    promoPrice = item.price,
                    startDate = "",
                    endDate = "",
                )
            }
        }
        promos.clear()
        promos.addAll(syncedPromos)
    }

    fun syncMenuAfterChange(successPrefix: String) {
        val baseUrl = settingsRepository.getOptionalServerBaseUrl()
        if (baseUrl.isNullOrBlank()) {
            message = "$successPrefix Tersimpan lokal. Push menu di Settings untuk kirim ke server."
            return
        }
        scope.launch {
            runCatching {
                val pushed = menuSyncRepository.pushToServer(baseUrl, currentOutletId())
                val pulled = menuSyncRepository.pullFromServer(baseUrl, currentOutletId())
                menuItems = menuRepository
                    .getItems(currentOutletId())
                    .filter { it.isActive }
                    .sortedBy { it.name.lowercase() }
                syncDraftsWithMenu()
                persistRecommendationDrafts()
                "Sync server sukses (push $pushed item, pull $pulled item)."
            }.onSuccess { syncMsg ->
                message = "$successPrefix $syncMsg"
            }.onFailure { error ->
                message = "$successPrefix Sync server gagal: ${error.message ?: "Unknown error"}"
            }
        }
    }

    fun upsertRecommendationGroup(groupId: String, groupName: String) {
        menuRepository.upsertGroup(
            GroupItem(
                id = groupId,
                name = groupName,
                order = 99,
                outletId = currentOutletId(),
            ),
            outletId = currentOutletId(),
        )
    }

    fun refreshMenu() {
        menuItems = menuRepository
            .getItems(currentOutletId())
            .filter { it.isActive }
            .sortedBy { it.name.lowercase() }
    }

    LaunchedEffect(Unit) {
        hydrateRecommendationDraftsFromSettings()
        refreshMenu()
        syncDraftsWithMenu()
        persistRecommendationDrafts()
    }

    val itemById = remember(menuItems) { menuItems.associateBy { it.id } }
    val selectableMenuItems = remember(menuItems) {
        menuItems.filterNot { item ->
            item.groupId == BUNDLE_GROUP_ID ||
                item.groupId == PROMO_GROUP_ID ||
                item.id.startsWith("menu_bundle_") ||
                item.id.startsWith("menu_promo_")
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
    ) {
        val wide = maxWidth >= 1080.dp
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                AppSectionHeader(
                    title = "Rekomendasi",
                    subtitle = "Kelola bundle produk dan promo temporer untuk kasir + dashboard",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecommendationTabButton(
                        title = RecommendationTab.BUNDLE.title,
                        selected = selectedTab == RecommendationTab.BUNDLE,
                        onClick = { selectedTab = RecommendationTab.BUNDLE },
                    )
                    RecommendationTabButton(
                        title = RecommendationTab.PROMO.title,
                        selected = selectedTab == RecommendationTab.PROMO,
                        onClick = { selectedTab = RecommendationTab.PROMO },
                    )
                }
            }
            if (!message.isNullOrBlank()) {
                item {
                    AppCard {
                        Text(message.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                if (selectedTab == RecommendationTab.BUNDLE) {
                    BundleEditorCard(
                        menuItems = selectableMenuItems,
                        selectedQty = selectedBundleQty,
                        bundleName = bundleName,
                        bundlePrice = bundlePrice,
                        bundleStartDate = bundleStartDate,
                        bundleEndDate = bundleEndDate,
                        onBundleNameChange = { bundleName = it },
                        onBundlePriceChange = { bundlePrice = normalizeCurrencyInput(it) },
                        onPickBundleStartDate = {
                            pickDate(bundleStartDate.takeIf { it.isNotBlank() }) { picked ->
                                bundleStartDate = picked
                            }
                        },
                        onPickBundleEndDate = {
                            pickDate(bundleEndDate.takeIf { it.isNotBlank() }) { picked ->
                                bundleEndDate = picked
                            }
                        },
                        onToggleItem = { itemId, checked ->
                            if (checked) {
                                selectedBundleQty[itemId] = selectedBundleQty[itemId] ?: 1
                            } else {
                                selectedBundleQty.remove(itemId)
                            }
                        },
                        onDecreaseQty = { itemId ->
                            val current = selectedBundleQty[itemId] ?: return@BundleEditorCard
                            if (current <= 1) {
                                selectedBundleQty.remove(itemId)
                            } else {
                                selectedBundleQty[itemId] = current - 1
                            }
                        },
                        onIncreaseQty = { itemId ->
                            val current = selectedBundleQty[itemId] ?: 0
                            selectedBundleQty[itemId] = (current + 1).coerceAtMost(99)
                        },
                        onSave = {
                            val name = bundleName.trim()
                            val price = parseCurrencyInput(bundlePrice)
                            val selected = selectedBundleQty.filterValues { it > 0 }
                            if (name.isBlank()) {
                                message = "Nama bundle wajib diisi."
                                return@BundleEditorCard
                            }
                            if (price <= 0L) {
                                message = "Harga bundle harus lebih dari 0."
                                return@BundleEditorCard
                            }
                            if (selected.size < 2) {
                                message = "Pilih minimal 2 produk untuk bundle."
                                return@BundleEditorCard
                            }

                            val bundleItems = selected.mapNotNull { (itemId, qty) ->
                                val item = itemById[itemId] ?: return@mapNotNull null
                                BundleItemInput(
                                    itemId = item.id,
                                    itemName = item.name,
                                    qty = qty,
                                )
                            }

                            bundles.add(
                                BundleDraft(
                                    id = IdGenerator.newId("bundle_"),
                                    name = name,
                                    startDate = bundleStartDate.trim(),
                                    endDate = bundleEndDate.trim(),
                                    price = price,
                                    items = bundleItems,
                                )
                            )
                            val savedBundle = bundles.last()
                            upsertRecommendationGroup(BUNDLE_GROUP_ID, BUNDLE_GROUP_NAME)
                            menuRepository.upsertItem(
                                Item(
                                    id = bundleMenuItemId(savedBundle.id),
                                    name = savedBundle.name,
                                    price = savedBundle.price,
                                    groupId = BUNDLE_GROUP_ID,
                                    code = "BND-${savedBundle.id.takeLast(6).uppercase()}",
                                    isActive = true,
                                    outletId = currentOutletId(),
                                ),
                                outletId = currentOutletId(),
                            )
                            refreshMenu()
                            syncDraftsWithMenu()
                            persistRecommendationDrafts()
                            bundleName = ""
                            bundlePrice = ""
                            bundleStartDate = ""
                            bundleEndDate = ""
                            selectedBundleQty.clear()
                            syncMenuAfterChange("Bundle tersimpan (${bundles.size} total).")
                        },
                    )
                } else {
                    PromoEditorCard(
                        menuItems = menuItems,
                        selectedProductId = promoProductId,
                        promoName = promoName,
                        promoPrice = promoPrice,
                        promoDiscountPercent = promoDiscountPercent,
                        promoStartDate = promoStartDate,
                        promoEndDate = promoEndDate,
                        onPromoNameChange = { promoName = it },
                        onPromoPriceChange = { promoPrice = normalizeCurrencyInput(it) },
                        onPromoDiscountPercentChange = {
                            promoDiscountPercent = it.filter(Char::isDigit).take(2)
                        },
                        onPickPromoStartDate = {
                            pickDate(promoStartDate.takeIf { it.isNotBlank() }) { picked ->
                                promoStartDate = picked
                            }
                        },
                        onPickPromoEndDate = {
                            pickDate(promoEndDate.takeIf { it.isNotBlank() }) { picked ->
                                promoEndDate = picked
                            }
                        },
                        onSelectProduct = { promoProductId = it },
                        onSave = {
                            val item = itemById[promoProductId] ?: run {
                                message = "Pilih produk untuk promo."
                                return@PromoEditorCard
                            }
                            val percent = promoDiscountPercent.toIntOrNull()?.coerceIn(0, 95) ?: 0
                            val customPromoPrice = parseCurrencyInputOrNull(promoPrice)
                            val resolvedPrice = when {
                                customPromoPrice != null && customPromoPrice > 0L -> customPromoPrice
                                percent > 0 -> (item.price - (item.price * percent / 100L)).coerceAtLeast(0L)
                                else -> 0L
                            }
                            if (promoName.trim().isBlank()) {
                                message = "Nama promo wajib diisi."
                                return@PromoEditorCard
                            }
                            if (resolvedPrice <= 0L) {
                                message = "Isi harga promo atau diskon persen yang valid."
                                return@PromoEditorCard
                            }
                            promos.add(
                                PromoDraft(
                                    id = IdGenerator.newId("promo_"),
                                    name = promoName.trim(),
                                    itemId = item.id,
                                    itemName = item.name,
                                    discountPercent = percent,
                                    promoPrice = resolvedPrice,
                                    startDate = promoStartDate.trim(),
                                    endDate = promoEndDate.trim(),
                                )
                            )
                            val savedPromo = promos.last()
                            upsertRecommendationGroup(PROMO_GROUP_ID, PROMO_GROUP_NAME)
                            menuRepository.upsertItem(
                                Item(
                                    id = promoMenuItemId(savedPromo.id),
                                    name = savedPromo.name,
                                    price = savedPromo.promoPrice,
                                    groupId = PROMO_GROUP_ID,
                                    code = "PRM-${savedPromo.id.takeLast(6).uppercase()}",
                                    isActive = true,
                                    outletId = currentOutletId(),
                                ),
                                outletId = currentOutletId(),
                            )
                            refreshMenu()
                            syncDraftsWithMenu()
                            persistRecommendationDrafts()
                            promoName = ""
                            promoPrice = ""
                            promoDiscountPercent = ""
                            promoStartDate = ""
                            promoEndDate = ""
                            promoProductId = null
                            syncMenuAfterChange("Promo tersimpan (${promos.size} total).")
                        },
                    )
                }
            }
            item {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        RecommendationListCard(
                            title = "Daftar Bundle",
                            emptyMessage = "Belum ada bundle tersimpan.",
                            modifier = Modifier.weight(1f),
                        ) {
                            if (bundles.isEmpty()) {
                                AppEmptyState(title = "Bundle kosong", message = "Tambahkan bundle untuk mulai promosi paket.")
                            } else {
                                bundles.forEach { bundle ->
                                    RecommendationBundleRow(
                                        bundle = bundle,
                                        onDelete = {
                                            bundles.remove(bundle)
                                            menuRepository.deleteItem(
                                                itemId = bundleMenuItemId(bundle.id),
                                                outletId = currentOutletId(),
                                            )
                                            refreshMenu()
                                            syncDraftsWithMenu()
                                            persistRecommendationDrafts()
                                            syncMenuAfterChange("Bundle dihapus.")
                                        },
                                    )
                                }
                            }
                        }
                        RecommendationListCard(
                            title = "Daftar Promo",
                            emptyMessage = "Belum ada promo tersimpan.",
                            modifier = Modifier.weight(1f),
                        ) {
                            if (promos.isEmpty()) {
                                AppEmptyState(title = "Promo kosong", message = "Tambahkan promo temporer untuk item yang perlu didorong.")
                            } else {
                                promos.forEach { promo ->
                                    RecommendationPromoRow(
                                        promo = promo,
                                        onDelete = {
                                            promos.remove(promo)
                                            menuRepository.deleteItem(
                                                itemId = promoMenuItemId(promo.id),
                                                outletId = currentOutletId(),
                                            )
                                            refreshMenu()
                                            syncDraftsWithMenu()
                                            persistRecommendationDrafts()
                                            syncMenuAfterChange("Promo dihapus.")
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RecommendationListCard(
                            title = "Daftar Bundle",
                            emptyMessage = "Belum ada bundle tersimpan.",
                        ) {
                            if (bundles.isEmpty()) {
                                AppEmptyState(title = "Bundle kosong", message = "Tambahkan bundle untuk mulai promosi paket.")
                            } else {
                                bundles.forEach { bundle ->
                                    RecommendationBundleRow(
                                        bundle = bundle,
                                        onDelete = {
                                            bundles.remove(bundle)
                                            menuRepository.deleteItem(
                                                itemId = bundleMenuItemId(bundle.id),
                                                outletId = currentOutletId(),
                                            )
                                            refreshMenu()
                                            syncDraftsWithMenu()
                                            persistRecommendationDrafts()
                                            syncMenuAfterChange("Bundle dihapus.")
                                        },
                                    )
                                }
                            }
                        }
                        RecommendationListCard(
                            title = "Daftar Promo",
                            emptyMessage = "Belum ada promo tersimpan.",
                        ) {
                            if (promos.isEmpty()) {
                                AppEmptyState(title = "Promo kosong", message = "Tambahkan promo temporer untuk item yang perlu didorong.")
                            } else {
                                promos.forEach { promo ->
                                    RecommendationPromoRow(
                                        promo = promo,
                                        onDelete = {
                                            promos.remove(promo)
                                            menuRepository.deleteItem(
                                                itemId = promoMenuItemId(promo.id),
                                                outletId = currentOutletId(),
                                            )
                                            refreshMenu()
                                            syncDraftsWithMenu()
                                            persistRecommendationDrafts()
                                            syncMenuAfterChange("Promo dihapus.")
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bundleMenuItemId(bundleId: String): String = "menu_bundle_$bundleId"

private fun promoMenuItemId(promoId: String): String = "menu_promo_$promoId"

private fun extractBundleId(menuItemId: String): String {
    return menuItemId.removePrefix("menu_bundle_").ifBlank { menuItemId }
}

private fun extractPromoId(menuItemId: String): String {
    return menuItemId.removePrefix("menu_promo_").ifBlank { menuItemId }
}

private fun encodeBundles(bundles: List<BundleDraft>): String {
    if (bundles.isEmpty()) return ""
    return bundles.joinToString(RECORD_SEPARATOR) { bundle ->
        val itemsEncoded = bundle.items.joinToString(ITEM_SEPARATOR) { item ->
            listOf(item.itemId, item.itemName, item.qty.toString()).joinToString(ITEM_FIELD_SEPARATOR)
        }
        listOf(
            bundle.id,
            bundle.name,
            bundle.startDate,
            bundle.endDate,
            bundle.price.toString(),
            itemsEncoded,
        ).joinToString(FIELD_SEPARATOR)
    }
}

private fun decodeBundles(raw: String): List<BundleDraft> {
    if (raw.isBlank()) return emptyList()
    return raw.split(RECORD_SEPARATOR)
        .mapNotNull { row ->
            val cols = row.split(FIELD_SEPARATOR)
            if (cols.size < 5) return@mapNotNull null
            val price = cols[4].toLongOrNull() ?: 0L
            val items = cols.getOrNull(5).orEmpty()
                .takeIf { it.isNotBlank() }
                ?.split(ITEM_SEPARATOR)
                ?.mapNotNull { encodedItem ->
                    val itemCols = encodedItem.split(ITEM_FIELD_SEPARATOR)
                    if (itemCols.size < 3) return@mapNotNull null
                    BundleItemInput(
                        itemId = itemCols[0],
                        itemName = itemCols[1],
                        qty = itemCols[2].toIntOrNull() ?: 1,
                    )
                }
                .orEmpty()
            BundleDraft(
                id = cols[0],
                name = cols[1],
                startDate = cols[2],
                endDate = cols[3],
                price = price,
                items = items,
            )
        }
}

private fun encodePromos(promos: List<PromoDraft>): String {
    if (promos.isEmpty()) return ""
    return promos.joinToString(RECORD_SEPARATOR) { promo ->
        listOf(
            promo.id,
            promo.name,
            promo.itemId,
            promo.itemName,
            promo.discountPercent.toString(),
            promo.promoPrice.toString(),
            promo.startDate,
            promo.endDate,
        ).joinToString(FIELD_SEPARATOR)
    }
}

private fun decodePromos(raw: String): List<PromoDraft> {
    if (raw.isBlank()) return emptyList()
    return raw.split(RECORD_SEPARATOR)
        .mapNotNull { row ->
            val cols = row.split(FIELD_SEPARATOR)
            if (cols.size < 8) return@mapNotNull null
            PromoDraft(
                id = cols[0],
                name = cols[1],
                itemId = cols[2],
                itemName = cols[3],
                discountPercent = cols[4].toIntOrNull() ?: 0,
                promoPrice = cols[5].toLongOrNull() ?: 0L,
                startDate = cols[6],
                endDate = cols[7],
            )
        }
}

private fun normalizeCurrencyInput(value: String, maxDigits: Int = 12): String {
    return value.filter(Char::isDigit).take(maxDigits)
}

private fun parseCurrencyInput(raw: String): Long {
    return raw.filter(Char::isDigit).toLongOrNull() ?: 0L
}

private fun parseCurrencyInputOrNull(raw: String): Long? {
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return null
    return digits.toLongOrNull()
}

private fun formatCurrencyInput(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.isBlank()) return ""
    return formatNumber(digits.toLongOrNull() ?: 0L)
}

@Composable
private fun RecommendationTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
        ) {
            Text(title)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(title)
        }
    }
}

@Composable
private fun BundleEditorCard(
    menuItems: List<Item>,
    selectedQty: Map<String, Int>,
    bundleName: String,
    bundlePrice: String,
    bundleStartDate: String,
    bundleEndDate: String,
    onBundleNameChange: (String) -> Unit,
    onBundlePriceChange: (String) -> Unit,
    onPickBundleStartDate: () -> Unit,
    onPickBundleEndDate: () -> Unit,
    onToggleItem: (itemId: String, checked: Boolean) -> Unit,
    onDecreaseQty: (itemId: String) -> Unit,
    onIncreaseQty: (itemId: String) -> Unit,
    onSave: () -> Unit,
) {
    val selectedCount = selectedQty.values.count { it > 0 }
    val itemById = remember(menuItems) { menuItems.associateBy { it.id } }
    val baselineTotal = selectedQty.entries.sumOf { (itemId, qty) ->
        val item = itemById[itemId] ?: return@sumOf 0L
        item.price * qty
    }
    val bundlePriceValue = parseCurrencyInput(bundlePrice)
    val savings = (baselineTotal - bundlePriceValue).coerceAtLeast(0L)

    AppCard {
        AppSectionHeader("Buat Bundle Produk", "Contoh: Coffee + Snack jadi paket promo yang langsung muncul di kasir")
        OutlinedTextField(
            value = bundleName,
            onValueChange = onBundleNameChange,
            label = { Text("Nama Bundle") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = formatCurrencyInput(bundlePrice),
            onValueChange = onBundlePriceChange,
            label = { Text("Harga Bundle") },
            prefix = { Text("Rp") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Harga tersimpan: ${formatRupiah(bundlePriceValue)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (selectedCount > 0) {
                Text(
                    "Pilih: ${formatNumber(selectedCount)} item",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateActionField(
                label = "Mulai",
                value = bundleStartDate,
                placeholder = "Pilih tanggal mulai",
                onClick = onPickBundleStartDate,
                modifier = Modifier.weight(1f),
            )
            DateActionField(
                label = "Selesai",
                value = bundleEndDate,
                placeholder = "Pilih tanggal selesai",
                onClick = onPickBundleEndDate,
                modifier = Modifier.weight(1f),
            )
        }
        if (baselineTotal > 0L) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Harga normal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupiah(baselineTotal), fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Harga bundle", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupiah(bundlePriceValue), fontWeight = FontWeight.SemiBold, color = ActionBlue)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Potensi hemat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupiah(savings), fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Pilih item bundle", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (menuItems.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "Belum ada produk aktif",
                        message = "Tambahkan produk dulu di tab Produk, lalu kembali ke sini untuk bikin bundle.",
                    )
                }
            }
            items(menuItems) { item ->
                val qty = selectedQty[item.id] ?: 0
                val checked = qty > 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(formatRupiah(item.price), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!checked) {
                        OutlinedButton(onClick = { onToggleItem(item.id, true) }) {
                            Text("Pilih")
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = { onDecreaseQty(item.id) }) { Text("−") }
                            Text(
                                formatNumber(qty),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.width(28.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedButton(onClick = { onIncreaseQty(item.id) }) { Text("+") }
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedButton(onClick = { onToggleItem(item.id, false) }) { Text("Hapus") }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Simpan Bundle")
        }
    }
}

@Composable
private fun PromoEditorCard(
    menuItems: List<Item>,
    selectedProductId: String?,
    promoName: String,
    promoPrice: String,
    promoDiscountPercent: String,
    promoStartDate: String,
    promoEndDate: String,
    onPromoNameChange: (String) -> Unit,
    onPromoPriceChange: (String) -> Unit,
    onPromoDiscountPercentChange: (String) -> Unit,
    onPickPromoStartDate: () -> Unit,
    onPickPromoEndDate: () -> Unit,
    onSelectProduct: (String?) -> Unit,
    onSave: () -> Unit,
) {
    val promoPriceValue = parseCurrencyInput(promoPrice)
    AppCard {
        AppSectionHeader("Buat Promo Produk", "Promo sementara untuk item tertentu")
        OutlinedTextField(
            value = promoName,
            onValueChange = onPromoNameChange,
            label = { Text("Nama Promo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text("Pilih produk", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            menuItems.take(24).forEach { item ->
                val selected = selectedProductId == item.id
                if (selected) {
                    Button(
                        onClick = { onSelectProduct(item.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
                    ) {
                        Text(item.name)
                    }
                } else {
                    OutlinedButton(onClick = { onSelectProduct(item.id) }) {
                        Text(item.name)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formatCurrencyInput(promoPrice),
                onValueChange = onPromoPriceChange,
                label = { Text("Harga Promo (opsional)") },
                prefix = { Text("Rp") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = promoDiscountPercent,
                onValueChange = onPromoDiscountPercentChange,
                label = { Text("Diskon %") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateActionField(
                label = "Mulai",
                value = promoStartDate,
                placeholder = "Pilih tanggal mulai",
                onClick = onPickPromoStartDate,
                modifier = Modifier.weight(1f),
            )
            DateActionField(
                label = "Selesai",
                value = promoEndDate,
                placeholder = "Pilih tanggal selesai",
                onClick = onPickPromoEndDate,
                modifier = Modifier.weight(1f),
            )
        }
        if (promoPrice.isNotBlank()) {
            Text(
                text = "Harga promo tersimpan: ${formatRupiah(promoPriceValue)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Simpan Promo")
        }
    }
}

@Composable
private fun DateActionField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (value.isBlank()) placeholder else value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RecommendationListCard(
    title: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
        Text(emptyMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecommendationBundleRow(
    bundle: BundleDraft,
    onDelete: () -> Unit,
) {
    AppCard {
        AppInfoLine("Bundle", bundle.name, emphasized = true)
        AppInfoLine("Harga", formatRupiah(bundle.price))
        AppInfoLine(
            "Periode",
            "${bundle.startDate.ifBlank { "-" }} s/d ${bundle.endDate.ifBlank { "-" }}",
        )
        Text(
            "Items: ${bundle.items.joinToString { "${it.itemName} x${it.qty}" }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("Hapus Bundle")
        }
    }
}

@Composable
private fun RecommendationPromoRow(
    promo: PromoDraft,
    onDelete: () -> Unit,
) {
    AppCard {
        AppInfoLine("Promo", promo.name, emphasized = true)
        AppInfoLine("Produk", promo.itemName)
        AppInfoLine("Harga Promo", formatRupiah(promo.promoPrice))
        AppInfoLine("Diskon", "${promo.discountPercent}%")
        AppInfoLine(
            "Periode",
            "${promo.startDate.ifBlank { "-" }} s/d ${promo.endDate.ifBlank { "-" }}",
        )
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("Hapus Promo")
        }
    }
}
