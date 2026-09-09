package com.example.tasama.presentation.dashboard

import com.example.tasama.domain.model.SavingsInvitation
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.Transaction

data class DashboardUiState(
    val userName: String? = null,
    val balance: Long = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val transactions: List<Transaction> = emptyList(),
    val weeklySpending: List<DailySpending> = emptyList(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val monthlyTrends: List<MonthlyTrend> = emptyList(),
    val balanceHistory: List<BalancePoint> = emptyList(),
    
    // New fields for redesigned dashboard
    val totalSavingsBalance: Long = 0,
    val recentSavingsSpaces: List<SavingsSpace> = emptyList(),
    val recentActivities: List<DashboardActivity> = emptyList(),
    val pendingInvitations: List<SavingsInvitation> = emptyList(),
    
    val showAddTransactionDialog: Boolean = false,
    val error: String? = null
)

data class DashboardActivity(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val timestamp: Long,
    val type: DashboardActivityType
)

enum class DashboardActivityType {
    SAVINGS, CHAT, TRANSACTION, SYSTEM
}

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
