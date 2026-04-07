package com.durrr.first.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class OrderStatusDto {
    NEW,
    ACCEPTED,
    COOKING,
    SERVED,
    READY,
    DONE,
    CANCELLED,
}

@Serializable
data class OrderItemDto(
    val id: String,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("item_name") val itemName: String,
    val qty: Long,
    val price: Long,
    val note: String? = null,
)

@Serializable
data class OrderDto(
    val id: String,
    val token: String? = null,
    val status: OrderStatusDto,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val items: List<OrderItemDto> = emptyList(),
)

@Serializable
data class CreateOrderRequest(
    val token: String,
    val notes: String? = null,
    val items: List<OrderItemDto>,
)

@Serializable
data class OrderStatusUpdateRequest(
    val status: OrderStatusDto,
)

@Serializable
data class OrderListResponse(
    val orders: List<OrderDto>,
)
