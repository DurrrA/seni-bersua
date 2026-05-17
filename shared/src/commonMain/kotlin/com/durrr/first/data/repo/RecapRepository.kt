package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.DailyRecap
import com.durrr.first.domain.model.PaymentMethodRecap
import com.durrr.first.domain.model.ProductRecap
import com.durrr.first.domain.model.RecapChartPoint
import com.durrr.first.domain.model.RecapDashboard
import com.durrr.first.domain.model.RecapMetrics
import com.durrr.first.domain.model.RecapProductRecommendationPolicy
import com.durrr.first.domain.model.RecapRange

class RecapRepository(private val db: TokoDatabase) {
    fun getDailyRecap(
        date: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): DailyRecap {
        migrateCompletedOrdersToTransaksi(outletId)
        val transaksi = db.tokoQueries.selectTransaksiByDate(date, outletId).executeAsList()
        val grossTotal = transaksi.sumOf { it.rekap_harga_total?.toLongOrNull() ?: 0L }
        return DailyRecap(
            date = date,
            transaksiCount = transaksi.size,
            grossTotal = grossTotal,
        )
    }

    fun getRecap(
        range: RecapRange,
        anchorDate: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): RecapDashboard {
        migrateCompletedOrdersToTransaksi(outletId)
        val transaksi = filteredTransaksi(range, anchorDate, outletId)
        val transaksiIds = transaksi.map { it.id_transaksi }.toSet()
        val details = filteredDetailsForTransaksiIds(transaksiIds)
        val payments = filteredPaymentsForTransaksiIds(transaksiIds, outletId)
        val methodNames = db.tokoQueries.selectAllJenisBayar().executeAsList()
            .associate { it.id_jenis_bayar to it.nama }

        val totalSales = transaksi.sumOf { parseLong(it.rekap_harga_total) }
        val totalTransactions = transaksi.size
        val totalDiscounts = transaksi.sumOf { parseLong(it.diskon_plus) } + details.sumOf { parseLong(it.diskon) }
        val metrics = RecapMetrics(
            totalSales = totalSales,
            totalTransactions = totalTransactions,
            averagePerTransaction = if (totalTransactions == 0) 0L else totalSales / totalTransactions,
            totalDiscounts = totalDiscounts,
        )

        val chart = buildChart(range, transaksi)
        val productRecaps = aggregateProducts(details)
        val topProducts = productRecaps
            .sortedWith(compareByDescending<ProductRecap> { it.qty }.thenByDescending { it.revenue })
            .take(5)
        val slowProducts = productRecaps
            .filter { it.qty > 0 }
            .sortedWith(compareBy<ProductRecap> { it.qty }.thenBy { it.revenue })
            .take(5)
        val paymentBreakdown = aggregatePaymentMethods(payments, methodNames)
        val best = RecapProductRecommendationPolicy.selectTopSeller(totalTransactions, topProducts)
        val lowest = RecapProductRecommendationPolicy.selectNeedsAttention(totalTransactions, slowProducts)
        val productInsight = RecapProductRecommendationPolicy.buildInsight(
            totalTransactions = totalTransactions,
            topSeller = best,
            needsAttention = lowest,
        )

        return RecapDashboard(
            range = range,
            metrics = metrics,
            chart = chart,
            bestProduct = best,
            lowestProduct = lowest,
            productInsight = productInsight,
            paymentBreakdown = paymentBreakdown,
            topProducts = topProducts,
            slowProducts = slowProducts,
        )
    }

    fun getTopProducts(
        range: RecapRange,
        anchorDate: String,
        limit: Int = 5,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): List<ProductRecap> {
        migrateCompletedOrdersToTransaksi(outletId)
        val transaksi = filteredTransaksi(range, anchorDate, outletId)
        val details = filteredDetailsForTransaksiIds(transaksi.map { it.id_transaksi }.toSet())
        return aggregateProducts(details)
            .sortedByDescending { it.qty }
            .take(limit)
    }

