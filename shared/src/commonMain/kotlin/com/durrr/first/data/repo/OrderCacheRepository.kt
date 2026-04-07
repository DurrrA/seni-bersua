package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.OrderHeader
import com.durrr.first.domain.model.OrderItem
import com.durrr.first.domain.model.OrderStatus
import com.durrr.first.domain.model.OrderWithItems

class OrderCacheRepository(private val db: TokoDatabase) {
    fun upsertOrder(
        order: OrderHeader,
        items: List<OrderItem>,
        outletId: String = order.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        db.transaction {
            db.tokoQueries.upsertOrderHeader(
                id_order = order.id,
                token = order.token,
                status = order.status.name,
                notes = order.notes,
                created_at = order.createdAt,
                updated_at = order.updatedAt,
                outlet_id = outletId,
            )
            db.tokoQueries.deleteOrderItemsByOrder(order.id)
            items.forEach { item ->
                db.tokoQueries.insertOrderItem(
                    id_order_item = item.id,
                    id_order = item.orderId,
                    id_item = item.itemId,
                    nama_item = item.itemName,
                    qty = item.qty,
                    price = item.price,
                    note = item.note,
                )
            }
        }
    }

    fun getOrdersByStatus(
        status: OrderStatus,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): List<OrderWithItems> {
        val headers = db.tokoQueries.selectOrdersByStatus(status.name, outletId).executeAsList()
        return mapOrders(headers)
    }

    fun getAllOrders(outletId: String = SettingsRepository.DEFAULT_OUTLET_ID): List<OrderWithItems> {
        val headers = db.tokoQueries.selectAllOrders(outletId).executeAsList()
        return mapOrders(headers)
    }

    private fun mapOrders(headers: List<com.durrr.first.Order_header>): List<OrderWithItems> {
        return headers.map { header ->
            val items = db.tokoQueries.selectOrderItemsByOrder(header.id_order).executeAsList().map { item ->
                OrderItem(
                    id = item.id_order_item,
                    orderId = item.id_order,
                    itemId = item.id_item,
                    itemName = item.nama_item,
                    qty = item.qty,
                    price = item.price,
                    note = item.note,
                )
            }
            OrderWithItems(
                header = OrderHeader(
                    id = header.id_order,
                    token = header.token,
                    status = parseStatus(header.status),
                    notes = header.notes,
                    createdAt = header.created_at,
                    updatedAt = header.updated_at,
                    outletId = header.outlet_id,
                ),
                items = items,
            )
        }
    }

    private fun parseStatus(raw: String): OrderStatus {
        return when (raw.uppercase()) {
            "NEW" -> OrderStatus.NEW
            "ACCEPTED" -> OrderStatus.ACCEPTED
            "READY", "PREPARING", "COOKING" -> OrderStatus.COOKING
            "SERVED" -> OrderStatus.SERVED
            "DONE" -> OrderStatus.DONE
            "CANCELLED" -> OrderStatus.CANCELLED
            else -> OrderStatus.NEW
        }
    }
}
