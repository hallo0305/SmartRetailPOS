package com.industri.smartpos

import java.io.IOException

// 1. Error ketika barcode tidak ditemukan
class ProductBarcodeNotFoundException(
    val barcode: String
) : Exception(
    "Barang dengan barcode [$barcode] tidak terdaftar pada katalog sistem."
)

// 2. Error ketika payment gateway timeout
class PaymentGatewayTimeoutException(
    val gatewayName: String
) : IOException(
    "Layanan pembayaran $gatewayName tidak merespons dalam 15 detik."
)

// 3. Error ketika limit kasir terlampaui
class CashierLimitExceededException(
    val maxLimit: Double
) : Exception(
    "Total transaksi melebihi limit otorisasi kasir mandiri (Maksimal Rp %,.0f)"
        .format(maxLimit)
)