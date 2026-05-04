package com.durrr.first.features.cart.domain

data class OrderDraftModifierSelection(
    val groupId: String,
    val groupName: String,
    val optionId: String,
    val optionName: String,
    val priceDelta: Long,
)

data class OrderDraftLine(
    val itemId: String?,
    val itemName: String,
    val qty: Long,
    val basePrice: Long,
    val price: Long,
    val modifiers: List<OrderDraftModifierSelection> = emptyList(),
)

data class OrderDraft(
    val id: String,
    val tableToken: String?,
    val lines: List<OrderDraftLine>,
)
