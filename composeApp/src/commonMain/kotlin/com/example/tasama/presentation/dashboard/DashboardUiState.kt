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
    
    // Financial Overview
    val selectedSpaceId: String? = null, // null means "All Spaces"
    val selectedPeriod: FinancialPeriod = FinancialPeriod.THIS_MONTH,
    val financialSummary: FinancialSummary = FinancialSummary(),
    val trendChartData: List<MonthlyTrend> = emptyList(),

    // New fields for redesigned dashboard
    val totalSavingsBalance: Long = 0,
    val recentSavingsSpaces: List<SavingsSpace> = emptyList(),
    val pendingInvitations: List<SavingsInvitation> = emptyList(),
    val hasPendingPartnerRequest: Boolean = false,
    val hasUnreadNotifications: Boolean = false,
    val isLoading: Boolean = false,
    val showAddTransactionDialog: Boolean = false,
    val error: String? = null,
    val currency: String = "IDR"
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
    val label: String,
    val income: Long,
    val expense: Long
)

enum class FinancialPeriod {
    THIS_WEEK, THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, THIS_YEAR
}

data class FinancialSummary(
    val income: Long = 0,
    val expense: Long = 0,
    val net: Long = 0
)
