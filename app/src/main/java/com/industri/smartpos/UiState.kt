package com.industri.smartpos

sealed class UiState<out T> {

    object Idle : UiState<Nothing>()

    object Loading : UiState<Nothing>()

    data class Success<out T>(
        val data: T
    ) : UiState<T>()

    data class Error(
        val message: String,
        val errorCode: Int? = null,
        val canRetry: Boolean = true
    ) : UiState<Nothing>()
}

data class PosTransaction(
    val transactionId: String,
    val barcode: String,
    val productName: String,
    val totalAmount: Double,
    val status: String
)