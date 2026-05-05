package com.durrr.first.domain.model

data class GroupItem(
    val id: String,
    val name: String,
    val order: Int,
    val outletId: String? = null,
)

data class Item(
    val id: String,
    val name: String,
    val price: Long,
    val groupId: String?,
    val code: String?,
    val imageUrl: String? = null,
    val isActive: Boolean,
    val outletId: String? = null,
)

data class ModifierGroup(
    val id: String,
    val name: String,
    val selectionType: String,
    val isRequired: Boolean,
    val maxSelection: Int,
    val outletId: String? = null,
)

data class ModifierOption(
    val id: String,
    val groupId: String,
    val name: String,
    val priceDelta: Long,
    val order: Int,
    val isDefault: Boolean,
    val outletId: String? = null,
)

data class ModifierGroupBundle(
    val group: ModifierGroup,
    val options: List<ModifierOption>,
)

data class Unit(
    val id: String,
    val name: String?,
    val shortName: String?,
)

data class UnitVarian(
    val id: String,
    val itemId: String?,
    val unitId: String?,
    val price: Long,
    val quantity: Long,
)

data class Material(
    val id: String,
    val unitId: String?,
    val name: String?,
)
