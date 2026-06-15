package com.example.tasama.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
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

                _uiState.value = DashboardUiState(
                    balance = income - expense,
                    income = income,
                    expense = expense,
                    transactions = transactions.sortedByDescending { it.createdAt }.take(10),
                    weeklySpending = weeklySpending,
                    categorySpending = categorySpending
                )
            }
        }
    }
}
