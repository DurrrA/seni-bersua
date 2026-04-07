package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.ReceiptRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.ReceiptData
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppLoading
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import com.durrr.first.ui.model.ReceiptDraftStore
import com.durrr.first.ui.platform.PlatformUriImage

@Composable
fun ReceiptPreviewScreen(
    transaksiId: String,
    receiptRepository: ReceiptRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit = {},
) {
    var loading by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<ReceiptData?>(null) }
    var storeName by remember { mutableStateOf("SuCash") }
    var storeAddress by remember { mutableStateOf("") }
    var headerLogoPath by remember { mutableStateOf("") }
    var watermarkLogoPath by remember { mutableStateOf("") }
    var footerText by remember { mutableStateOf("Thank you") }
    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    LaunchedEffect(transaksiId) {
        loading = true
        val config = settingsRepository.loadReceiptConfig()
        storeName = config.storeName
        storeAddress = config.storeAddressOrPhone
        headerLogoPath = config.headerLogoPath
        watermarkLogoPath = config.watermarkLogoPath
        footerText = config.footerText
        data = ReceiptDraftStore.getDraft(transaksiId)
            ?: receiptRepository.getReceiptData(transaksiId, currentOutletId())
        loading = false
    }

    if (loading) {
        AppLoading("Preparing receipt preview...")
        return
    }

    val receipt = data
    if (receipt == null) {
        AppEmptyState(
            title = "Receipt not found",
            message = "No receipt data for $transaksiId",
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader(
                title = "Receipt Preview",
                subtitle = "Print-ready thermal layout",
            )
        }
        item {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
            ) {
                Text("Back")
            }
        }
        item {
            ReceiptPaper(
                receipt = receipt,
                storeName = storeName,
                storeAddress = storeAddress,
                headerLogoPath = headerLogoPath,
                watermarkLogoPath = watermarkLogoPath,
                footerText = footerText,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: print via Android print/printer SDK */ },
                ) {
                    Text("Print (TODO)")
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: export/share PNG */ },
                ) {
                    Text("Share / Save")
                }
            }
        }
        item {
            Text(
                text = "Next step: connect this layout to printer output.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReceiptPaper(
    receipt: ReceiptData,
    storeName: String,
    storeAddress: String,
    headerLogoPath: String,
    watermarkLogoPath: String,
    footerText: String,
) {
    val subtotal = receipt.details.sumOf { it.total }
    val payment = receipt.pembayaran

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (watermarkLogoPath.isNotBlank()) {
                PlatformUriImage(
                    uri = watermarkLogoPath,
                    contentDescription = "Receipt watermark logo",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.6f)
                        .height(120.dp),
                    alpha = 0.5f,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.xxs),
            ) {
                if (headerLogoPath.isNotBlank()) {
                    PlatformUriImage(
                        uri = headerLogoPath,
                        contentDescription = "Receipt header logo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        alpha = 1f,
                    )
                }
                Text(
                    text = storeName.ifBlank { "SuCash" },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                if (storeAddress.isNotBlank()) {
                    Text(
                        text = storeAddress,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black,
                    )
                }
                ReceiptDivider()
                ReceiptValueRow("Transaction", receipt.transaksi.id)
                ReceiptValueRow("Date", receipt.transaksi.createdAt)
                ReceiptValueRow("Table", receipt.transaksi.meja ?: "-")
                ReceiptDivider()

                Text(
                    text = "ITEMS",
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                if (receipt.details.isEmpty()) {
                    Text(
                        text = "-",
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black,
                    )
                } else {
                    receipt.details.forEach { detail ->
                        Text(
                            text = detail.itemName,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                        )
                        ReceiptValueRow(
                            label = "${detail.qty} x ${formatRupiah(detail.price)}",
                            value = formatRupiah(detail.total),
                        )
                    }
                }

                ReceiptDivider()
                ReceiptValueRow("Subtotal", formatRupiah(subtotal))
                ReceiptValueRow("Discount", formatRupiah(receipt.transaksi.discountPlus))
                ReceiptValueRow("Tax", formatRupiah(receipt.transaksi.tax))
                ReceiptValueRow("Service", formatRupiah(receipt.transaksi.serviceCharge))
                ReceiptValueRow("Rounding", formatRupiah(receipt.transaksi.rounding))
                ReceiptDivider()
                ReceiptValueRow("TOTAL", formatRupiah(receipt.transaksi.total), emphasized = true)
                ReceiptValueRow("Paid", formatRupiah(payment?.amountPaid ?: 0L))
                ReceiptValueRow("Change", formatRupiah(payment?.change ?: 0L), emphasized = true)
                ReceiptDivider()
                Text(
                    text = footerText.ifBlank { "Thank you" },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun ReceiptValueRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
        )
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
        )
    }
}

@Composable
private fun ReceiptDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = Dimens.xxs),
        color = Color.Black.copy(alpha = 0.3f),
    )
}

private fun formatRupiah(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val absString = kotlin.math.abs(value).toString()
    val grouped = absString.reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return "$sign" + "Rp $grouped"
}
