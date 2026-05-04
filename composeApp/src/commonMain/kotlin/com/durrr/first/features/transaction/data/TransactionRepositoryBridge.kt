package com.durrr.first.features.transaction.data

import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiRepository
import com.durrr.first.data.repo.TransaksiSyncRepository

data class TransactionRepositoryBridge(
    val transaksiRepository: TransaksiRepository,
    val transaksiSyncRepository: TransaksiSyncRepository,
    val settingsRepository: SettingsRepository,
)
