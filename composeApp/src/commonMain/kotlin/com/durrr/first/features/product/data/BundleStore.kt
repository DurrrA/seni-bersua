package com.durrr.first.features.product.data

import androidx.compose.runtime.mutableStateListOf
import com.durrr.first.features.product.domain.BundleDraft

object BundleStore {
    val bundles = mutableStateListOf<BundleDraft>()
}
