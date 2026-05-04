package com.durrr.first.features.orders.data

import com.durrr.first.data.repo.OrderCacheRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.SettingsRepository

data class OrdersRepositoryBridge(
    val orderCacheRepository: OrderCacheRepository,
    val orderSyncRepository: OrderSyncRepository,
    val settingsRepository: SettingsRepository,
)
