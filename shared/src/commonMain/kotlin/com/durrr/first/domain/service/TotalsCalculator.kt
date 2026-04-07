package com.durrr.first.domain.service

import kotlin.math.max

class TotalsCalculator {
    data class Line(
        val qty: Long,
        val price: Long,
        val discount: Long = 0,
    )

    data class Result(
        val subtotal: Long,
        val discountTotal: Long,
        val discountPlus: Long,
        val tax: Long,
        val serviceCharge: Long,
        val rounding: Long,
        val grandTotal: Long,
        val paid: Long,
        val change: Long,
        val remaining: Long,
    )

    fun calculate(
        lines: List<Line>,
        discountPlus: Long = 0,
        tax: Long = 0,
        serviceCharge: Long = 0,
        rounding: Long = 0,
        paid: Long = 0,
    ): Result {
        val lineSubtotal = lines.sumOf { line -> line.qty * line.price }
        val lineDiscountTotal = lines.sumOf { line -> line.discount }
        val subtotal = lineSubtotal - lineDiscountTotal
        val discountTotal = lineDiscountTotal + discountPlus
        val grandTotal = subtotal - discountPlus + tax + serviceCharge + rounding
        val remaining = max(0, grandTotal - paid)
        val change = max(0, paid - grandTotal)
        return Result(
            subtotal = subtotal,
            discountTotal = discountTotal,
            discountPlus = discountPlus,
            tax = tax,
            serviceCharge = serviceCharge,
            rounding = rounding,
            grandTotal = grandTotal,
            paid = paid,
            change = change,
            remaining = remaining,
        )
    }
}
