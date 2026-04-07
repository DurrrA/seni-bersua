package com.durrr.first.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpsertMenuItemRequest(
    val id: String? = null,
    val name: String,
    val price: Long,
    @SerialName("outlet_id") val outletId: String? = null,
)
