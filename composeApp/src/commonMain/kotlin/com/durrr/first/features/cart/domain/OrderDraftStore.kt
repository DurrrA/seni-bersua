package com.durrr.first.features.cart.domain

object OrderDraftStore {
    private val drafts = linkedMapOf<String, OrderDraft>()

    fun putDraft(draft: OrderDraft) {
        drafts[draft.id] = draft
    }

    fun getDraft(id: String): OrderDraft? = drafts[id]

    fun removeDraft(id: String) {
        drafts.remove(id)
    }
}
