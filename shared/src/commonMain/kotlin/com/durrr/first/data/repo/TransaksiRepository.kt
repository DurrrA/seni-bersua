package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.Pembayaran
import com.durrr.first.domain.model.Transaksi
import com.durrr.first.domain.model.TransaksiDetail
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.domain.service.TotalsCalculator

class TransaksiRepository(
    private val db: TokoDatabase,
    private val totalsCalculator: TotalsCalculator = TotalsCalculator(),
    private val stockRepository: StockRepository = StockRepository(db),
) {
    fun createDraftTransaksi(
        mejaTokenOrLabel: String?,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): Transaksi {
        val transaksi = Transaksi(
            id = IdGenerator.newId("trx_"),
            createdAt = "",
            meja = mejaTokenOrLabel,
            discountPlus = 0L,
            tax = 0L,
            serviceCharge = 0L,
            rounding = 0L,
            total = 0L,
            outletId = outletId,
        )
        createTransaksi(transaksi)
        return transaksi
    }

    fun addLine(
        transaksiId: String,
        itemId: String?,
        qty: Long,
        price: Long,
        notes: String? = null,
    ): TransaksiDetail {
        val detail = TransaksiDetail(
            id = IdGenerator.newId("trd_"),
            transaksiId = transaksiId,
            itemId = itemId,
            itemName = notes ?: "",
            qty = qty,
            price = price,
            discount = 0L,
            total = qty * price,
        )
        addDetail(detail)
        return detail
    }

    fun checkoutCash(
        transaksiId: String,
        paidAmount: Long,
        paymentTypeId: String,
        details: List<TransaksiDetail>,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
        discountPlus: Long = 0,
        tax: Long = 0,
        serviceCharge: Long = 0,
        rounding: Long = 0,
    ): TotalsCalculator.Result {
        val pembayaran = Pembayaran(
            id = IdGenerator.newId("pay_"),
            transaksiId = transaksiId,
            paidAt = "",
            amountPaid = paidAmount,
            change = 0L,
            paymentTypeId = paymentTypeId,
            outletId = outletId,
        )
        return checkoutCash(
            transaksiId = transaksiId,
            details = details,
            pembayaran = pembayaran,
            outletId = outletId,
            discountPlus = discountPlus,
            tax = tax,
            serviceCharge = serviceCharge,
            rounding = rounding,
        )
    }

    fun checkoutCash(
        transaksiId: String,
        paidAmount: Long,
        paymentTypeId: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): TotalsCalculator.Result {
        val details = db.tokoQueries.selectTransaksiDetailByTransaksi(transaksiId).executeAsList().map { row ->
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
        return checkoutCash(
            transaksiId = transaksiId,
            paidAmount = paidAmount,
            paymentTypeId = paymentTypeId,
            details = details,
            outletId = outletId,
        )
    }

    fun createTransaksi(transaksi: Transaksi) {
        val outletId = transaksi.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID
        db.tokoQueries.insertTransaksi(
            id_transaksi = transaksi.id,
            c_date = transaksi.createdAt,
            meja = transaksi.meja,
            diskon_plus = transaksi.discountPlus.toString(),
            pajak = transaksi.tax.toString(),
            service_charge = transaksi.serviceCharge.toString(),
            round_harga = transaksi.rounding.toString(),
            rekap_harga_total = transaksi.total.toString(),
            dibayar = "0",
            outlet_id = outletId,
        )
    }

    fun addDetail(detail: TransaksiDetail) {
        db.tokoQueries.insertTransaksiDetail(
            id_transaksi_detail = detail.id,
            id_transaksi = detail.transaksiId,
            id_item = detail.itemId,
            nama_item = detail.itemName,
            jumlah = detail.qty.toString(),
            harga = detail.price.toString(),
            diskon = detail.discount.toString(),
            rekap_harga_detail = detail.total.toString(),
        )
    }

    fun checkoutCash(
        transaksiId: String,
        details: List<TransaksiDetail>,
        pembayaran: Pembayaran,
        outletId: String = pembayaran.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
        discountPlus: Long = 0,
        tax: Long = 0,
        serviceCharge: Long = 0,
        rounding: Long = 0,
        allowNegativeStock: Boolean = true,
    ): TotalsCalculator.Result {
        val totals = totalsCalculator.calculate(
            lines = details.map { TotalsCalculator.Line(it.qty, it.price, it.discount) },
            discountPlus = discountPlus,
            tax = tax,
            serviceCharge = serviceCharge,
            rounding = rounding,
            paid = pembayaran.amountPaid,
        )

        db.transaction {
            db.tokoQueries.updateTransaksiTotals(
                rekap_harga_total = totals.grandTotal.toString(),
                diskon_plus = discountPlus.toString(),
                pajak = tax.toString(),
                service_charge = serviceCharge.toString(),
                round_harga = rounding.toString(),
                dibayar = pembayaran.amountPaid.toString(),
                id_transaksi = transaksiId,
            )

            db.tokoQueries.insertPembayaran(
                id_pembayaran = pembayaran.id,
                id_transaksi = transaksiId,
                dibayar = pembayaran.amountPaid.toString(),
                kembalian = totals.change.toString(),
                id_jenis_bayar = pembayaran.paymentTypeId,
                outlet_id = outletId,
                c_date = pembayaran.paidAt,
            )

            stockRepository.decrementBySale(
                transaksiId = transaksiId,
                outletId = outletId,
                lines = details,
                createdAt = pembayaran.paidAt,
                createdBy = "cashier",
                allowNegativeStock = allowNegativeStock,
            )
        }

        return totals
    }

    private fun parseLong(value: String?): Long = value?.toLongOrNull() ?: 0L
}
