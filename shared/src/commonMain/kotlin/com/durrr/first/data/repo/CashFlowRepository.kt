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
        val jenisBayarMap = db.tokoQueries.selectAllJenisBayar().executeAsList()
            .associate { it.id_jenis_bayar to it.nama }

        val transaksiById = db.tokoQueries.selectAllTransaksi(outletId).executeAsList()
            .associateBy { it.id_transaksi }

        val rawPembayaran = db.tokoQueries.selectAllPembayaran(outletId).executeAsList()
        val weekDates = rawPembayaran
            .map { extractDate(it.c_date) }
            .filter { it.isNotBlank() && it <= anchorDate }
            .distinct()
            .sorted()
            .takeLast(7)
            .toSet()
        val pembayaran = rawPembayaran
            .filter { row -> isInRange(extractDate(row.c_date), range, anchorDate, weekDates) }

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

        val totalRefundOrCancelled = pembayaran.sumOf { row ->
            val transaksi = row.id_transaksi?.let { transaksiById[it] }
            val transaksiCancelled = transaksi?.is_cancel == "1"
            if (transaksiCancelled) parseLong(row.dibayar) else 0L
        }

        val recentEntries = pembayaran.take(30).map { row ->
            CashFlowEntry(
                paymentId = row.id_pembayaran,
                transaksiId = row.id_transaksi,
                methodId = row.id_jenis_bayar,
                methodName = jenisBayarMap[row.id_jenis_bayar] ?: (row.id_jenis_bayar ?: "Unknown"),
                amount = parseLong(row.dibayar),
                dateTime = row.c_date,
            )
        }

        return CashFlowSummary(
            totalCashIn = totalCashIn,
            totalRefundOrCancelled = totalRefundOrCancelled,
            byMethod = byMethod,
            recentEntries = recentEntries,
        )
    }

    private fun isInRange(date: String, range: RecapRange, anchorDate: String, weekDates: Set<String>): Boolean {
        if (date.isBlank()) return false
        return when (range) {
            RecapRange.TODAY -> date == anchorDate
            RecapRange.MONTH -> date.take(7) == anchorDate.take(7)
            RecapRange.WEEK -> date in weekDates
        }
    }

    private fun extractDate(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return if (value.length >= 10) value.substring(0, 10) else value
    }

    private fun parseLong(value: String?): Long = value?.toLongOrNull() ?: 0L
}
