package com.durrr.first.domain.model

enum class CashSessionStatus {
    OPEN,
    CLOSED,
}

enum class CashMovementType {
    CASH_IN,
    CASH_OUT,
}

data class CashSession(
    val sessionId: String,
    val outletId: String,
    val openedBy: String,
    val openedAt: String,
    val openingCash: Long,
    val closedBy: String?,
    val closedAt: String?,
    val closingCashCounted: Long?,
    val expectedCash: Long?,
    val variance: Long?,
    val status: CashSessionStatus,
)

data class CashMovement(
    val movementId: String,
    val sessionId: String,
    val outletId: String,
    val movementType: CashMovementType,
    val amount: Long,
    val note: String?,
    val createdBy: String?,
    val createdAt: String,
)

data class CashSessionSummary(
    val session: CashSession,
    val cashSales: Long,
    val cashIn: Long,
    val cashOut: Long,
    val expectedCashNow: Long,
)
