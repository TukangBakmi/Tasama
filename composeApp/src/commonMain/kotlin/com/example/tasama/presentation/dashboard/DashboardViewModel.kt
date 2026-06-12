package com.example.tasama.presentation.dashboard

import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel(
    private val repository: TransactionRepository
) {

    private val _uiState = MutableStateFlow(DashboardUiState())

    val uiState: StateFlow<DashboardUiState> =
        _uiState.asStateFlow()

    suspend fun loadData() {

        val transactions = repository.getTransactions()

        val income = transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val expense = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        _uiState.value = DashboardUiState(
            balance = income - expense,
            income = income,
            expense = expense,
            transactions = transactions
        )
    }
}