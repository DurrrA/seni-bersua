package com.durrr.first.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerMenuItemDto(
    val id: String,
    val name: String,
    val price: Long,
    @SerialName("groupId") val groupId: String? = null,
    @SerialName("groupName") val groupName: String? = null,
    @SerialName("outletId") val outletId: String? = null,
)

@Serializable
data class ServerModifierOptionDto(
    val id: String,
    val name: String,
    @SerialName("priceDelta") val priceDelta: Long = 0,
    @SerialName("order") val order: Int = 0,
    @SerialName("isDefault") val isDefault: Boolean = false,
)

@Serializable
data class ServerModifierGroupDto(
    val id: String,
    val name: String,
    @SerialName("selectionType") val selectionType: String = "SINGLE",
    @SerialName("isRequired") val isRequired: Boolean = false,
    @SerialName("maxSelection") val maxSelection: Int = 1,
    val options: List<ServerModifierOptionDto> = emptyList(),
)

@Serializable
data class ServerProductModifierLinkDto(
    @SerialName("itemId") val itemId: String,
    @SerialName("modifierGroupIds") val modifierGroupIds: List<String> = emptyList(),
)

@Serializable
data class ServerMenuCatalogDto(
    val items: List<ServerMenuItemDto> = emptyList(),
    @SerialName("modifierGroups") val modifierGroups: List<ServerModifierGroupDto> = emptyList(),
    @SerialName("productModifierLinks") val productModifierLinks: List<ServerProductModifierLinkDto> = emptyList(),
)

@Serializable
data class UpsertModifierGroupRequest(
    val id: String,
    val name: String,
    @SerialName("selection_type") val selectionType: String = "SINGLE",
    @SerialName("is_required") val isRequired: Boolean = false,
    @SerialName("max_selection") val maxSelection: Int = 1,
    val options: List<ServerModifierOptionDto> = emptyList(),
    @SerialName("outlet_id") val outletId: String? = null,
)

@Serializable
data class AssignProductModifiersRequest(
    @SerialName("modifier_group_ids") val modifierGroupIds: List<String> = emptyList(),
    @SerialName("outlet_id") val outletId: String? = null,
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
