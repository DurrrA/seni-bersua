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
    val isActive: Boolean,
    val outletId: String? = null,
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
