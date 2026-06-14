package com.example.tasama.presentation.dashboard

import com.example.tasama.domain.model.Transaction

data class DashboardUiState(
    val balance: Long = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val transactions: List<Transaction> = emptyList(),
    val weeklySpending: List<DailySpending> = emptyList(),
    val categorySpending: List<CategorySpending> = emptyList()
)

data class DailySpending(
    val day: String,
    val amount: Long
)

data class CategorySpending(
    val category: String,
    val amount: Long,
    val percentage: Float
)
