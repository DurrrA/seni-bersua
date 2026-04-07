package com.durrr.first.domain.model

enum class StockMovementType {
    SALE,
    ADJUST_IN,
    ADJUST_OUT,
    RESTOCK,
    VOID_ROLLBACK,
}

data class StockBalance(
    val itemId: String,
    val outletId: String,
    val qtyOnHand: Long,
    val updatedAt: String,
)

data class StockThreshold(
    val itemId: String,
    val outletId: String,
    val minQty: Long,
)

data class LowStockItem(
    val itemId: String,
    val itemName: String,
    val outletId: String,
    val qtyOnHand: Long,
    val minQty: Long,
)

data class StockLedgerEntry(
    val ledgerId: String,
    val outletId: String,
    val itemId: String,
    val movementType: StockMovementType,
    val qtyDelta: Long,
    val referenceType: String?,
    val referenceId: String?,
    val reason: String?,
    val createdBy: String?,
    val createdAt: String,
)
