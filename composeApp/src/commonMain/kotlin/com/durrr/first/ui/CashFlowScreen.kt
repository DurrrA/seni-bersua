package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.durrr.first.data.repo.CashFlowRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.CashFlowSummary
import com.durrr.first.domain.model.RecapRange
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppLoading
import com.durrr.first.ui.design.AppMetricTile
import com.durrr.first.ui.design.Dimens

@Composable
fun CashFlowScreen(
    repository: CashFlowRepository,
    settingsRepository: SettingsRepository,
    todayDate: () -> String,
) {
    var selectedRange by remember { mutableStateOf(RecapRange.TODAY) }
    var summary by remember { mutableStateOf<CashFlowSummary?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    fun load() {
        loading = true
        summary = repository.getSummary(selectedRange, todayDate(), currentOutletId())
        loading = false
    }

    LaunchedEffect(selectedRange) {
        load()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                listOf(
                    RecapRange.TODAY to "Today",
                    RecapRange.WEEK to "Week",
                    RecapRange.MONTH to "Month",
                ).forEach { (range, label) ->
                    if (range == selectedRange) {
                        Button(onClick = { selectedRange = range }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { selectedRange = range }) { Text(label) }
                    }
                }
            }
        }

        if (loading) {
            item { AppLoading("Loading cash flow...") }
        }

        val data = summary
        if (!loading && data != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    AppMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Total Cash In",
                        value = "Rp ${data.totalCashIn}",
                    )
                    AppMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "Refund/Cancelled",
                        value = "Rp ${data.totalRefundOrCancelled}",
                    )
                }
            }
            item {
                AppCard {
                    Text("By Method", style = MaterialTheme.typography.titleMedium)
                    if (data.byMethod.isEmpty()) {
                        Text("No data")
                    } else {
                        data.byMethod.forEach { method ->
                            Text("${method.methodName}: Rp ${method.total}")
                        }
                    }
                }
            }
            item {
                AppCard {
                    Text("Recent Entries", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(data.recentEntries) { entry ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                        Text("Method: ${entry.methodName}")
                        Text("Amount: Rp ${entry.amount}")
                        Text("Ref: ${entry.transaksiId ?: "-"}")
                        Text(entry.dateTime ?: "-")
                    }
                }
            }
        }

        if (!loading && data == null) {
            item {
                AppEmptyState(
                    title = "No cash flow data",
                    message = "No data for selected period.",
                )
            }
        }
    }
}
