package com.durrr.first.features.cashflow.domain

import com.durrr.first.domain.model.CashFlowSummary
import com.durrr.first.domain.model.RecapRange

data class CashFlowUiState(
    val selectedRange: RecapRange = RecapRange.TODAY,
    val summary: CashFlowSummary? = null,
)