    fun getBottomProducts(
        range: RecapRange,
        anchorDate: String,
        limit: Int = 5,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): List<ProductRecap> {
        migrateCompletedOrdersToTransaksi(outletId)
        val transaksi = filteredTransaksi(range, anchorDate, outletId)
        val details = filteredDetailsForTransaksiIds(transaksi.map { it.id_transaksi }.toSet())
        return aggregateProducts(details)
            .filter { it.qty > 0 }
            .sortedBy { it.qty }
            .take(limit)
    }

    private fun filteredTransaksi(
        range: RecapRange,
        anchorDate: String,
        outletId: String,
    ): List<com.durrr.first.Toko_transaksi> {
        val all = db.tokoQueries.selectAllTransaksi(outletId).executeAsList()
        val normalizedAnchorDate = extractDate(anchorDate)
        val anchorEpochDay = toEpochDay(normalizedAnchorDate)
        return when (range) {
            RecapRange.TODAY -> all.filter { extractDate(it.c_date) == normalizedAnchorDate }
            RecapRange.MONTH -> {
                val anchorMonth = normalizedAnchorDate.take(7)
                all.filter { extractDate(it.c_date).take(7) == anchorMonth }
            }
            RecapRange.WEEK -> {
                val endDay = anchorEpochDay ?: return emptyList()
                all.filter { row ->
                    val day = toEpochDay(extractDate(row.c_date)) ?: return@filter false
                    day in (endDay - 6)..endDay
                }
            }
            RecapRange.ALL -> all
        }
    }

    private fun filteredDetailsForTransaksiIds(ids: Set<String>): List<com.durrr.first.Toko_transaksi_detail> {
        if (ids.isEmpty()) return emptyList()
        return db.tokoQueries.selectAllTransaksiDetail().executeAsList().filter { it.id_transaksi in ids }
    }

    private fun filteredPaymentsForTransaksiIds(
        ids: Set<String>,
        outletId: String,
    ): List<com.durrr.first.Toko_pembayaran> {
        if (ids.isEmpty()) return emptyList()
        return db.tokoQueries.selectAllPembayaran(outletId).executeAsList()
            .filter { payment -> payment.id_transaksi != null && payment.id_transaksi in ids }
    }

    private fun buildChart(range: RecapRange, transaksi: List<com.durrr.first.Toko_transaksi>): List<RecapChartPoint> {
        return when (range) {
            RecapRange.TODAY -> transaksi
                .groupBy { extractHour(it.c_date) }
                .entries
                .sortedBy { it.key }
                .map { entry ->
                    val hour = entry.key
                    val rows = entry.value
                    RecapChartPoint(label = "$hour:00", total = rows.sumOf { parseLong(it.rekap_harga_total) })
                }

            RecapRange.WEEK -> transaksi
                .groupBy { extractDate(it.c_date) }
                .entries
                .sortedBy { it.key }
                .map { entry ->
                    val date = entry.key
                    val rows = entry.value
                    RecapChartPoint(label = date.takeLast(5), total = rows.sumOf { parseLong(it.rekap_harga_total) })
                }

            RecapRange.MONTH -> transaksi
                .groupBy { extractDate(it.c_date) }
                .entries
                .sortedBy { it.key }
                .map { entry ->
                    val date = entry.key
                    val rows = entry.value
                    RecapChartPoint(label = date.takeLast(2), total = rows.sumOf { parseLong(it.rekap_harga_total) })
                }

            RecapRange.ALL -> transaksi
                .groupBy { extractDate(it.c_date).take(7) }
                .entries
                .sortedBy { it.key }
                .map { entry ->
                    val month = entry.key
                    val rows = entry.value
                    RecapChartPoint(label = month, total = rows.sumOf { parseLong(it.rekap_harga_total) })
                }
        }
    }

