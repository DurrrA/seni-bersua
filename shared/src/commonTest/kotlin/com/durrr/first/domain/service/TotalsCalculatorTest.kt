package com.durrr.first.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals

class TotalsCalculatorTest {
    private val calculator = TotalsCalculator()

    @Test
    fun calculatesEmptyLines() {
        val result = calculator.calculate(lines = emptyList())
        assertEquals(0, result.subtotal)
        assertEquals(0, result.discountTotal)
        assertEquals(0, result.grandTotal)
        assertEquals(0, result.change)
        assertEquals(0, result.remaining)
    }

    @Test
    fun calculatesWithDiscountsAndCharges() {
        val result = calculator.calculate(
            lines = listOf(
                TotalsCalculator.Line(qty = 2, price = 10_000, discount = 1_000),
                TotalsCalculator.Line(qty = 1, price = 5_000, discount = 0),
            ),
            discountPlus = 500,
            tax = 1_000,
            serviceCharge = 500,
            rounding = -100,
            paid = 30_000,
        )

        assertEquals(24_000, result.subtotal)
        assertEquals(1_500, result.discountTotal)
        assertEquals(24_900, result.grandTotal)
        assertEquals(5_100, result.change)
        assertEquals(0, result.remaining)
    }

    @Test
    fun calculatesRemainingWhenUnderpaid() {
        val result = calculator.calculate(
            lines = listOf(TotalsCalculator.Line(qty = 1, price = 15_000, discount = 0)),
            paid = 10_000,
        )
        assertEquals(15_000, result.grandTotal)
        assertEquals(0, result.discountTotal)
        assertEquals(0, result.change)
        assertEquals(5_000, result.remaining)
    }
}
