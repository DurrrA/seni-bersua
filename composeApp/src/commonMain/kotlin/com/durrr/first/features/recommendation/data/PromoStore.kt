package com.durrr.first.features.recommendation.data

import androidx.compose.runtime.mutableStateListOf
import com.durrr.first.features.recommendation.domain.PromoDraft

object PromoStore {
    val promos = mutableStateListOf<PromoDraft>()
}