    private fun aggregateProducts(details: List<com.durrr.first.Toko_transaksi_detail>): List<ProductRecap> {
        return details
            .groupBy { "${it.id_item}|${it.nama_item ?: ""}" }
            .map { (_, rows) ->
                ProductRecap(
                    itemId = rows.firstOrNull()?.id_item,
                    itemName = rows.firstOrNull()?.nama_item ?: "Unknown Item",
                    qty = rows.sumOf { parseLong(it.jumlah) },
                    revenue = rows.sumOf { parseLong(it.rekap_harga_detail) },
                )
            }
            .filter { it.qty > 0L || it.revenue > 0L }
    }

    private fun aggregatePaymentMethods(
        payments: List<com.durrr.first.Toko_pembayaran>,
        methodNames: Map<String, String>,
    ): List<PaymentMethodRecap> {
        return payments
            .groupBy { it.id_jenis_bayar }
            .map { (methodId, rows) ->
                PaymentMethodRecap(
                    methodId = methodId,
                    methodName = methodNames[methodId] ?: (methodId ?: "Unknown"),
                    total = rows.sumOf { parseLong(it.dibayar) },
                )
            }
            .sortedByDescending { it.total }
    }

    private fun extractDate(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return if (value.length >= 10) value.substring(0, 10) else value
    }

    private fun extractHour(value: String?): String {
        if (value.isNullOrBlank()) return "00"
        return if (value.length >= 13) value.substring(11, 13) else "00"
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

    private fun parseLong(value: String?): Long = value?.toLongOrNull() ?: 0L

    private companion object {
        const val DAYS_0000_TO_1970 = 719528L
    }

    private fun migrateCompletedOrdersToTransaksi(outletId: String) {
        val completed = db.tokoQueries.selectAllOrders(outletId).executeAsList()
            .filter { it.status.equals("DONE", ignoreCase = true) }
        if (completed.isEmpty()) return

        val methodIds = db.tokoQueries.selectAllJenisBayar().executeAsList()
        val cashMethodId = methodIds.firstOrNull {
            it.id_jenis_bayar.contains("cash", ignoreCase = true) || it.nama.contains("cash", ignoreCase = true)
        }?.id_jenis_bayar ?: methodIds.firstOrNull()?.id_jenis_bayar

        db.transaction {
            completed.forEach { order ->
                val transaksiId = "ordtrx_${order.id_order}"
                val exists = db.tokoQueries.selectTransaksiById(transaksiId, outletId).executeAsOneOrNull() != null
                if (exists) return@forEach

                val items = db.tokoQueries.selectOrderItemsByOrder(order.id_order).executeAsList()
                val createdAt = order.updated_at?.takeIf { it.isNotBlank() } ?: order.created_at
                val total = items.sumOf { it.qty * it.price }

                db.tokoQueries.insertTransaksi(
                    id_transaksi = transaksiId,
                    c = "System",
                    c_by = "system",
                    c_date = createdAt,
                    meja = order.token,
                    diskon_plus = "0",
                    pajak = "0",
                    service_charge = "0",
                    round_harga = "0",
                    rekap_harga_total = total.toString(),
                    dibayar = total.toString(),
                    outlet_id = outletId,
                )

                items.forEachIndexed { index, item ->
                    db.tokoQueries.insertTransaksiDetail(
                        id_transaksi_detail = "ordtd_${order.id_order}_$index",
                        id_transaksi = transaksiId,
                        id_item = item.id_item,
                        nama_item = item.nama_item,
                        jumlah = item.qty.toString(),
                        harga = item.price.toString(),
                        diskon = "0",
                        rekap_harga_detail = (item.qty * item.price).toString(),
                    )
                }

                if (cashMethodId != null) {
                    db.tokoQueries.insertPembayaran(
                        id_pembayaran = "ordpay_${order.id_order}",
                        id_transaksi = transaksiId,
                        dibayar = total.toString(),
                        kembalian = "0",
                        id_jenis_bayar = cashMethodId,
                        outlet_id = outletId,
                        c_date = createdAt,
                    )
                }
            }
        }
    }
}
