package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.durrr.first.data.repo.RecapRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.ProductRecap
import com.durrr.first.domain.model.RecapDashboard
import com.durrr.first.domain.model.RecapMetrics
import com.durrr.first.domain.model.RecapRange
import com.durrr.first.ui.design.AppBarChart
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppErrorBanner
import com.durrr.first.ui.design.AppLoading
import com.durrr.first.ui.design.AppMetricTile
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens

@Composable
fun RecapScreen(
    recapRepository: RecapRepository,
    settingsRepository: SettingsRepository,
    todayDate: () -> String,
    onOpenCashFlow: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenCashClosing: () -> Unit = {},
) {
    var selectedRange by remember { mutableStateOf(RecapRange.TODAY) }
    var dashboard by remember { mutableStateOf<RecapDashboard?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    fun load() {
        loading = true
        error = null
        runCatching {
            recapRepository.getRecap(selectedRange, todayDate(), currentOutletId())
        }.onSuccess {
            dashboard = it
        }.onFailure {
            error = it.message ?: "Failed to load recap."
            dashboard = null
        }
        loading = false
    }

    LaunchedEffect(selectedRange) {
        load()
    }

    LazyColumn(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader(
                title = "Recap Dashboard",
                subtitle = "Today, week, and month summary from local transactions",
            )
        }
        item {
            RangeSwitcher(
                selected = selectedRange,
                onSelect = { selectedRange = it },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Button(onClick = onOpenCashFlow, modifier = Modifier.weight(1f)) {
                    Text("Cash Flow")
                }
                Button(onClick = onOpenStock, modifier = Modifier.weight(1f)) {
                    Text("Stock")
                }
                Button(onClick = onOpenCashClosing, modifier = Modifier.weight(1f)) {
                    Text("Cash Close")
                }
            }
        }
        item {
            if (error != null) {
                AppErrorBanner(message = error.orEmpty())
            }
        }
        item {
            if (loading) {
                AppLoading("Loading recap...")
            }
        }
        val snapshot = dashboard
        if (!loading && snapshot != null) {
            item {
                MetricsGrid(metrics = snapshot.metrics)
            }
            item {
                AppSectionHeader("Sales Chart")
            }
            item {
                AppBarChart(points = snapshot.chart)
            }
            item {
                AppSectionHeader("Products")
            }
            item {
                ProductSummary(
                    title = "Best Product",
                    product = snapshot.bestProduct,
                )
            }
            item {
                ProductSummary(
                    title = "Lowest Product",
                    product = snapshot.lowestProduct,
                )
            }
            item {
                PaymentMethodSummary(snapshot.paymentBreakdown)
            }
            item {
                ProductListSummary(
                    title = "Top Movers",
                    products = snapshot.topProducts,
                )
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
        ).forEach { (range, label) ->
            if (selected == range) {
                Button(onClick = { onSelect(range) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(range) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun MetricsGrid(metrics: RecapMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            AppMetricTile(
                modifier = Modifier.weight(1f),
                label = "Total Sales",
                value = "Rp ${metrics.totalSales}",
            )
            AppMetricTile(
                modifier = Modifier.weight(1f),
                label = "Transactions",
                value = metrics.totalTransactions.toString(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            AppMetricTile(
                modifier = Modifier.weight(1f),
                label = "Avg / Transaction",
                value = "Rp ${metrics.averagePerTransaction}",
            )
            AppMetricTile(
                modifier = Modifier.weight(1f),
                label = "Discounts",
                value = "Rp ${metrics.totalDiscounts}",
            )
        }
    }
}

@Composable
private fun ProductSummary(
    title: String,
    product: ProductRecap?,
) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (product == null) {
            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("TODO: optimize product query for large datasets", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(product.itemName)
            Text("Qty: ${product.qty}")
            Text("Revenue: Rp ${product.revenue}")
        }
    }
}

@Composable
private fun ProductListSummary(
    title: String,
    products: List<ProductRecap>,
) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (products.isEmpty()) {
            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        products.forEach { product ->
            Text("${product.itemName} | Qty ${product.qty} | Rp ${product.revenue}")
        }
    }
}

@Composable
private fun PaymentMethodSummary(
    methods: List<com.durrr.first.domain.model.PaymentMethodRecap>,
) {
    AppCard {
        Text("Payment Breakdown", style = MaterialTheme.typography.titleMedium)
        if (methods.isEmpty()) {
            Text("No payments yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppCard
        }
        methods.forEach { method ->
            Text("${method.methodName}: Rp ${method.total}")
        }
    }
}
