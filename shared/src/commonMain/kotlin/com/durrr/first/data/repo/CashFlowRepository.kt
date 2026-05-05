package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.CashFlowEntry
import com.durrr.first.domain.model.CashFlowMethodTotal
import com.durrr.first.domain.model.CashFlowSummary
import com.durrr.first.domain.model.RecapRange

class CashFlowRepository(private val db: TokoDatabase) {
    fun getSummary(
        range: RecapRange,
        anchorDate: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): CashFlowSummary {
        val normalizedAnchorDate = extractDate(anchorDate)
        val anchorEpochDay = toEpochDay(normalizedAnchorDate)
        val jenisBayarMap = db.tokoQueries.selectAllJenisBayar().executeAsList()
            .associate { it.id_jenis_bayar to it.nama }

        val transaksiById = db.tokoQueries.selectAllTransaksi(outletId).executeAsList()
            .associateBy { it.id_transaksi }

        val pembayaran = db.tokoQueries.selectAllPembayaran(outletId).executeAsList()
            .filter { row ->
                isInRange(
                    date = extractDate(row.c_date),
                    range = range,
                    anchorDate = normalizedAnchorDate,
                    anchorEpochDay = anchorEpochDay,
                )
            }

        val byMethod = pembayaran
            .groupBy { it.id_jenis_bayar }
            .map { (methodId, rows) ->
                CashFlowMethodTotal(
                    methodId = methodId,
                    methodName = jenisBayarMap[methodId] ?: (methodId ?: "Unknown"),
                    total = rows.sumOf { parseLong(it.dibayar) },
                )
            }
            .sortedByDescending { it.total }

        val totalCashIn = byMethod.sumOf { it.total }

        val paymentFacts = pembayaran.map { row ->
            val transaksi = row.id_transaksi?.let { transaksiById[it] }
            val transaksiCancelled = transaksi?.is_cancel == "1"
            val methodId = row.id_jenis_bayar
            val methodName = jenisBayarMap[methodId] ?: (methodId ?: "Unknown")
            PaymentFact(
                row = row,
                amount = parseLong(row.dibayar),
                methodId = methodId,
                methodName = methodName,
                isCancelled = transaksiCancelled,
                isCashMethod = isCashMethod(methodId, methodName),
            )
        }

        val totalRefundOrCancelled = paymentFacts
            .filter { it.isCancelled }
            .sumOf { it.amount }

        val cashSalesNet = paymentFacts
            .filter { it.isCashMethod }
            .sumOf { if (it.isCancelled) -it.amount else it.amount }

        val cashMovements = db.tokoQueries.selectCashMovementsByOutlet(outletId, 5000L)
            .executeAsList()
            .filter { row ->
                isInRange(
                    date = extractDate(row.created_at),
                    range = range,
                    anchorDate = normalizedAnchorDate,
                    anchorEpochDay = anchorEpochDay,
                )
            }

        val manualCashIn = cashMovements
            .filter { it.movement_type.equals("CASH_IN", ignoreCase = true) }
            .sumOf { it.amount }
        val manualCashOut = cashMovements
            .filter { it.movement_type.equals("CASH_OUT", ignoreCase = true) }
            .sumOf { it.amount }

        val openingCashTotal = db.tokoQueries.selectCashSessionsByOutlet(outletId, 5000L)
            .executeAsList()
            .filter { row ->
                isInRange(
                    date = extractDate(row.opened_at),
                    range = range,
                    anchorDate = normalizedAnchorDate,
                    anchorEpochDay = anchorEpochDay,
                )
            }
            .sumOf { it.opening_cash }

        val estimatedCashPosition = openingCashTotal + cashSalesNet + manualCashIn - manualCashOut

        val recentEntries = paymentFacts.take(30).map { payment ->
            CashFlowEntry(
                paymentId = payment.row.id_pembayaran,
                transaksiId = payment.row.id_transaksi,
                methodId = payment.methodId,
                methodName = payment.methodName,
                amount = payment.amount,
                dateTime = payment.row.c_date,
            )
        }

        return CashFlowSummary(
            totalCashIn = totalCashIn,
            totalRefundOrCancelled = totalRefundOrCancelled,
            openingCashTotal = openingCashTotal,
            cashSalesNet = cashSalesNet,
            manualCashIn = manualCashIn,
            manualCashOut = manualCashOut,
            estimatedCashPosition = estimatedCashPosition,
            byMethod = byMethod,
            recentEntries = recentEntries,
        )
    }

    private fun isInRange(
        date: String,
        range: RecapRange,
        anchorDate: String,
        anchorEpochDay: Long?,
    ): Boolean {
        if (date.isBlank()) return false
        return when (range) {
            RecapRange.TODAY -> date == anchorDate
            RecapRange.MONTH -> date.take(7) == anchorDate.take(7)
            RecapRange.WEEK -> {
                val currentEpochDay = toEpochDay(date) ?: return false
                val baseEpochDay = anchorEpochDay ?: return false
                currentEpochDay in (baseEpochDay - 6)..baseEpochDay
            }
            RecapRange.ALL -> true
        }
    }

    private fun extractDate(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return if (value.length >= 10) value.substring(0, 10) else value
    }

    private fun toEpochDay(isoDate: String): Long? {
        if (isoDate.length < 10) return null
        val year = isoDate.substring(0, 4).toIntOrNull() ?: return null
        val month = isoDate.substring(5, 7).toIntOrNull() ?: return null
        val day = isoDate.substring(8, 10).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null

        val leap = isLeapYear(year)
        val monthLength = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (leap) 29 else 28
            else -> return null
        }
        if (day > monthLength) return null

        val yearLong = year.toLong()
        val monthLong = month.toLong()
        var total = 365L * yearLong +
            floorDiv(yearLong + 3, 4) -
            floorDiv(yearLong + 99, 100) +
            floorDiv(yearLong + 399, 400)
        total += floorDiv(367 * monthLong - 362, 12)
        total += (day - 1).toLong()
        if (month > 2) {
            total -= if (leap) 1 else 2
        }
        return total - DAYS_0000_TO_1970
    }

    private fun floorDiv(x: Long, y: Long): Long {
        var result = x / y
        if ((x xor y) < 0 && result * y != x) {
            result -= 1
        }
        return result
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    private fun isCashMethod(methodId: String?, methodName: String): Boolean {
        val id = methodId.orEmpty()
        return id.contains("cash", ignoreCase = true) ||
            methodName.contains("cash", ignoreCase = true) ||
            methodName.contains("tunai", ignoreCase = true)
    }

    private fun parseLong(value: String?): Long = value?.toLongOrNull() ?: 0L

    private data class PaymentFact(
        val row: com.durrr.first.Toko_pembayaran,
        val amount: Long,
        val methodId: String?,
        val methodName: String,
        val isCancelled: Boolean,
        val isCashMethod: Boolean,
    )

    private companion object {
        const val DAYS_0000_TO_1970 = 719528L
    }
}
