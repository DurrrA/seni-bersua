package com.durrr.first.features.cashflow.data

import com.durrr.first.data.repo.CashFlowRepository
import com.durrr.first.data.repo.SettingsRepository

data class CashFlowRepositoryBridge(
    val cashFlowRepository: CashFlowRepository,
    val settingsRepository: SettingsRepository,
)
