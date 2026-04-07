package com.durrr.first.domain.model

data class Pelanggan(
    val id: String,
    val nama: String?,
    val barcode: String?,
    val poin: Long,
    val diskon: Long,
    val isDiskon: Boolean,
    val usePoin: Boolean,
)
