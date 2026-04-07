package com.durrr.first.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransaksiDetailDto(
    val id: String,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("item_name") val itemName: String,
    val qty: Long,
    val price: Long,
    val discount: Long = 0,
    val total: Long,
)

@Serializable
data class PembayaranDto(
    val id: String,
    @SerialName("paid_at") val paidAt: String,
    @SerialName("amount_paid") val amountPaid: Long,
    val change: Long,
    @SerialName("payment_type_id") val paymentTypeId: String,
)

@Serializable
data class TransaksiDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val meja: String? = null,
    @SerialName("discount_plus") val discountPlus: Long = 0,
    val tax: Long = 0,
    @SerialName("service_charge") val serviceCharge: Long = 0,
    val rounding: Long = 0,
    val total: Long,
    val details: List<TransaksiDetailDto>,
    val pembayaran: PembayaranDto,
)

@Serializable
data class TransactionBatchRequest(
    @SerialName("outbox_ids") val outboxIds: List<String>,
    val transaksi: List<TransaksiDto>,
    @SerialName("outlet_id") val outletId: String? = null,
)

@Serializable
data class TransactionBatchResponse(
    val accepted: List<String>,
    val rejected: List<String> = emptyList(),
)
