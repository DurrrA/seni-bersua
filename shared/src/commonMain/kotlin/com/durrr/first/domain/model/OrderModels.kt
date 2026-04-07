package com.durrr.first.domain.model

enum class OrderStatus {
    NEW,
    ACCEPTED,
    COOKING,
    SERVED,
    DONE,
    CANCELLED,
}

data class OrderHeader(
    val id: String,
    val token: String?,
    val status: OrderStatus,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String?,
    val outletId: String? = null,
)

data class OrderItem(
    val id: String,
    val orderId: String,
    val itemId: String?,
    val itemName: String,
    val qty: Long,
    val price: Long,
    val note: String?,
)

data class OrderWithItems(
    val header: OrderHeader,
    val items: List<OrderItem>,
)
