package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.Pembayaran
import com.durrr.first.domain.model.ReceiptData
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.domain.model.TransaksiDetail

class ReceiptRepository(private val db: TokoDatabase) {
    fun getReceiptData(
        transaksiId: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): ReceiptData? {
        val transaksiRow = db.tokoQueries.selectTransaksiById(transaksiId, outletId).executeAsOneOrNull() ?: return null
        val detailsRows = db.tokoQueries.selectTransaksiDetailByTransaksi(transaksiId).executeAsList()
        val pembayaranRow = db.tokoQueries.selectPembayaranByTransaksi(transaksiId, outletId).executeAsOneOrNull()

        val transaksi = Transaksi(
            id = transaksiRow.id_transaksi,
            createdAt = transaksiRow.c_date ?: "",
            meja = transaksiRow.meja,
            discountPlus = parseLong(transaksiRow.diskon_plus),
            tax = parseLong(transaksiRow.pajak),
            serviceCharge = parseLong(transaksiRow.service_charge),
            rounding = parseLong(transaksiRow.round_harga),
            total = parseLong(transaksiRow.rekap_harga_total),
            outletId = transaksiRow.outlet_id,
        )
        val details = detailsRows.map { row ->
            TransaksiDetail(
                id = row.id_transaksi_detail,
                transaksiId = row.id_transaksi,
                itemId = row.id_item,
                itemName = row.nama_item ?: "",
                qty = parseLong(row.jumlah),
                price = parseLong(row.harga),
                discount = parseLong(row.diskon),
                total = parseLong(row.rekap_harga_detail),
            )
        }
        val pembayaran = pembayaranRow?.let { row ->
            Pembayaran(
                id = row.id_pembayaran,
                transaksiId = row.id_transaksi ?: transaksiId,
                paidAt = row.c_date ?: "",
                amountPaid = parseLong(row.dibayar),
                change = parseLong(row.kembalian),
                paymentTypeId = row.id_jenis_bayar ?: "CASH",
                outletId = row.outlet_id,
            )
        }

        return ReceiptData(
            transaksi = transaksi,
            details = details,
            pembayaran = pembayaran,
        )
    }

    private fun parseLong(value: String?): Long = value?.toLongOrNull() ?: 0L
}
