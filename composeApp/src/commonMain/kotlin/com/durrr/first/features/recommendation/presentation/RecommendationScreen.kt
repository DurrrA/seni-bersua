package com.durrr.first.features.recommendation.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durrr.first.core.utils.formatNumber
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.SettingsRepository
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

private enum class RecommendationTab(val title: String) {
    BUNDLE("Bundle"),
    PROMO("Promo"),
}

private val ActionBlue = Color(0xFF273BBF)

@Composable
fun RecommendationScreen(
    menuRepository: MenuRepository,
    settingsRepository: SettingsRepository,
) {
    val bundles = BundleStore.bundles
    val promos = PromoStore.promos
    var menuItems by remember { mutableStateOf(emptyList<Item>()) }
    var selectedTab by remember { mutableStateOf(RecommendationTab.BUNDLE) }
    var message by remember { mutableStateOf<String?>(null) }

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

    fun refreshMenu() {
        menuItems = menuRepository
            .getItems(currentOutletId())
            .filter { it.isActive }
            .sortedBy { it.name.lowercase() }
    }

    LaunchedEffect(Unit) {
        refreshMenu()
    }

    val itemById = remember(menuItems) { menuItems.associateBy { it.id } }

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
                        menuItems = menuItems,
                        selectedQty = selectedBundleQty,
                        bundleName = bundleName,
                        bundlePrice = bundlePrice,
                        bundleStartDate = bundleStartDate,
                        bundleEndDate = bundleEndDate,
                        onBundleNameChange = { bundleName = it },
                        onBundlePriceChange = { bundlePrice = it.filter(Char::isDigit) },
                        onBundleStartDateChange = { bundleStartDate = it },
                        onBundleEndDateChange = { bundleEndDate = it },
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
                            val price = bundlePrice.toLongOrNull() ?: 0L
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
                            bundleName = ""
                            bundlePrice = ""
                            bundleStartDate = ""
                            bundleEndDate = ""
                            selectedBundleQty.clear()
                            message = "Bundle tersimpan (${bundles.size} total)."
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
                        onPromoPriceChange = { promoPrice = it.filter(Char::isDigit) },
                        onPromoDiscountPercentChange = {
                            promoDiscountPercent = it.filter(Char::isDigit).take(2)
                        },
                        onPromoStartDateChange = { promoStartDate = it },
                        onPromoEndDateChange = { promoEndDate = it },
                        onSelectProduct = { promoProductId = it },
                        onSave = {
                            val item = itemById[promoProductId] ?: run {
                                message = "Pilih produk untuk promo."
                                return@PromoEditorCard
                            }
                            val percent = promoDiscountPercent.toIntOrNull()?.coerceIn(0, 95) ?: 0
                            val customPromoPrice = promoPrice.toLongOrNull()
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
                            promoName = ""
                            promoPrice = ""
                            promoDiscountPercent = ""
                            promoStartDate = ""
                            promoEndDate = ""
                            promoProductId = null
                            message = "Promo tersimpan (${promos.size} total)."
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
                                        onDelete = { bundles.remove(bundle) },
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
                                        onDelete = { promos.remove(promo) },
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
                                        onDelete = { bundles.remove(bundle) },
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
                                        onDelete = { promos.remove(promo) },
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
    onBundleStartDateChange: (String) -> Unit,
    onBundleEndDateChange: (String) -> Unit,
    onToggleItem: (itemId: String, checked: Boolean) -> Unit,
    onDecreaseQty: (itemId: String) -> Unit,
    onIncreaseQty: (itemId: String) -> Unit,
    onSave: () -> Unit,
) {
    AppCard {
        AppSectionHeader("Buat Bundle Produk", "Contoh: Coffee + Snack jadi paket promo")
        OutlinedTextField(
            value = bundleName,
            onValueChange = onBundleNameChange,
            label = { Text("Nama Bundle") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = bundlePrice,
            onValueChange = onBundlePriceChange,
            label = { Text("Harga Bundle") },
            prefix = { Text("Rp") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = bundleStartDate,
                onValueChange = onBundleStartDateChange,
                label = { Text("Mulai (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = bundleEndDate,
                onValueChange = onBundleEndDateChange,
                label = { Text("Selesai (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        Text("Pilih item bundle", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(menuItems) { item ->
                val qty = selectedQty[item.id] ?: 0
                val checked = qty > 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onDecreaseQty(item.id) }) { Text("-") }
                            Text(
                                formatNumber(qty),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            OutlinedButton(onClick = { onIncreaseQty(item.id) }) { Text("+") }
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
    onPromoStartDateChange: (String) -> Unit,
    onPromoEndDateChange: (String) -> Unit,
    onSelectProduct: (String?) -> Unit,
    onSave: () -> Unit,
) {
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
                value = promoPrice,
                onValueChange = onPromoPriceChange,
                label = { Text("Harga Promo (opsional)") },
                prefix = { Text("Rp") },
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
            OutlinedTextField(
                value = promoStartDate,
                onValueChange = onPromoStartDateChange,
                label = { Text("Mulai (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = promoEndDate,
                onValueChange = onPromoEndDateChange,
                label = { Text("Selesai (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
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
