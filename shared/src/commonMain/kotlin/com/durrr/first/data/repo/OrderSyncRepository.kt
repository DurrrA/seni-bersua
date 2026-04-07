package com.durrr.first.data.repo

import com.durrr.first.domain.model.OrderHeader
import com.durrr.first.domain.model.OrderItem
import com.durrr.first.domain.model.OrderStatus
import com.durrr.first.network.ServerApiClient
import com.durrr.first.network.dto.ServerOrderDto
import com.durrr.first.network.dto.ServerOrderStatus
import kotlin.math.max

class OrderSyncRepository(
    private val orderCacheRepository: OrderCacheRepository,
    private val apiClient: ServerApiClient,
) {
    suspend fun pullOrders(baseUrl: String, outletId: String? = null): Int {
        val remoteOrders = apiClient.fetchOrders(
            baseUrl = baseUrl,
            statuses = listOf(
                ServerOrderStatus.NEW,
                ServerOrderStatus.ACCEPTED,
                ServerOrderStatus.PREPARING,
                ServerOrderStatus.SERVED,
                ServerOrderStatus.DONE,
            ),
            outletId = outletId,
        )
        remoteOrders.forEach(::cacheRemoteOrder)
        return remoteOrders.size
    }

    suspend fun updateStatus(
        baseUrl: String,
        orderId: String,
        targetStatus: ServerOrderStatus,
        outletId: String? = null,
    ) {
        val updated = apiClient.updateOrderStatus(baseUrl, orderId, targetStatus, outletId)
        cacheRemoteOrder(updated)
    }

    private fun cacheRemoteOrder(order: ServerOrderDto) {
        val notes = listOfNotNull(
            order.customerName?.takeIf { it.isNotBlank() }?.let { "Customer: $it" },
            order.paymentConfirmation?.takeIf { it.isNotBlank() }?.let { raw ->
                if (raw.equals("CASHIER", ignoreCase = true)) "Payment: At Cashier" else "Payment: $raw"
            },
            order.note?.takeIf { it.isNotBlank() },
        ).joinToString(" | ").ifBlank { null }

        val header = OrderHeader(
            id = order.id,
            token = order.customerUuid,
            status = mapStatus(order.status),
            notes = notes,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
            outletId = order.outletId,
        )
        val items = order.items.map { item ->
            OrderItem(
                id = item.id,
                orderId = order.id,
                itemId = item.menuId,
                itemName = item.itemName,
                qty = max(1, item.qty),
                price = max(0, item.price),
                note = item.note,
            )
        }
        orderCacheRepository.upsertOrder(
            order = header,
            items = items,
            outletId = order.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
        )
    }

    private fun mapStatus(status: String): OrderStatus {
        return when (status.uppercase()) {
            "NEW" -> OrderStatus.NEW
            "ACCEPTED" -> OrderStatus.ACCEPTED
            "PREPARING" -> OrderStatus.COOKING
            "SERVED" -> OrderStatus.SERVED
            "DONE" -> OrderStatus.DONE
            "CANCELLED" -> OrderStatus.CANCELLED
            else -> OrderStatus.NEW
        }
    }
}
