package com.durrr.first.domain.model

enum class RecapRange {
    TODAY,
    WEEK,
    MONTH,
}

data class RecapMetrics(
    val totalSales: Long,
    val totalTransactions: Int,
    val averagePerTransaction: Long,
    val totalDiscounts: Long,
)

data class RecapChartPoint(
    val label: String,
    val total: Long,
)

data class ProductRecap(
    val itemId: String?,
    val itemName: String,
    val qty: Long,
    val revenue: Long,
)

data class PaymentMethodRecap(
    val methodId: String?,
    val methodName: String,
    val total: Long,
)

data class RecapDashboard(
    val range: RecapRange,
    val metrics: RecapMetrics,
    val chart: List<RecapChartPoint>,
    val bestProduct: ProductRecap?,
    val lowestProduct: ProductRecap?,
    val paymentBreakdown: List<PaymentMethodRecap> = emptyList(),
    val topProducts: List<ProductRecap> = emptyList(),
    val slowProducts: List<ProductRecap> = emptyList(),
)
