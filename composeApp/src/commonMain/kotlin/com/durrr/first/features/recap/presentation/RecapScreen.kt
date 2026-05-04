package com.durrr.first.features.recap.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.RecapRepository
import com.durrr.first.data.repo.RecapSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.CashFlowRepository
import com.durrr.first.domain.model.CashFlowSummary
import com.durrr.first.domain.model.ProductRecap
import com.durrr.first.domain.model.RecapDashboard
import com.durrr.first.domain.model.RecapMetrics
import com.durrr.first.domain.model.RecapRange
import com.durrr.first.ui.design.AppBarChart
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppErrorBanner
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppLoading
import com.durrr.first.ui.design.AppStatusPill
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.design.Dimens

private val RecapBlue = Color(0xFF273BBF)
private val RecapBorder = Color(0xFFD5D9E2)
@Composable
fun RecapScreen(
    recapRepository: RecapRepository,
    cashFlowRepository: CashFlowRepository,
    recapSyncRepository: RecapSyncRepository? = null,
    settingsRepository: SettingsRepository,
    todayDate: () -> String,
    onOpenCashFlow: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenCashClosing: () -> Unit = {},
) {
    var selectedRange by remember { mutableStateOf(RecapRange.ALL) }
    var dashboard by remember { mutableStateOf<RecapDashboard?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dataSource by remember { mutableStateOf("Local") }
    var cashFlowSummary by remember { mutableStateOf<CashFlowSummary?>(null) }

    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    suspend fun load() {
        loading = true
        error = null
        val anchorDate = todayDate()
        val outletId = currentOutletId()
        val baseUrl = settingsRepository
            .getValue(SettingsRepository.KEY_SERVER_BASE_URL)
            .trim()
        val syncRepository = recapSyncRepository
        val remoteCapable = syncRepository != null && baseUrl.isNotBlank()

        if (remoteCapable) {
            runCatching {
                syncRepository.getDashboardSafe(
                    baseUrl = baseUrl,
                    range = selectedRange,
                    anchorDate = anchorDate,
                    outletId = outletId,
                )
            }.onSuccess {
                dashboard = it.dashboard
                dataSource = if (it.usedFallback) "Local" else "Server + Local"
                error = it.warning
            }.onFailure {
                runCatching {
                    recapRepository.getRecap(selectedRange, anchorDate, outletId)
                }.onSuccess { local ->
                    dashboard = local
                    dataSource = "Local"
                    error = "Server recap unavailable. Showing local data."
                }.onFailure { localError ->
                    error = localError.message ?: "Failed to load recap."
                    dashboard = null
                }
            }
        } else {
            runCatching {
                recapRepository.getRecap(selectedRange, anchorDate, outletId)
            }.onSuccess {
                dashboard = it
                dataSource = "Local"
            }.onFailure {
                error = it.message ?: "Failed to load recap."
                dashboard = null
            }
        }
        runCatching {
            cashFlowRepository.getSummary(selectedRange, anchorDate, outletId)
        }.onSuccess {
            cashFlowSummary = it
        }.onFailure {
            cashFlowSummary = null
        }
        loading = false
    }

    LaunchedEffect(selectedRange) {
        load()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
    ) {
        val wide = maxWidth >= 1100.dp
        val snapshot = dashboard

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RangeSwitcher(selected = selectedRange, onSelect = { selectedRange = it })
                            Text(
                                text = "Data source: $dataSource",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            UtilityActionButton("Arus Kas", onOpenCashFlow)
                            UtilityActionButton("Stok", onOpenStock)
                            UtilityActionButton("Kas Closing", onOpenCashClosing)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RangeSwitcher(selected = selectedRange, onSelect = { selectedRange = it })
                        Text(
                            text = "Data source: $dataSource",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B7280),
                        )
                        UtilityActionButton("Arus Kas", onOpenCashFlow)
                        UtilityActionButton("Stok", onOpenStock)
                        UtilityActionButton("Kas Closing", onOpenCashClosing)
                    }
                }
            }
            if (error != null) {
                item { AppErrorBanner(message = error.orEmpty()) }
            }
            if (loading) {
                item { AppLoading("Loading recap...") }
            }
            if (!loading && snapshot != null) {
                item { MetricsGrid(metrics = snapshot.metrics, wide = wide) }
                cashFlowSummary?.let { flow ->
                    item {
                        AppCard {
                            Text(
                                "Ringkasan Arus Kas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            AppInfoLine("Pemasukan", "Rp ${flow.totalCashIn}")
                            AppInfoLine("Refund/Batal", "Rp ${flow.totalRefundOrCancelled}")
                            AppInfoLine("Saldo Bersih", "Rp ${flow.totalCashIn - flow.totalRefundOrCancelled}")
                        }
                    }
                }
                item {
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            ChartPanel(
                                title = "Grafik Perbandingan",
                                modifier = Modifier.weight(2.2f),
                            ) { AppBarChart(points = snapshot.chart, modifier = Modifier.fillMaxWidth()) }
                            ProductPerformancePanel(
                                bestProduct = snapshot.bestProduct,
                                lowestProduct = snapshot.lowestProduct,
                                productInsight = snapshot.productInsight,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            ChartPanel(title = "Grafik Perbandingan") {
                                AppBarChart(points = snapshot.chart, modifier = Modifier.fillMaxWidth())
                            }
                            ProductPerformancePanel(
                                bestProduct = snapshot.bestProduct,
                                lowestProduct = snapshot.lowestProduct,
                                productInsight = snapshot.productInsight,
                            )
                        }
                    }
                }
                item {
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            ProductListSummary(
                                title = "Items Performance",
                                products = snapshot.topProducts,
                                modifier = Modifier.weight(1.35f),
                            )
                            PaymentMethodSummary(
                                methods = snapshot.paymentBreakdown,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            ProductListSummary(title = "Items Performance", products = snapshot.topProducts)
                            PaymentMethodSummary(methods = snapshot.paymentBreakdown)
                        }
                    }
                }
                item {
                    ProductListSummary(
                        title = "Slow Movers",
                        products = snapshot.slowProducts,
                    )
                }
            }
            if (!loading && snapshot == null && error == null) {
                item {
                    AppEmptyState(
                        title = "No recap data",
                        message = "No transaction data for selected period.",
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeSwitcher(
    selected: RecapRange,
    onSelect: (RecapRange) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        listOf(
            RecapRange.TODAY to "Today",
            RecapRange.WEEK to "This Week",
            RecapRange.MONTH to "This Month",
            RecapRange.ALL to "All Time",
        ).forEach { (range, label) ->
            if (selected == range) {
                Button(
                    onClick = { onSelect(range) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = RecapBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RecapBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) { Text(label) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(range) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, RecapBorder),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun MetricsGrid(metrics: RecapMetrics, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RecapMetricCard("Total Pendapatan", "Rp ${metrics.totalSales}", Modifier.weight(1f))
                RecapMetricCard("Laba Kotor", "Rp ${metrics.averagePerTransaction}", Modifier.weight(1f))
                RecapMetricCard("Total Pengeluaran", "Rp ${metrics.totalDiscounts}", Modifier.weight(1f))
            }
        } else {
            RecapMetricCard("Total Pendapatan", "Rp ${metrics.totalSales}")
            RecapMetricCard("Laba Kotor", "Rp ${metrics.averagePerTransaction}")
            RecapMetricCard("Total Pengeluaran", "Rp ${metrics.totalDiscounts}")
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RecapMetricCard("Total Transaksi", metrics.totalTransactions.toString(), Modifier.weight(1f))
                RecapMetricCard("Rata-rata / Transaksi", "Rp ${metrics.averagePerTransaction}", Modifier.weight(1f))
                RecapMetricCard("Diskon", "Rp ${metrics.totalDiscounts}", Modifier.weight(1f))
            }
        } else {
            RecapMetricCard("Total Transaksi", metrics.totalTransactions.toString())
            RecapMetricCard("Rata-rata / Transaksi", "Rp ${metrics.averagePerTransaction}")
            RecapMetricCard("Diskon", "Rp ${metrics.totalDiscounts}")
        }
    }
}

@Composable
private fun RecapMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Text(title, color = Color(0xFF767676), style = MaterialTheme.typography.titleMedium)
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6E6E6E),
        )
    }
}

