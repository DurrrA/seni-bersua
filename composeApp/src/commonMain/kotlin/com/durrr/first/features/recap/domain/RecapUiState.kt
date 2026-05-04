package com.durrr.first.features.recap.domain

import com.durrr.first.domain.model.RecapDashboard
import com.durrr.first.domain.model.RecapRange

data class RecapUiState(
    val selectedRange: RecapRange = RecapRange.TODAY,
    val dashboard: RecapDashboard? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val dataSource: String = "Local",
)
