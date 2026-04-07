package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiSyncRepository
import com.durrr.first.data.repo.TransaksiRepository
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.model.Pembayaran
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.domain.model.TransaksiDetail
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.domain.service.TotalsCalculator
import com.durrr.first.network.dto.PembayaranDto
import com.durrr.first.network.dto.TransaksiDetailDto
import com.durrr.first.network.dto.TransaksiDto
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import com.durrr.first.ui.model.ReceiptDraftStore
import kotlinx.coroutines.launch

private data class CartItem(
    val item: Item,
    val qty: Long,
)

@Composable
fun PosScreen(
    menuRepository: MenuRepository,
    transaksiRepository: TransaksiRepository,
    settingsRepository: SettingsRepository,
    transaksiSyncRepository: TransaksiSyncRepository,
    nowIso: () -> String,
    launchScanner: () -> Unit,
    scannedToken: String?,
    onScannedTokenConsumed: () -> Unit,
    onPreviewReceipt: (String) -> Unit = {},
) {
    var menuItems by remember { mutableStateOf(emptyList<Item>()) }
    var cartItems by remember { mutableStateOf(emptyList<CartItem>()) }
    var tableToken by remember { mutableStateOf("") }
    var discountPlus by remember { mutableStateOf("0") }
    var tax by remember { mutableStateOf("0") }
    var serviceCharge by remember { mutableStateOf("0") }
    var rounding by remember { mutableStateOf("0") }
    var paid by remember { mutableStateOf("0") }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val totalsCalculator = remember { TotalsCalculator() }

    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    fun refreshMenu() {
        menuItems = menuRepository.getItems(currentOutletId())
    }

    LaunchedEffect(Unit) { refreshMenu() }
    LaunchedEffect(scannedToken) {
        if (!scannedToken.isNullOrBlank()) {
            tableToken = scannedToken
            onScannedTokenConsumed()
        }
    }

    fun serverBaseUrl(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_SERVER_BASE_URL)
            .ifBlank { "http://10.0.2.2:8080" }
    }

    val totals = totalsCalculator.calculate(
        lines = cartItems.map { TotalsCalculator.Line(it.qty, it.item.price, 0) },
        discountPlus = discountPlus.toLongOrNull() ?: 0L,
        tax = tax.toLongOrNull() ?: 0L,
        serviceCharge = serviceCharge.toLongOrNull() ?: 0L,
        rounding = rounding.toLongOrNull() ?: 0L,
        paid = paid.toLongOrNull() ?: 0L,
    )

    LazyColumn(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader("POS", "Create transaction, preview receipt, checkout cash")
        }
        item {
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Table", style = MaterialTheme.typography.titleMedium)
                        Text(if (tableToken.isBlank()) "No table selected" else tableToken)
                    }
                    Button(onClick = { launchScanner() }) { Text("Scan QR") }
                }
            }
        }
        item {
            AppCard {
                AppSectionHeader("Menu")
                menuItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(item.name)
                            Text("Rp ${item.price}", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            val existing = cartItems.find { it.item.id == item.id }
                            cartItems = if (existing == null) {
                                cartItems + CartItem(item, 1)
                            } else {
                                cartItems.map {
                                    if (it.item.id == item.id) it.copy(qty = it.qty + 1) else it
                                }
                            }
                        }) { Text("Add") }
                    }
                }
            }
        }
        item {
            AppCard {
                AppSectionHeader("Cart")
                if (cartItems.isEmpty()) {
                    Text("Cart is empty")
                } else {
                    cartItems.forEach { cart ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${cart.item.name} x${cart.qty}")
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                                Button(onClick = {
                                    cartItems = cartItems.map {
                                        if (it.item.id == cart.item.id && it.qty > 1) it.copy(qty = it.qty - 1) else it
                                    }
                                }) { Text("-") }
                                Button(onClick = {
                                    cartItems = cartItems.map {
                                        if (it.item.id == cart.item.id) it.copy(qty = it.qty + 1) else it
                                    }
                                }) { Text("+") }
                            }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    AppSectionHeader("Payment Summary", "Set adjustments and confirm cash checkout")
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AmountField(
                            label = "Discount Plus",
                            value = discountPlus,
                            onValueChange = { discountPlus = it },
                            modifier = Modifier.weight(1f),
                        )
                        AmountField(
                            label = "Tax",
                            value = tax,
                            onValueChange = { tax = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AmountField(
                            label = "Service Charge",
                            value = serviceCharge,
                            onValueChange = { serviceCharge = it },
                            modifier = Modifier.weight(1f),
                        )
                        AmountField(
                            label = "Rounding",
                            value = rounding,
                            onValueChange = { rounding = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AmountField(
                        label = "Cash Paid",
                        value = paid,
                        onValueChange = { paid = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider()
                    SummaryLine(label = "Subtotal", amount = totals.subtotal)
                    SummaryLine(label = "Grand Total", amount = totals.grandTotal, emphasized = true)
                    SummaryLine(label = "Change", amount = totals.change, emphasized = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (cartItems.isEmpty()) return@FilledTonalButton
                                val previewId = IdGenerator.newId("preview_")
                                val createdAt = nowIso()
                                val previewTransaksi = Transaksi(
                                    id = previewId,
                                    createdAt = createdAt,
                                    meja = tableToken.ifBlank { null },
                                    discountPlus = discountPlus.toLongOrNull() ?: 0L,
                                    tax = tax.toLongOrNull() ?: 0L,
                                    serviceCharge = serviceCharge.toLongOrNull() ?: 0L,
                                    rounding = rounding.toLongOrNull() ?: 0L,
                                    total = totals.grandTotal,
                                    outletId = currentOutletId(),
                                )
                                val previewDetails = cartItems.map { cart ->
                                    TransaksiDetail(
                                        id = IdGenerator.newId("trd_preview_"),
                                        transaksiId = previewId,
                                        itemId = cart.item.id,
                                        itemName = cart.item.name,
                                        qty = cart.qty,
                                        price = cart.item.price,
                                        discount = 0L,
                                        total = cart.qty * cart.item.price,
                                    )
                                }
                                val previewPayment = Pembayaran(
                                    id = IdGenerator.newId("pay_preview_"),
                                    transaksiId = previewId,
                                    paidAt = createdAt,
                                    amountPaid = paid.toLongOrNull() ?: 0L,
                                    change = totals.change,
                                    paymentTypeId = "CASH",
                                    outletId = currentOutletId(),
                                )
                                ReceiptDraftStore.putDraft(
                                    draftId = previewId,
                                    transaksi = previewTransaksi,
                                    details = previewDetails,
                                    pembayaran = previewPayment,
                                )
                                onPreviewReceipt(previewId)
                            },
                        ) { Text("Preview Receipt") }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (cartItems.isEmpty()) return@Button
                                val transaksiId = IdGenerator.newId("trx_")
                                val createdAt = nowIso()
                                val transaksi = Transaksi(
                                    id = transaksiId,
                                    createdAt = createdAt,
                                    meja = tableToken.ifBlank { null },
                                    discountPlus = discountPlus.toLongOrNull() ?: 0L,
                                    tax = tax.toLongOrNull() ?: 0L,
                                    serviceCharge = serviceCharge.toLongOrNull() ?: 0L,
                                    rounding = rounding.toLongOrNull() ?: 0L,
                                    total = 0L,
                                    outletId = currentOutletId(),
                                )
                                transaksiRepository.createTransaksi(transaksi)

                                val details = cartItems.map { cart ->
                                    val detailTotal = cart.qty * cart.item.price
                                    val detail = TransaksiDetail(
                                        id = IdGenerator.newId("trd_"),
                                        transaksiId = transaksiId,
                                        itemId = cart.item.id,
                                        itemName = cart.item.name,
                                        qty = cart.qty,
                                        price = cart.item.price,
                                        discount = 0L,
                                        total = detailTotal,
                                    )
                                    transaksiRepository.addDetail(detail)
                                    detail
                                }

                                val pembayaran = Pembayaran(
                                    id = IdGenerator.newId("pay_"),
                                    transaksiId = transaksiId,
                                    paidAt = createdAt,
                                    amountPaid = paid.toLongOrNull() ?: 0L,
                                    change = 0L,
                                    paymentTypeId = "CASH",
                                    outletId = currentOutletId(),
                                )

                                transaksiRepository.checkoutCash(
                                    transaksiId = transaksiId,
                                    details = details,
                                    pembayaran = pembayaran,
                                    discountPlus = transaksi.discountPlus,
                                    tax = transaksi.tax,
                                    serviceCharge = transaksi.serviceCharge,
                                    rounding = transaksi.rounding,
                                )

                                val transaksiDto = TransaksiDto(
                                    id = transaksiId,
                                    createdAt = createdAt,
                                    meja = transaksi.meja,
                                    discountPlus = transaksi.discountPlus,
                                    tax = transaksi.tax,
                                    serviceCharge = transaksi.serviceCharge,
                                    rounding = transaksi.rounding,
                                    total = totals.grandTotal,
                                    details = details.map { detail ->
                                        TransaksiDetailDto(
                                            id = detail.id,
                                            itemId = detail.itemId,
                                            itemName = detail.itemName,
                                            qty = detail.qty,
                                            price = detail.price,
                                            discount = detail.discount,
                                            total = detail.total,
                                        )
                                    },
                                    pembayaran = PembayaranDto(
                                        id = pembayaran.id,
                                        paidAt = pembayaran.paidAt,
                                        amountPaid = pembayaran.amountPaid,
                                        change = totals.change,
                                        paymentTypeId = pembayaran.paymentTypeId,
                                    ),
                                )
                                transaksiSyncRepository.enqueueCheckout(transaksiDto, currentOutletId())
                                scope.launch {
                                    isSyncing = true
                                    syncMessage = null
                                    runCatching {
                                        val synced = transaksiSyncRepository.flushPending(
                                            baseUrl = serverBaseUrl(),
                                            outletId = currentOutletId(),
                                        )
                                        "Synced $synced transaction event(s) to server."
                                    }.onFailure {
                                        syncMessage = "Sync pending: ${it.message ?: "Unknown error"}"
                                    }.onSuccess {
                                        syncMessage = it
                                    }
                                    isSyncing = false
                                }

                                cartItems = emptyList()
                                tableToken = ""
                                onScannedTokenConsumed()
                                discountPlus = "0"
                                tax = "0"
                                serviceCharge = "0"
                                rounding = "0"
                                paid = "0"
                            },
                        ) { Text("Checkout Cash") }
                    }
                    if (!syncMessage.isNullOrBlank()) {
                        Text(
                            syncMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSyncing) {
                        Text(
                            "Syncing to server...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = { Text("Rp") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SummaryLine(
    label: String,
    amount: Long,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = "Rp $amount",
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
