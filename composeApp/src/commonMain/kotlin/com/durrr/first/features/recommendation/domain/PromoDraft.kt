package com.durrr.first.features.recommendation.domain

data class PromoDraft(
    val id: String,
    val name: String,
    val itemId: String,
    val itemName: String,
    val discountPercent: Int,
    val promoPrice: Long,
    val startDate: String,
    val endDate: String,
)
