package com.durrr.first.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelopeDto<T>(
    val data: T? = null,
    val message: String = "OK",
    val error: String? = null,
)

