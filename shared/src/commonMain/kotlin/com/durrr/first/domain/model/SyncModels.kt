package com.durrr.first.domain.model

data class OutboxEvent(
    val id: String,
    val type: String,
    val payloadJson: String,
    val createdAt: String,
    val sentAt: String?,
    val outletId: String? = null,
)
