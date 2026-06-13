package com.example.tasama.presentation.transaction

import com.example.tasama.domain.model.Transaction

data class TransactionUiState(
    val transactions: List<Transaction> = emptyList()
)