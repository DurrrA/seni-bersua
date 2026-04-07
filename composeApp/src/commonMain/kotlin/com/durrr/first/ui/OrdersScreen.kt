package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.durrr.first.data.repo.OrderCacheRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.OrderStatus
import com.durrr.first.domain.model.OrderWithItems
import com.durrr.first.network.dto.ServerOrderStatus
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

@Composable
fun OrdersScreen(
    orderRepository: OrderCacheRepository,
    orderSyncRepository: OrderSyncRepository,
    settingsRepository: SettingsRepository,
    onCreateWalkInOrder: () -> Unit = {},
) {
    var orders by remember { mutableStateOf(emptyList<OrderWithItems>()) }
    var isSyncing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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

    fun reloadLocalOrders() {
        orders = orderRepository.getAllOrders(currentOutletId())
    }

    suspend fun pullOrders() {
        isSyncing = true
        statusMessage = null
        try {
            val pulled = orderSyncRepository.pullOrders(
                baseUrl = serverBaseUrl(),
                outletId = currentOutletId(),
            )
            reloadLocalOrders()
            statusMessage = "Synced $pulled order(s) from server."
        } catch (t: Throwable) {
            statusMessage = "Sync failed: ${t.message ?: "Unknown error"}"
        } finally {
            isSyncing = false
        }
    }

    suspend fun advanceStatus(order: OrderWithItems) {
        val next = nextServerStatus(order.header.status) ?: return
        isSyncing = true
        statusMessage = null
        try {
            orderSyncRepository.updateStatus(
                baseUrl = serverBaseUrl(),
                orderId = order.header.id,
                targetStatus = next,
                outletId = currentOutletId(),
            )
            reloadLocalOrders()
            statusMessage = "Order ${order.header.id} updated to ${next.name}."
        } catch (t: Throwable) {
            statusMessage = "Update failed: ${t.message ?: "Unknown error"}"
        } finally {
            isSyncing = false
        }
    }

    LaunchedEffect(Unit) {
        reloadLocalOrders()
        pullOrders()
    }

    Column(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        AppSectionHeader("Orders", "Synced from server and cached locally")

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Button(
                        onClick = onCreateWalkInOrder,
                        enabled = !isSyncing,
                    ) {
                        Text("New Walk-in Order")
                    }
                    Button(
                        onClick = { scope.launch { pullOrders() } },
                        enabled = !isSyncing,
                    ) {
                        Text(if (isSyncing) "Syncing..." else "Pull Orders")
                    }
                }
            }
        }

        if (orders.isEmpty()) {
            AppEmptyState(
                title = "No orders yet",
                message = "Pull from server to fetch incoming customer orders.",
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                items(orders) { order ->
                    val actionLabel = nextActionLabel(order.header.status)
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Text("Order ${order.header.id}", style = MaterialTheme.typography.titleMedium)
                            Text("Status: ${statusLabel(order.header.status)}")
                            Text("Customer Token: ${order.header.token ?: "-"}")
                            if (!order.header.notes.isNullOrBlank()) {
                                Text(order.header.notes ?: "")
                            }
                            order.items.forEach { item ->
                                Text("${item.itemName} x${item.qty} - Rp ${item.price}")
                            }
                            if (actionLabel != null) {
                                Text(
                                    text = "Next Step: $actionLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { scope.launch { advanceStatus(order) } },
                                    enabled = !isSyncing,
                                ) {
                                    Text(actionLabel)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun nextServerStatus(status: OrderStatus): ServerOrderStatus? {
    return when (status) {
        OrderStatus.NEW -> ServerOrderStatus.ACCEPTED
        OrderStatus.ACCEPTED -> ServerOrderStatus.PREPARING
        OrderStatus.COOKING -> ServerOrderStatus.SERVED
        OrderStatus.SERVED -> ServerOrderStatus.DONE
        OrderStatus.DONE -> null
        OrderStatus.CANCELLED -> null
    }
}

private fun nextActionLabel(status: OrderStatus): String? {
    return when (status) {
        OrderStatus.NEW -> "Accept"
        OrderStatus.ACCEPTED -> "Start Cooking"
        OrderStatus.COOKING -> "Mark Served"
        OrderStatus.SERVED -> "Done"
        OrderStatus.DONE -> null
        OrderStatus.CANCELLED -> null
    }
}

private fun statusLabel(status: OrderStatus): String {
    return when (status) {
        OrderStatus.NEW -> "New"
        OrderStatus.ACCEPTED -> "Accepted"
        OrderStatus.COOKING -> "Cooking"
        OrderStatus.SERVED -> "Served"
        OrderStatus.DONE -> "Done"
        OrderStatus.CANCELLED -> "Cancelled"
    }
}
