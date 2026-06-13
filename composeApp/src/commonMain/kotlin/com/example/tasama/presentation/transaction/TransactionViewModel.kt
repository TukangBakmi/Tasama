package com.example.tasama.presentation.transaction

import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class TransactionViewModel(
    private val repository: TransactionRepository
) {

    private val _uiState =
        MutableStateFlow(TransactionUiState())

    val uiState: StateFlow<TransactionUiState> =
        _uiState.asStateFlow()

    suspend fun loadData() {

        _uiState.value =
            TransactionUiState(
                transactions = repository.getTransactions()
            )

    }

    suspend fun addTransaction(
        amount: Long,
        note: String
    ) {
        repository.addTransaction(
            Transaction(
                id = Random.nextInt().toString(),
                amount = amount,
                type = TransactionType.EXPENSE,
                category = "Food",
                note = note,
                createdAt = 0L
            )
        )
    }
}