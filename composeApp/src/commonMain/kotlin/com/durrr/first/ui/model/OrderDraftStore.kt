package com.durrr.first.ui.model

data class OrderDraftLine(
    val itemId: String?,
    val itemName: String,
    val qty: Long,
    val price: Long,
)

data class OrderDraft(
    val id: String,
    val tableToken: String?,
    val lines: List<OrderDraftLine>,
)

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
