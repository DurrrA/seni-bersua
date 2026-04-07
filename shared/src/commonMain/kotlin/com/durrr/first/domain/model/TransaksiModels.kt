package com.durrr.first.domain.model

data class Transaksi(
    val id: String,
    val createdAt: String,
    val meja: String?,
    val discountPlus: Long,
    val tax: Long,
    val serviceCharge: Long,
    val rounding: Long,
    val total: Long,
    val outletId: String? = null,
)

data class TransaksiDetail(
    val id: String,
    val transaksiId: String,
    val itemId: String?,
    val itemName: String,
    val qty: Long,
    val price: Long,
    val discount: Long,
    val total: Long,
)

data class Pembayaran(
    val id: String,
    val transaksiId: String,
    val paidAt: String,
    val amountPaid: Long,
    val change: Long,
    val paymentTypeId: String,
    val outletId: String? = null,
)

data class DailyRecap(
    val date: String,
    val transaksiCount: Int,
    val grossTotal: Long,
)
