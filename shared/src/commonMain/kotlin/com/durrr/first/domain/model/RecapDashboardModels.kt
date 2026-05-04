package com.durrr.first.domain.model

enum class RecapRange {
    TODAY,
    WEEK,
    MONTH,
    ALL,
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

object RecapProductRecommendationPolicy {
    const val MIN_RECOMMENDATION_TRANSACTIONS = 3
    const val MIN_TOP_QTY = 5L
    const val MAX_LOW_QTY = 2L

    fun selectTopSeller(
        totalTransactions: Int,
        products: List<ProductRecap>,
    ): ProductRecap? {
        if (totalTransactions < MIN_RECOMMENDATION_TRANSACTIONS) return null
        return products
            .sortedWith(compareByDescending<ProductRecap> { it.qty }.thenByDescending { it.revenue }.thenBy { it.itemName })
            .firstOrNull { it.qty >= MIN_TOP_QTY }
    }

    fun selectNeedsAttention(
        totalTransactions: Int,
        products: List<ProductRecap>,
    ): ProductRecap? {
        if (totalTransactions < MIN_RECOMMENDATION_TRANSACTIONS) return null
        return products
            .filter { it.qty > 0 }
            .sortedWith(compareBy<ProductRecap> { it.qty }.thenBy { it.revenue }.thenBy { it.itemName })
            .firstOrNull { it.qty <= MAX_LOW_QTY }
    }

    fun buildInsight(
        totalTransactions: Int,
        topSeller: ProductRecap?,
        needsAttention: ProductRecap?,
    ): String {
        if (totalTransactions < MIN_RECOMMENDATION_TRANSACTIONS) {
            return "Need at least $MIN_RECOMMENDATION_TRANSACTIONS transactions before product recommendations are shown."
        }
        return when {
            topSeller != null && needsAttention != null && topSeller.itemId != needsAttention.itemId ->
                "Bundle or upsell ${needsAttention.itemName} with ${topSeller.itemName} to lift low-performing sales."
            topSeller != null && needsAttention != null ->
                "${topSeller.itemName} is the current leader. Wait for more item variation before pairing a slow-mover recommendation."
            topSeller == null && needsAttention == null ->
                "No items meet the current recommendation standard yet. Top seller needs at least $MIN_TOP_QTY qty and slow movers must stay at $MAX_LOW_QTY qty or below."
            topSeller == null ->
                "Need a stronger top seller first. An item must sell at least $MIN_TOP_QTY qty in this period."
            else ->
                "Top seller is established. Wait until another item drops to $MAX_LOW_QTY qty or below for a bundle recommendation."
        }
    }
}

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
    val productInsight: String,
    val paymentBreakdown: List<PaymentMethodRecap> = emptyList(),
    val topProducts: List<ProductRecap> = emptyList(),
    val slowProducts: List<ProductRecap> = emptyList(),
)
