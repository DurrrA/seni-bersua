package com.durrr.first.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailyRecapResponse(
    val date: String,
    @SerialName("transaksi_count") val transaksiCount: Int,
    @SerialName("gross_total") val grossTotal: Long,
)

@Serializable
enum class RecapRangeDto {
    TODAY,
    WEEK,
    MONTH,
}

@Serializable
data class PaymentBreakdownDto(
    @SerialName("method_id") val methodId: String,
    @SerialName("method_name") val methodName: String,
    @SerialName("transaction_count") val transactionCount: Int,
    val total: Long,
)

@Serializable
data class ProductMovementDto(
    @SerialName("item_id") val itemId: String?,
    @SerialName("item_name") val itemName: String,
    @SerialName("qty_sold") val qtySold: Long,
    val revenue: Long,
)

@Serializable
data class RecapSummaryResponse(
    @SerialName("anchor_date") val anchorDate: String,
    val range: RecapRangeDto,
    @SerialName("transaksi_count") val transaksiCount: Int,
    @SerialName("gross_total") val grossTotal: Long,
    @SerialName("total_discount") val totalDiscount: Long,
    @SerialName("average_ticket") val averageTicket: Long,
    @SerialName("payment_breakdown") val paymentBreakdown: List<PaymentBreakdownDto>,
    @SerialName("top_items") val topItems: List<ProductMovementDto>,
    @SerialName("slow_items") val slowItems: List<ProductMovementDto>,
)
