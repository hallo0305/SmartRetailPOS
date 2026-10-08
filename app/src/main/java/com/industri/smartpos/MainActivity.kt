package com.industri.smartpos

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber
import android.content.Intent

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val etNominal = findViewById<EditText>(R.id.etNominal)
        val etQty = findViewById<EditText>(R.id.etQty)
        val btnHitung = findViewById<Button>(R.id.btnHitung)
        val tvHasil = findViewById<TextView>(R.id.tvHasil)
        val btnLanjut = findViewById<Button>(R.id.btnLanjut)

        btnLanjut.setOnClickListener {
            startActivity(
                Intent(this, PosActivity::class.java)
            )
        }

        btnHitung.setOnClickListener {

            val nominalStr = etNominal.text.toString()
            val qtyStr = etQty.text.toString()

            Timber.d(
                "Menghitung transaksi kasir: Nominal=%s, Qty=%s",
                nominalStr,
                qtyStr
            )

            val kalkulasiResult = runCatching {

                val nominal = nominalStr.toDouble()
                val qty = qtyStr.toInt()

                if (qty <= 0) {
                    throw IllegalArgumentException(
                        "Kuantitas barang minimal 1"
                    )
                }

                nominal * qty
            }

            kalkulasiResult.onSuccess { total ->

                Timber.i(
                    "Kalkulasi sukses: Total bayar Rp %,.2f",
                    total
                )

                tvHasil.text =
                    "Total Transaksi: Rp %,.2f".format(total)
            }

            kalkulasiResult.onFailure { error ->

                Timber.e(
                    error,
                    "Terjadi kesalahan kalkulasi input kasir"
                )

                tvHasil.text =
                    "Error Input: ${error.message ?: "Input tidak valid"}"
            }
        }
    }
}