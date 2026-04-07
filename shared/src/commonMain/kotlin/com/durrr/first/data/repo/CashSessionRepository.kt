package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.CashMovement
import com.durrr.first.domain.model.CashMovementType
import com.durrr.first.domain.model.CashSession
import com.durrr.first.domain.model.CashSessionStatus
import com.durrr.first.domain.model.CashSessionSummary
import com.durrr.first.domain.service.IdGenerator

class CashSessionRepository(private val db: TokoDatabase) {
    fun openSession(
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
        openingCash: Long,
        userId: String,
        openedAt: String,
    ): CashSession {
        val active = getActiveSession(outletId)
        if (active != null) {
            error("There is already an active cash session for outlet=$outletId")
        }

        val session = CashSession(
            sessionId = IdGenerator.newId("cs_"),
            outletId = outletId,
            openedBy = userId,
            openedAt = openedAt,
            openingCash = openingCash.coerceAtLeast(0L),
            closedBy = null,
            closedAt = null,
            closingCashCounted = null,
            expectedCash = null,
            variance = null,
            status = CashSessionStatus.OPEN,
        )
        db.tokoQueries.insertCashSession(
            session_id = session.sessionId,
            outlet_id = session.outletId,
            opened_by = session.openedBy,
            opened_at = session.openedAt,
            opening_cash = session.openingCash,
            closed_by = null,
            closed_at = null,
            closing_cash_counted = null,
            expected_cash = null,
            variance = null,
            status = session.status.name,
        )
        return session
    }

    fun addCashIn(
        sessionId: String,
        amount: Long,
        note: String?,
        userId: String,
        createdAt: String,
    ): CashMovement {
        return addMovement(
            sessionId = sessionId,
            amount = amount,
            note = note,
            userId = userId,
            createdAt = createdAt,
            type = CashMovementType.CASH_IN,
        )
    }

    fun addCashOut(
        sessionId: String,
        amount: Long,
        note: String?,
        userId: String,
        createdAt: String,
    ): CashMovement {
        return addMovement(
            sessionId = sessionId,
            amount = amount,
            note = note,
            userId = userId,
            createdAt = createdAt,
            type = CashMovementType.CASH_OUT,
        )
    }

    fun closeSession(
        sessionId: String,
        countedCash: Long,
        userId: String,
        closedAt: String,
    ): CashSession {
        val session = getSessionById(sessionId) ?: error("Session not found")
        if (session.status != CashSessionStatus.OPEN) {
            error("Session already closed")
        }
        val summary = getSessionSummary(sessionId, closedAt)
        val expectedCash = summary.expectedCashNow
        val variance = countedCash - expectedCash
        db.tokoQueries.closeCashSession(
            closed_by = userId,
            closed_at = closedAt,
            closing_cash_counted = countedCash,
            expected_cash = expectedCash,
            variance = variance,
            session_id = sessionId,
        )
        return getSessionById(sessionId) ?: error("Failed to load closed session")
    }

    fun getActiveSession(
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): CashSession? {
        val row = db.tokoQueries.selectActiveCashSessionByOutlet(outletId).executeAsOneOrNull() ?: return null
        return row.toDomain()
    }

    fun getSessionById(sessionId: String): CashSession? {
        return db.tokoQueries.selectCashSessionById(sessionId).executeAsOneOrNull()?.toDomain()
    }

    fun getSessionHistory(
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
        limit: Long = 30,
    ): List<CashSession> {
        return db.tokoQueries.selectCashSessionsByOutlet(outletId, limit.coerceAtLeast(1L))
            .executeAsList()
            .map { it.toDomain() }
    }

    fun getMovementsBySession(sessionId: String): List<CashMovement> {
        return db.tokoQueries.selectCashMovementsBySession(sessionId).executeAsList().map { row ->
            CashMovement(
                movementId = row.movement_id,
                sessionId = row.session_id,
                outletId = row.outlet_id,
                movementType = runCatching { CashMovementType.valueOf(row.movement_type) }
                    .getOrDefault(CashMovementType.CASH_IN),
                amount = row.amount,
                note = row.note,
                createdBy = row.created_by,
                createdAt = row.created_at,
            )
        }
    }

    fun getSessionSummary(sessionId: String, asOf: String? = null): CashSessionSummary {
        val session = getSessionById(sessionId) ?: error("Session not found")
        val endAt = asOf ?: session.closedAt
        val movements = getMovementsBySession(sessionId)
        val cashIn = movements
            .filter { it.movementType == CashMovementType.CASH_IN }
            .sumOf { it.amount }
        val cashOut = movements
            .filter { it.movementType == CashMovementType.CASH_OUT }
            .sumOf { it.amount }
        val cashSales = db.tokoQueries.selectAllPembayaran(session.outletId)
            .executeAsList()
            .filter { payment ->
                isCashPayment(payment.id_jenis_bayar) &&
                    isInRange(payment.c_date, session.openedAt, endAt)
            }
            .sumOf { it.dibayar?.toLongOrNull() ?: 0L }
        val expectedNow = session.openingCash + cashSales + cashIn - cashOut
        return CashSessionSummary(
            session = session,
            cashSales = cashSales,
            cashIn = cashIn,
            cashOut = cashOut,
            expectedCashNow = expectedNow,
        )
    }

    private fun addMovement(
        sessionId: String,
        amount: Long,
        note: String?,
        userId: String,
        createdAt: String,
        type: CashMovementType,
    ): CashMovement {
        val session = getSessionById(sessionId) ?: error("Session not found")
        if (session.status != CashSessionStatus.OPEN) {
            error("Session is closed")
        }
        val safeAmount = amount.coerceAtLeast(0L)
        val movement = CashMovement(
            movementId = IdGenerator.newId("cm_"),
            sessionId = sessionId,
            outletId = session.outletId,
            movementType = type,
            amount = safeAmount,
            note = note,
            createdBy = userId,
            createdAt = createdAt,
        )
        db.tokoQueries.insertCashMovement(
            movement_id = movement.movementId,
            session_id = movement.sessionId,
            outlet_id = movement.outletId,
            movement_type = movement.movementType.name,
            amount = movement.amount,
            note = movement.note,
            created_by = movement.createdBy,
            created_at = movement.createdAt,
        )
        return movement
    }

    private fun isInRange(value: String?, fromInclusive: String, toInclusive: String?): Boolean {
        val current = value?.takeIf { it.isNotBlank() } ?: return false
        if (current < fromInclusive) return false
        return toInclusive == null || current <= toInclusive
    }

    private fun isCashPayment(methodId: String?): Boolean {
        if (methodId.isNullOrBlank()) return false
        return methodId.contains("cash", ignoreCase = true) || methodId == "CASH"
    }

    private fun com.durrr.first.Cash_session.toDomain(): CashSession {
        return CashSession(
            sessionId = session_id,
            outletId = outlet_id,
            openedBy = opened_by,
            openedAt = opened_at,
            openingCash = opening_cash,
            closedBy = closed_by,
            closedAt = closed_at,
            closingCashCounted = closing_cash_counted,
            expectedCash = expected_cash,
            variance = variance,
            status = runCatching { CashSessionStatus.valueOf(status) }.getOrDefault(CashSessionStatus.OPEN),
        )
    }
}
