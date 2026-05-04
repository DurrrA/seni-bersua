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
    @SerialName("cashier_id") val cashierId: String? = null,
    @SerialName("cashier_name") val cashierName: String? = null,
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
    @SerialName("outbox_ids") val outboxIds: List<String> = emptyList(),
    val transaksi: List<TransaksiDto> = emptyList(),
    val events: List<TransactionSyncEventDto> = emptyList(),
    @SerialName("outlet_id") val outletId: String? = null,
)

@Serializable
data class TransactionSyncEventDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("entity_type") val entityType: String,
    val op: String,
    @SerialName("payload_json") val payloadJson: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class TransactionEventAckDto(
    @SerialName("event_id") val eventId: String,
    val status: String,
    val reason: String? = null,
)

@Serializable
data class TransactionBatchResponse(
    val accepted: List<String>,
    val rejected: List<String> = emptyList(),
    val acks: List<TransactionEventAckDto> = emptyList(),
)
