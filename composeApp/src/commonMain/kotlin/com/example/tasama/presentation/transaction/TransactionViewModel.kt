package com.example.tasama.presentation.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class TransactionViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(TransactionUiState())

    val uiState: StateFlow<TransactionUiState> =
        _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value =
                TransactionUiState(
                    transactions = repository.getTransactions()
                )
        }
    }

    fun addTransaction(
        amount: Long,
        note: String
    ) {
        viewModelScope.launch {
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
            loadData()
        }
    }
}