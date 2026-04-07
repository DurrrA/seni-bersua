package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.LowStockItem
import com.durrr.first.domain.model.StockBalance
import com.durrr.first.domain.model.StockLedgerEntry
import com.durrr.first.domain.model.StockMovementType
import com.durrr.first.domain.model.StockThreshold
import com.durrr.first.domain.model.TransaksiDetail
import com.durrr.first.domain.service.IdGenerator

class StockRepository(private val db: TokoDatabase) {
    fun getBalance(
        itemId: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): StockBalance? {
        val row = db.tokoQueries.selectStockBalanceByItem(itemId, outletId).executeAsOneOrNull() ?: return null
        return StockBalance(
            itemId = row.item_id,
            outletId = row.outlet_id,
            qtyOnHand = row.qty_on_hand,
            updatedAt = row.updated_at,
        )
    }

    fun setThreshold(
        itemId: String,
        minQty: Long,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): StockThreshold {
        val safeMinQty = minQty.coerceAtLeast(0L)
        db.tokoQueries.upsertStockThreshold(
            item_id = itemId,
            outlet_id = outletId,
            min_qty = safeMinQty,
        )
        return StockThreshold(
            itemId = itemId,
            outletId = outletId,
            minQty = safeMinQty,
        )
    }

    fun adjustStock(
        itemId: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
        qtyDelta: Long,
        reason: String? = null,
        user: String? = null,
        createdAt: String = "",
        allowNegativeStock: Boolean = true,
    ): StockBalance {
        val movementType = when {
            qtyDelta > 0L -> StockMovementType.ADJUST_IN
            qtyDelta < 0L -> StockMovementType.ADJUST_OUT
            else -> StockMovementType.ADJUST_IN
        }
        return applyMovement(
            itemId = itemId,
            outletId = outletId,
            qtyDelta = qtyDelta,
            movementType = movementType,
            referenceType = "MANUAL",
            referenceId = null,
            reason = reason,
            createdBy = user,
            createdAt = createdAt,
            allowNegativeStock = allowNegativeStock,
        )
    }

    fun decrementBySale(
        transaksiId: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
        lines: List<TransaksiDetail>,
        createdAt: String = "",
        createdBy: String? = null,
        allowNegativeStock: Boolean = true,
    ) {
        lines.forEach { line ->
            val itemId = line.itemId ?: return@forEach
            if (line.qty <= 0L) return@forEach
            applyMovement(
                itemId = itemId,
                outletId = outletId,
                qtyDelta = -line.qty,
                movementType = StockMovementType.SALE,
                referenceType = "TRANSAKSI",
                referenceId = transaksiId,
                reason = "Checkout",
                createdBy = createdBy,
                createdAt = createdAt,
                allowNegativeStock = allowNegativeStock,
            )
        }
    }

    fun getLowStockItems(
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): List<LowStockItem> {
        return db.tokoQueries.selectLowStockItems(outletId).executeAsList().map { row ->
            LowStockItem(
                itemId = row.item_id,
                itemName = row.item_name ?: row.item_id,
                outletId = row.outlet_id,
                qtyOnHand = row.qty_on_hand,
                minQty = row.min_qty,
            )
        }
    }

    fun getStockHistory(
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
        itemId: String? = null,
        limit: Long = 100,
    ): List<StockLedgerEntry> {
        val cappedLimit = limit.coerceAtLeast(1L).coerceAtMost(1000L)
        val rows = if (itemId.isNullOrBlank()) {
            db.tokoQueries.selectStockHistoryByOutlet(outletId, cappedLimit).executeAsList()
        } else {
            db.tokoQueries.selectStockHistoryByItem(outletId, itemId, cappedLimit).executeAsList()
        }
        return rows.map { row ->
            StockLedgerEntry(
                ledgerId = row.ledger_id,
                outletId = row.outlet_id,
                itemId = row.item_id,
                movementType = runCatching { StockMovementType.valueOf(row.movement_type) }
                    .getOrDefault(StockMovementType.ADJUST_IN),
                qtyDelta = row.qty_delta,
                referenceType = row.reference_type,
                referenceId = row.reference_id,
                reason = row.reason,
                createdBy = row.created_by,
                createdAt = row.created_at,
            )
        }
    }

    private fun applyMovement(
        itemId: String,
        outletId: String,
        qtyDelta: Long,
        movementType: StockMovementType,
        referenceType: String?,
        referenceId: String?,
        reason: String?,
        createdBy: String?,
        createdAt: String,
        allowNegativeStock: Boolean,
    ): StockBalance {
        val current = getBalance(itemId, outletId)
        val nextQty = (current?.qtyOnHand ?: 0L) + qtyDelta
        if (!allowNegativeStock && nextQty < 0L) {
            error("Insufficient stock for itemId=$itemId in outlet=$outletId")
        }

        db.tokoQueries.upsertStockBalance(
            item_id = itemId,
            outlet_id = outletId,
            qty_on_hand = nextQty,
            updated_at = createdAt,
        )
        db.tokoQueries.insertStockLedger(
            ledger_id = IdGenerator.newId("stk_"),
            outlet_id = outletId,
            item_id = itemId,
            movement_type = movementType.name,
            qty_delta = qtyDelta,
            reference_type = referenceType,
            reference_id = referenceId,
            reason = reason,
            created_by = createdBy,
            created_at = createdAt,
        )
        return StockBalance(
            itemId = itemId,
            outletId = outletId,
            qtyOnHand = nextQty,
            updatedAt = createdAt,
        )
    }
}
