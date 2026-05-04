package com.durrr.first.features.recap.data

import com.durrr.first.data.repo.RecapRepository
import com.durrr.first.data.repo.RecapSyncRepository
import com.durrr.first.data.repo.SettingsRepository

data class RecapRepositoryBridge(
    val recapRepository: RecapRepository,
    val recapSyncRepository: RecapSyncRepository?,
    val settingsRepository: SettingsRepository,
)
