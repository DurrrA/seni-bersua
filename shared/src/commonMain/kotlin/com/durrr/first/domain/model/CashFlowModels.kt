package com.durrr.first.domain.model

data class CashFlowMethodTotal(
    val methodId: String?,
    val methodName: String,
    val total: Long,
)

data class CashFlowEntry(
    val paymentId: String,
    val transaksiId: String?,
    val methodId: String?,
    val methodName: String,
    val amount: Long,
    val dateTime: String?,
)

data class CashFlowSummary(
    val totalCashIn: Long,
    val totalRefundOrCancelled: Long,
    val byMethod: List<CashFlowMethodTotal>,
    val recentEntries: List<CashFlowEntry>,
)
