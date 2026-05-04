package com.durrr.first.ui.model

import com.durrr.first.domain.model.Pembayaran
import com.durrr.first.domain.model.ReceiptData
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.domain.model.TransaksiDetail
import com.durrr.first.features.transaction.domain.ReceiptDraftStore as FeatureReceiptDraftStore

object ReceiptDraftStore {
    fun putDraft(
        draftId: String,
        transaksi: Transaksi,
        details: List<TransaksiDetail>,
        pembayaran: Pembayaran?,
    ) {
        FeatureReceiptDraftStore.putDraft(
            draftId = draftId,
            transaksi = transaksi,
            details = details,
            pembayaran = pembayaran,
        )
    }

    fun getDraft(id: String): ReceiptData? = FeatureReceiptDraftStore.getDraft(id)
}
