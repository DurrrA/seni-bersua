package com.durrr.first.core.utils

import kotlin.math.absoluteValue

fun formatNumber(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val grouped = value.absoluteValue
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return "$sign$grouped"
}

fun formatNumber(value: Int): String = formatNumber(value.toLong())

fun formatRupiah(value: Long): String = "Rp ${formatNumber(value)}"

fun formatRupiah(value: Int): String = formatRupiah(value.toLong())
