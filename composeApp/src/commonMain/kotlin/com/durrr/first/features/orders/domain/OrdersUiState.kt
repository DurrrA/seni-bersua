package com.durrr.first.features.orders.domain

import com.durrr.first.domain.model.OrderWithItems

data class OrdersUiState(
    val orders: List<OrderWithItems> = emptyList(),
    val searchQuery: String = "",
    val selectedOrderId: String? = null,
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
)
