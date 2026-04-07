package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiRepository
import com.durrr.first.data.repo.TransaksiSyncRepository
import com.durrr.first.domain.model.Pembayaran
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.domain.model.TransaksiDetail
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.domain.service.TotalsCalculator
import com.durrr.first.network.dto.PembayaranDto
import com.durrr.first.network.dto.TransaksiDetailDto
import com.durrr.first.network.dto.TransaksiDto
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import com.durrr.first.ui.model.OrderDraftStore
import com.durrr.first.ui.model.ReceiptDraftStore
import kotlinx.coroutines.launch

@Composable
fun OrderCheckoutScreen(
    draftId: String,
    transaksiRepository: TransaksiRepository,
    settingsRepository: SettingsRepository,
    transaksiSyncRepository: TransaksiSyncRepository,
    nowIso: () -> String,
    onBackToOrders: () -> Unit,
    onPreviewReceipt: (String) -> Unit,
) {
    var draft by remember(draftId) { mutableStateOf(OrderDraftStore.getDraft(draftId)) }
    var discountPlus by remember { mutableStateOf("0") }
    var tax by remember { mutableStateOf("0") }
    var serviceCharge by remember { mutableStateOf("0") }
    var rounding by remember { mutableStateOf("0") }
    var paid by remember { mutableStateOf("0") }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val totalsCalculator = remember { TotalsCalculator() }

    val lines = draft?.lines.orEmpty()
    val totals = totalsCalculator.calculate(
        lines = lines.map { TotalsCalculator.Line(it.qty, it.price, 0) },
        discountPlus = discountPlus.toLongOrNull() ?: 0L,
        tax = tax.toLongOrNull() ?: 0L,
        serviceCharge = serviceCharge.toLongOrNull() ?: 0L,
        rounding = rounding.toLongOrNull() ?: 0L,
        paid = paid.toLongOrNull() ?: 0L,
    )

    fun serverBaseUrl(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_SERVER_BASE_URL)
            .ifBlank { "http://10.0.2.2:8080" }
    }

    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    if (draft == null) {
        Column(
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            AppSectionHeader("Checkout")
            AppEmptyState(
                title = "Order draft not found",
                message = "Go back and create a new order first.",
            )
            Button(onClick = onBackToOrders, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Orders")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader("Checkout", "Finalize payment for current order")
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Text("Table: ${draft?.tableToken ?: "-"}")
                    lines.forEach { line ->
                        Text("${line.itemName} x${line.qty} - Rp ${line.price * line.qty}")
                    }
                }
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    AppSectionHeader("Cash Payment")
                    AmountField(
                        label = "Discount Plus",
                        value = discountPlus,
                        onValueChange = { discountPlus = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AmountField(
                        label = "Tax",
                        value = tax,
                        onValueChange = { tax = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AmountField(
                        label = "Service Charge",
                        value = serviceCharge,
                        onValueChange = { serviceCharge = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AmountField(
                        label = "Rounding",
                        value = rounding,
                        onValueChange = { rounding = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                        Button(
                            onClick = {
                                val previewId = IdGenerator.newId("preview_")
                                val previewCreatedAt = nowIso()
                                val previewTransaksi = Transaksi(
                                    id = previewId,
                                    createdAt = previewCreatedAt,
                                    meja = draft?.tableToken,
                                    discountPlus = discountPlus.toLongOrNull() ?: 0L,
                                    tax = tax.toLongOrNull() ?: 0L,
                                    serviceCharge = serviceCharge.toLongOrNull() ?: 0L,
                                    rounding = rounding.toLongOrNull() ?: 0L,
                                    total = totals.grandTotal,
                                    outletId = currentOutletId(),
                                )
                                val previewDetails = lines.map { line ->
                                    TransaksiDetail(
                                        id = IdGenerator.newId("trd_preview_"),
                                        transaksiId = previewId,
                                        itemId = line.itemId,
                                        itemName = line.itemName,
                                        qty = line.qty,
                                        price = line.price,
                                        discount = 0L,
                                        total = line.qty * line.price,
                                    )
                                }
                                val previewPayment = Pembayaran(
                                    id = IdGenerator.newId("pay_preview_"),
                                    transaksiId = previewId,
                                    paidAt = previewCreatedAt,
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
                            enabled = !processing,
                        ) {
                            Text("Preview")
                        }
                        Button(
                            onClick = {
                                if (processing) return@Button
                                val currentDraft = draft ?: return@Button
                                scope.launch {
                                    processing = true
                                    syncMessage = null
                                    runCatching {
                                        val transaksiId = IdGenerator.newId("trx_")
                                        val createdAt = nowIso()
                                        val header = Transaksi(
                                            id = transaksiId,
                                            createdAt = createdAt,
                                            meja = currentDraft.tableToken,
                                            discountPlus = discountPlus.toLongOrNull() ?: 0L,
                                            tax = tax.toLongOrNull() ?: 0L,
                                            serviceCharge = serviceCharge.toLongOrNull() ?: 0L,
                                            rounding = rounding.toLongOrNull() ?: 0L,
                                            total = 0L,
                                            outletId = currentOutletId(),
                                        )
                                        transaksiRepository.createTransaksi(header)

                                        val details = currentDraft.lines.map { line ->
                                            val detail = TransaksiDetail(
                                                id = IdGenerator.newId("trd_"),
                                                transaksiId = transaksiId,
                                                itemId = line.itemId,
                                                itemName = line.itemName,
                                                qty = line.qty,
                                                price = line.price,
                                                discount = 0L,
                                                total = line.qty * line.price,
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

                                        val checkoutTotals = transaksiRepository.checkoutCash(
                                            transaksiId = transaksiId,
                                            details = details,
                                            pembayaran = pembayaran,
                                            discountPlus = header.discountPlus,
                                            tax = header.tax,
                                            serviceCharge = header.serviceCharge,
                                            rounding = header.rounding,
                                        )

                                        val transaksiDto = TransaksiDto(
                                            id = transaksiId,
                                            createdAt = createdAt,
                                            meja = header.meja,
                                            discountPlus = header.discountPlus,
                                            tax = header.tax,
                                            serviceCharge = header.serviceCharge,
                                            rounding = header.rounding,
                                            total = checkoutTotals.grandTotal,
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
                                                change = checkoutTotals.change,
                                                paymentTypeId = pembayaran.paymentTypeId,
                                            ),
                                        )

                                        transaksiSyncRepository.enqueueCheckout(transaksiDto, currentOutletId())
                                        runCatching {
                                            val synced = transaksiSyncRepository.flushPending(
                                                baseUrl = serverBaseUrl(),
                                                outletId = currentOutletId(),
                                            )
                                            "Checkout saved. Synced $synced event(s)."
                                        }.onFailure {
                                            syncMessage = "Checkout saved locally. Sync pending: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }

                                        OrderDraftStore.removeDraft(draftId)
                                        draft = null
                                        onPreviewReceipt(transaksiId)
                                    }.onFailure {
                                        syncMessage = "Checkout failed: ${it.message ?: "Unknown error"}"
                                    }
                                    processing = false
                                }
                            },
                        ) {
                            Text(if (processing) "Processing..." else "Checkout Cash")
                        }
                    }
                    if (!syncMessage.isNullOrBlank()) {
                        Text(syncMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = onBackToOrders,
                        enabled = !processing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Back to Orders")
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
