package com.durrr.first.features.product.data

import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.SettingsRepository

data class ProductRepositoryBridge(
    val menuRepository: MenuRepository,
    val menuSyncRepository: MenuSyncRepository,
    val settingsRepository: SettingsRepository,
)
