package com.durrr.first.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerMenuItemDto(
    val id: String,
    val name: String,
    val price: Long,
    @SerialName("outletId") val outletId: String? = null,
)

@Serializable
data class ServerOrderItemDto(
    val id: String,
    val menuId: String? = null,
    val itemName: String,
    val qty: Long,
    val price: Long,
    val lineTotal: Long,
    val note: String? = null,
)

@Serializable
data class ServerOrderDto(
    val id: String,
    val customerUuid: String? = null,
    val customerName: String? = null,
    val status: String,
    val note: String? = null,
    val paymentConfirmation: String? = null,
    @SerialName("outletId") val outletId: String? = null,
    val createdAt: String,
    val updatedAt: String? = null,
    val total: Long = 0L,
    val items: List<ServerOrderItemDto> = emptyList(),
)

@Serializable
data class ServerOrderStatusRequest(
    val status: String,
    @SerialName("outlet_id") val outletId: String? = null,
)

enum class ServerOrderStatus {
    NEW,
    ACCEPTED,
    PREPARING,
    SERVED,
    DONE,
    CANCELLED,
}