@Composable
private fun UtilityActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF555555)),
        border = androidx.compose.foundation.BorderStroke(1.dp, RecapBorder),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) { Text(label) }
}

@Composable
private fun ChartPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AppCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ProductPerformancePanel(
    bestProduct: ProductRecap?,
    lowestProduct: ProductRecap?,
    productInsight: String,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Text("Items Performance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ProductSummary(
            title = "Top Seller",
            product = bestProduct,
            emptyMessage = "Belum ada item yang memenuhi minimum top seller.",
        )
        ProductSummary(
            title = "Needs Attention",
            product = lowestProduct,
            emptyMessage = "Belum ada slow mover yang memenuhi minimum evaluasi.",
        )
        RecommendationCallout(
            productInsight = productInsight,
            hasSignal = bestProduct != null || lowestProduct != null,
        )
    }
}

@Composable
private fun ProductSummary(
    title: String,
    product: ProductRecap?,
    emptyMessage: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (product == null) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(product.itemName, fontWeight = FontWeight.SemiBold)
                AppInfoLine(label = "Qty", value = product.qty.toString())
                AppInfoLine(label = "Revenue", value = "Rp ${product.revenue}")
            }
        }
    }
}

@Composable
private fun RecommendationCallout(
    productInsight: String,
    hasSignal: Boolean,
) {
    Surface(
        color = if (hasSignal) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        },
        contentColor = if (hasSignal) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        ) {
            AppStatusPill(
                label = if (hasSignal) "Suggested Action" else "Waiting for More Data",
                containerColor = if (hasSignal) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (hasSignal) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
            Text(
                text = productInsight,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Standar rekomendasi: minimal 3 transaksi, top seller qty >= 5, needs attention qty <= 2.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductListSummary(
    title: String,
    products: List<ProductRecap>,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (products.isEmpty()) {
            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        products.forEach { product ->
            AppInfoLine(
                label = "${product.itemName} • Qty ${product.qty}",
                value = "Rp ${product.revenue}",
            )
        }
    }
}

@Composable
private fun PaymentMethodSummary(
    methods: List<com.durrr.first.domain.model.PaymentMethodRecap>,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Text("Payment Breakdown", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (methods.isEmpty()) {
            Text("No payments yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        methods.forEach { method ->
            AppInfoLine(label = method.methodName, value = "Rp ${method.total}")
        }
    }
}

@Preview
@Composable
fun RecapScreenPreview() {
    var selectedRange by remember { mutableStateOf(RecapRange.TODAY) }
    val sample = RecapDashboard(
        range = selectedRange,
        metrics = RecapMetrics(
            totalSales = 1_850_000,
            totalTransactions = 74,
            averagePerTransaction = 25_000,
            totalDiscounts = 120_000,
        ),
        chart = listOf(
            com.durrr.first.domain.model.RecapChartPoint("Mon", 240_000),
            com.durrr.first.domain.model.RecapChartPoint("Tue", 310_000),
            com.durrr.first.domain.model.RecapChartPoint("Wed", 260_000),
            com.durrr.first.domain.model.RecapChartPoint("Thu", 280_000),
            com.durrr.first.domain.model.RecapChartPoint("Fri", 330_000),
        ),
        bestProduct = ProductRecap("item-1", "Es Teh", 40, 320_000),
        lowestProduct = ProductRecap("item-9", "Brownies", 4, 120_000),
        productInsight = "Bundle Brownies with Es Teh to lift low-performing sales.",
        paymentBreakdown = listOf(
            com.durrr.first.domain.model.PaymentMethodRecap("cash", "Cash", 1_000_000),
            com.durrr.first.domain.model.PaymentMethodRecap("qris", "QRIS", 850_000),
        ),
        topProducts = listOf(
            ProductRecap("item-1", "Es Teh", 40, 320_000),
            ProductRecap("item-2", "Nasi Goreng", 24, 600_000),
        ),
        slowProducts = listOf(
            ProductRecap("item-9", "Brownies", 4, 120_000),
            ProductRecap("item-10", "Kentang", 5, 95_000),
        ),
    )

    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RangeSwitcher(selected = selectedRange, onSelect = { selectedRange = it })
            Text(
                text = "Data source: Preview",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
            )
            MetricsGrid(metrics = sample.metrics, wide = false)
            ProductPerformancePanel(
                bestProduct = sample.bestProduct,
                lowestProduct = sample.lowestProduct,
                productInsight = sample.productInsight,
            )
            ProductListSummary(title = "Items Performance", products = sample.topProducts)
            PaymentMethodSummary(methods = sample.paymentBreakdown)
        }
    }
}
