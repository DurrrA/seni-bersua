package com.durrr.first.ui.model

import com.durrr.first.features.cart.domain.OrderDraft as FeatureOrderDraft
import com.durrr.first.features.cart.domain.OrderDraftLine as FeatureOrderDraftLine
import com.durrr.first.features.cart.domain.OrderDraftStore as FeatureOrderDraftStore

typealias OrderDraft = FeatureOrderDraft
typealias OrderDraftLine = FeatureOrderDraftLine

object OrderDraftStore {
    fun putDraft(draft: OrderDraft) {
        FeatureOrderDraftStore.putDraft(draft)
    }

    fun getDraft(id: String): OrderDraft? = FeatureOrderDraftStore.getDraft(id)

    fun removeDraft(id: String) {
        FeatureOrderDraftStore.removeDraft(id)
    }
}
