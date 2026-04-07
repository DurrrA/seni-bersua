package com.durrr.first.ui.model

import com.durrr.first.domain.model.Pembayaran
import com.durrr.first.domain.model.ReceiptData
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.domain.model.TransaksiDetail

object ReceiptDraftStore {
    private val drafts = linkedMapOf<String, ReceiptData>()

    fun putDraft(
        draftId: String,
        transaksi: Transaksi,
        details: List<TransaksiDetail>,
        pembayaran: Pembayaran?,
    ) {
        drafts[draftId] = ReceiptData(
            transaksi = transaksi,
            details = details,
            pembayaran = pembayaran,
        )
    }

    fun getDraft(id: String): ReceiptData? = drafts[id]
}
