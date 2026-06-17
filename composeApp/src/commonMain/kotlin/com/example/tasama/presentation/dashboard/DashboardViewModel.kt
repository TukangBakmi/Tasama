package com.example.tasama.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class DashboardViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            repository.getTransactionsFlow().collect { transactions ->
                val income = transactions
                    .filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amount }

                val expense = transactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amount }

                // Calculate weekly spending (last 7 days)
                val nowEpoch = Clock.System.now().toEpochMilliseconds()
                val systemTZ = TimeZone.currentSystemDefault()
                val today = Instant.fromEpochMilliseconds(nowEpoch).toLocalDateTime(systemTZ).date
                
                val last7Days = (0..6).map { i ->
                    today.minus(i, DateTimeUnit.DAY)
                }.reversed()

                val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }

                val weeklySpending = last7Days.map { date ->
                    val dayAmount = expenseTransactions.filter { 
                        Instant.fromEpochMilliseconds(it.createdAt).toLocalDateTime(systemTZ).date == date
                    }.sumOf { it.amount }
                    
                    DailySpending(
                        day = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                        amount = dayAmount
                    )
                }

                // Calculate category spending
                val totalExpense = expenseTransactions.sumOf { it.amount }
                val categorySpending = if (totalExpense > 0) {
                    expenseTransactions
                        .groupBy { it.category }
                        .map { (category, txs) ->
                            val amount = txs.sumOf { it.amount }
                            CategorySpending(
                                category = category,
                                amount = amount,
                                percentage = amount.toFloat() / totalExpense
                            )
                        }
                        .sortedByDescending { it.amount }
                } else {
                    emptyList()
                }

                // Calculate monthly trends (last 6 months)
                val last6Months = (0..5).map { i ->
                    val monthDate = today.minus(i, DateTimeUnit.MONTH)
                    monthDate.year to monthDate.month
                }.reversed()

                val monthlyTrends = last6Months.map { (year, month) ->
                    val monthTransactions = transactions.filter {
                        val txDate = Instant.fromEpochMilliseconds(it.createdAt).toLocalDateTime(systemTZ).date
                        txDate.year == year && txDate.month == month
                    }
                    val monthIncome = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    val monthExpense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

                    MonthlyTrend(
                        month = month.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                        income = monthIncome,
                        expense = monthExpense
                    )
                }

                // Calculate balance history (last 7 days cumulative)
                val sortedTxs = transactions.sortedBy { it.createdAt }
                val balanceHistory = last7Days.map { date ->
                    val endOfDay = Instant.fromEpochMilliseconds(
                        date.atStartOfDayIn(systemTZ).toEpochMilliseconds() + 86400000 - 1
                    ).toEpochMilliseconds()
                    
                    val balanceAtDate = sortedTxs.filter { it.createdAt <= endOfDay }
                        .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                    
                    BalancePoint(
                        label = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                        balance = balanceAtDate
                    )
                }

                _uiState.value = DashboardUiState(
                    balance = income - expense,
                    income = income,
                    expense = expense,
                    transactions = transactions.sortedByDescending { it.createdAt }.take(10),
                    weeklySpending = weeklySpending,
                    categorySpending = categorySpending,
                    monthlyTrends = monthlyTrends,
                    balanceHistory = balanceHistory
                )
            }
        }
    }

    fun addTransaction(transaction: com.example.tasama.domain.model.Transaction) {
        viewModelScope.launch {
            repository.addTransaction(transaction)
        }
    }

    fun onAddTransactionClick() {
        _uiState.update { it.copy(showAddTransactionDialog = true) }
    }

    fun onDismissAddTransaction() {
        _uiState.update { it.copy(showAddTransactionDialog = false) }
    }
}
