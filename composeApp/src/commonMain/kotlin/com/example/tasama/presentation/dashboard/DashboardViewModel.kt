package com.example.tasama.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.InvitationStatus
import com.example.tasama.domain.model.SavingsInvitation
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.domain.repository.TransactionRepository
import com.example.tasama.util.formatAmount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val savingsRepository: SavingsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var dataJob: Job? = null

    init {
        observeUserSession()
    }

    private fun observeUserSession() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                if (uid == null) {
                    dataJob?.cancel()
                    _uiState.value = DashboardUiState()
                } else {
                    val user = authRepository.getUser(uid)
                    _uiState.update { it.copy(userName = user?.name) }
                    observeData()
                }
            }
        }
    }

    private fun observeData() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            combine(
                repository.getTransactionsFlow(),
                savingsRepository.getSavingsSpaces(),
                savingsRepository.getMyInvitations(),
                chatRepository.getChannels()
            ) { transactions, spaces, invitations, channels ->
                updateDashboardWith(transactions, spaces, invitations, channels)
            }.collect { }
        }
    }

    private fun updateDashboardWith(
        transactions: List<Transaction>,
        spaces: List<SavingsSpace>,
        invitations: List<SavingsInvitation>,
        channels: List<ChatChannel>
    ) {
        val income = transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val expense = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val totalSavingsBalance = spaces.sumOf { it.balance }
        val recentSpaces = spaces.sortedByDescending { it.updatedAt }.take(2)
        val pendingInvitations = invitations.filter { it.status == InvitationStatus.PENDING }

        // Combine activities
        val activities = mutableListOf<DashboardActivity>()
        
        // Add recent transactions
        transactions.take(5).forEach { tx ->
            activities.add(
                DashboardActivity(
                    id = tx.id,
                    title = if (tx.type == TransactionType.INCOME) "Received Money" else "Spent Money",
                    description = "${tx.note} • Rp ${tx.amount.formatAmount()}",
                    icon = getCategoryEmoji(tx.category, tx.type),
                    timestamp = tx.createdAt,
                    type = DashboardActivityType.TRANSACTION
                )
            )
        }

        // Add recent chat messages
        channels.filter { it.lastMessage.isNotEmpty() }.take(3).forEach { channel ->
            activities.add(
                DashboardActivity(
                    id = channel.id,
                    title = "New Message",
                    description = channel.lastMessage,
                    icon = "💬",
                    timestamp = channel.lastMessageTimestamp,
                    type = DashboardActivityType.CHAT
                )
            )
        }

        val sortedActivities = activities.sortedByDescending { it.timestamp }.take(10)

        _uiState.update { it.copy(
            balance = income - expense,
            income = income,
            expense = expense,
            transactions = transactions.sortedByDescending { it.createdAt }.take(5),
            totalSavingsBalance = totalSavingsBalance,
            recentSavingsSpaces = recentSpaces,
            pendingInvitations = pendingInvitations,
            recentActivities = sortedActivities
        ) }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                repository.addTransaction(transaction)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to add transaction") }
            }
        }
    }

    fun onAddTransactionClick() {
        _uiState.update { it.copy(showAddTransactionDialog = true) }
    }

    fun onDismissAddTransaction() {
        _uiState.update { it.copy(showAddTransactionDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun getCategoryEmoji(category: String, type: TransactionType): String {
        if (type == TransactionType.INCOME) return "💰"
        
        return when (category.lowercase()) {
            "food", "makan", "minum", "restoran" -> "🍔"
            "transport", "transportasi", "ojek", "bensin" -> "🚗"
            "shopping", "belanja" -> "🛍️"
            "entertainment", "hiburan", "nonton" -> "🎬"
            "bills", "tagihan", "listrik", "air" -> "🧾"
            "health", "kesehatan", "obat" -> "🏥"
            "education", "pendidikan", "sekolah", "kuliah" -> "🎓"
            "gift", "hadiah" -> "🎁"
            "salary", "gaji" -> "💸"
            "investment", "investasi" -> "📈"
            else -> "📦"
        }
    }
}
