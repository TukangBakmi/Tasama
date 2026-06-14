package com.example.tasama.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())

    val uiState: StateFlow<DashboardUiState> =
        _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val transactions = repository.getTransactions()

            val income = transactions
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount }

            val expense = transactions
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }

            // Dummy weekly spending data for visualization
            val weeklySpending = listOf(
                DailySpending("Mon", 150000),
                DailySpending("Tue", 80000),
                DailySpending("Wed", 200000),
                DailySpending("Thu", 50000),
                DailySpending("Fri", 120000),
                DailySpending("Sat", 300000),
                DailySpending("Sun", 90000)
            )

            // Dummy category spending data
            val categorySpending = listOf(
                CategorySpending("Food", 450000, 0.6f),
                CategorySpending("Transport", 150000, 0.2f),
                CategorySpending("Bills", 100000, 0.13f),
                CategorySpending("Other", 50000, 0.07f)
            )

            _uiState.value = DashboardUiState(
                balance = income - expense,
                income = income,
                expense = expense,
                transactions = transactions,
                weeklySpending = weeklySpending,
                categorySpending = categorySpending
            )
        }
    }
}