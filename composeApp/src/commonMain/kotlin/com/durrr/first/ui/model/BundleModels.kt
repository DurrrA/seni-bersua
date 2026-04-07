package com.durrr.first.ui.model

data class BundleItemInput(
    val itemId: String,
    val itemName: String,
    val qty: Int,
)

data class BundleDraft(
    val id: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val price: Long,
    val items: List<BundleItemInput>,
)
