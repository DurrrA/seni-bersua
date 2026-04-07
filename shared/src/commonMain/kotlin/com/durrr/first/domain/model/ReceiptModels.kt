package com.durrr.first.domain.model

data class ReceiptConfig(
    val storeName: String = "SuCash",
    val storeAddressOrPhone: String = "",
    val headerLogoPath: String = "",
    val watermarkLogoPath: String = "",
    val footerText: String = "Thank you",
)

data class ReceiptData(
    val transaksi: Transaksi,
    val details: List<TransaksiDetail>,
    val pembayaran: Pembayaran?,
)
