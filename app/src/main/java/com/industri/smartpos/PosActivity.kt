package com.industri.smartpos

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PosActivity : AppCompatActivity() {

    // Counter jumlah percobaan ulang
    private var retryAttempt = 0

    private lateinit var etBarcode: EditText
    private lateinit var etTotal: EditText
    private lateinit var btnProcessPayment: Button
    private lateinit var pbPosLoading: ProgressBar
    private lateinit var cardPosResult: CardView
    private lateinit var tvPosStatus: TextView
    private lateinit var tvPosDetails: TextView
    private lateinit var rgSimulation: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pos)

        etBarcode = findViewById(R.id.etBarcode)
        etTotal = findViewById(R.id.etTotal)
        btnProcessPayment = findViewById(R.id.btnProcessPayment)
        pbPosLoading = findViewById(R.id.pbPosLoading)
        cardPosResult = findViewById(R.id.cardPosResult)
        tvPosStatus = findViewById(R.id.tvPosStatus)
        tvPosDetails = findViewById(R.id.tvPosDetails)
        rgSimulation = findViewById(R.id.rgSimulation)

        btnProcessPayment.setOnClickListener {

            val barcode = etBarcode.text.toString().trim()
            val totalText = etTotal.text.toString().trim()

            if (barcode.isEmpty()) {
                Toast.makeText(
                    this,
                    "Barcode wajib diisi atau dipindai",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (totalText.isEmpty()) {
                Toast.makeText(
                    this,
                    "Total transaksi wajib diisi",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val total = totalText.toDoubleOrNull()

            if (total == null) {
                Toast.makeText(
                    this,
                    "Total transaksi harus berupa angka",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            eksekusiTransaksi(barcode, total)
        }
    }

    private fun eksekusiTransaksi(
        barcode: String,
        total: Double
    ) {

        pbPosLoading.visibility = View.VISIBLE
        cardPosResult.visibility = View.GONE
        btnProcessPayment.isEnabled = false

        val coroutineExceptionHandler =
            CoroutineExceptionHandler { _, exception ->

                Timber.e(
                    exception,
                    "FATAL COROUTINE ERROR TERTANGKAP: %s",
                    exception.message
                )

                runOnUiThread {

                    pbPosLoading.visibility = View.GONE
                    btnProcessPayment.isEnabled = true

                    tampilkanSnackbarError(
                        "Kesalahan sistem tak terduga: ${exception.localizedMessage}",
                        canRetry = false
                    )
                }
            }

        lifecycleScope.launch(
            Dispatchers.IO + coroutineExceptionHandler
        ) {

            Timber.d(
                "Memulai alur pembayaran untuk Barcode: %s",
                barcode
            )

            val hasil = runCatching {

                // Simulasi latensi jaringan
                delay(1200)

                // Cek batas otorisasi kasir
                if (total > 10_000_000) {
                    throw CashierLimitExceededException(
                        10_000_000.0
                    )
                }

                // Simulasi berdasarkan pilihan RadioButton
                when (rgSimulation.checkedRadioButtonId) {

                    R.id.rbBarcodeError -> {

                        throw ProductBarcodeNotFoundException(
                            barcode
                        )
                    }

                    R.id.rbNetworkTimeout -> {

                        throw PaymentGatewayTimeoutException(
                            "QRIS Bank Settlement"
                        )
                    }

                    else -> {

                        PosTransaction(
                            transactionId = "TRX-2026-9901",
                            barcode = barcode,
                            productName = "Susu UHT Full Cream 1 Liter",
                            totalAmount = total,
                            status = "SETTLED_SUCCESS"
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {

                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true

                hasil.onSuccess { trx ->

                    retryAttempt = 0

                    Timber.i(
                        "Transaksi kasir berhasil dicatat: %s",
                        trx.transactionId
                    )

                    cardPosResult.visibility = View.VISIBLE

                    tvPosStatus.text =
                        "TRANSAKSI BERHASIL (LUNAS)"

                    tvPosDetails.text = """
                        ID Transaksi : ${trx.transactionId}
                        Produk : ${trx.productName}
                        Total Bayar : Rp ${"%,.2f".format(trx.totalAmount)}
                        Waktu : Baru Saja
                    """.trimIndent()
                }

                    .onFailure { err ->

                        Timber.w(
                            "Transaksi kasir ditolak / gagal: %s",
                            err.message
                        )

                        when (err) {

                            is ProductBarcodeNotFoundException -> {

                                tampilkanSnackbarError(
                                    err.message
                                        ?: "Barcode tidak ditemukan",
                                    canRetry = false
                                )
                            }

                            is PaymentGatewayTimeoutException -> {

                                tampilkanSnackbarError(
                                    err.message
                                        ?: "Koneksi gateway terputus",
                                    canRetry = true
                                )
                            }

                            is CashierLimitExceededException -> {

                                tampilkanDialogSupervisor(
                                    err.message
                                        ?: "Transaksi melebihi batas otorisasi kasir"
                                )
                            }

                            else -> {

                                tampilkanSnackbarError(
                                    "Kegagalan operasional: ${err.message}",
                                    canRetry = true
                                )
                            }
                        }
                    }
            }
        }
    }

    private fun tampilkanSnackbarError(
        pesan: String,
        canRetry: Boolean
    ) {

        val root =
            findViewById<View>(R.id.coordinatorLayout)

        val snackbar = Snackbar.make(
            root,
            pesan,
            Snackbar.LENGTH_LONG
        )

        snackbar.setBackgroundTint(
            android.graphics.Color.parseColor("#991B1B")
        )

        snackbar.setTextColor(
            android.graphics.Color.WHITE
        )

        if (canRetry) {

            snackbar.setAction("COBA LAGI") {

                retryAttempt++

                if (retryAttempt >= 3) {

                    tampilkanDialogPembayaranManual()

                } else {

                    val barcode =
                        etBarcode.text.toString().trim()

                    val totalText =
                        etTotal.text.toString().trim()

                    val total =
                        totalText.toDoubleOrNull()

                    if (
                        barcode.isNotEmpty() &&
                        total != null
                    ) {
                        eksekusiTransaksi(
                            barcode,
                            total
                        )
                    }
                }
            }

            snackbar.setActionTextColor(
                android.graphics.Color.parseColor("#FEF08A")
            )
        }

        snackbar.show()
    }

    private fun tampilkanDialogPembayaranManual() {

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pembayaran Gagal")
            .setMessage(
                "Percobaan pembayaran melalui jaringan perbankan " +
                        "telah mencapai 3 kali.\n\n" +
                        "Disarankan untuk mengalihkan pelanggan ke " +
                        "Pembayaran Tunai Manual."
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Coba Lagi") { dialog, _ ->

                retryAttempt = 0

                val barcode =
                    etBarcode.text.toString().trim()

                val totalText =
                    etTotal.text.toString().trim()

                val total =
                    totalText.toDoubleOrNull()

                if (
                    barcode.isNotEmpty() &&
                    total != null
                ) {
                    eksekusiTransaksi(
                        barcode,
                        total
                    )
                }

                dialog.dismiss()
            }
            .show()
    }

    private fun tampilkanDialogSupervisor(
        pesan: String
    ) {

        val inputPin = EditText(this)

        inputPin.hint = "Masukkan PIN Supervisor"
        inputPin.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val container = LinearLayout(this)

        container.orientation = LinearLayout.VERTICAL
        container.setPadding(50, 0, 50, 0)
        container.addView(inputPin)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Otorisasi Supervisor")
            .setMessage(pesan)
            .setView(container)
            .setPositiveButton("OTORISASI") { dialog, _ ->

                val pin = inputPin.text.toString()

                if (pin.isNotEmpty()) {

                    Toast.makeText(
                        this,
                        "PIN Supervisor diterima. Silakan lakukan verifikasi.",
                        Toast.LENGTH_LONG
                    ).show()

                    Timber.i(
                        "Permintaan otorisasi supervisor diajukan."
                    )

                } else {

                    Toast.makeText(
                        this,
                        "PIN Supervisor wajib diisi.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                dialog.dismiss()
            }
            .setNegativeButton("BATAL") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}