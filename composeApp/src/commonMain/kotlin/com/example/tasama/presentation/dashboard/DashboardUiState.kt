package com.example.tasama.presentation.dashboard

import com.example.tasama.domain.model.Transaction

data class DashboardUiState(
    val balance: Long = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val transactions: List<Transaction> = emptyList(),
    val weeklySpending: List<DailySpending> = emptyList(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val monthlyTrends: List<MonthlyTrend> = emptyList(),
    val balanceHistory: List<BalancePoint> = emptyList(),
    val showAddTransactionDialog: Boolean = false,
    val error: String? = null
)

data class BalancePoint(
    val label: String,
    val balance: Long
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

data class MonthlyTrend(
    val month: String,
    val income: Long,
    val expense: Long
)
