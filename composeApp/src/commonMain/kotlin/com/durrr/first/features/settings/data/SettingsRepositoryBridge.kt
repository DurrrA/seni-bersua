package com.durrr.first.features.settings.data

import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiSyncRepository

data class SettingsRepositoryBridge(
    val settingsRepository: SettingsRepository,
    val menuSyncRepository: MenuSyncRepository? = null,
    val orderSyncRepository: OrderSyncRepository? = null,
    val transaksiSyncRepository: TransaksiSyncRepository? = null,
)
