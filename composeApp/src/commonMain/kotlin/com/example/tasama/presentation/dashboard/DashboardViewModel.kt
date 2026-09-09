package com.example.tasama.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.InvitationStatus
import com.example.tasama.domain.model.SavingsActivity
import com.example.tasama.domain.model.SavingsActivityType
import com.example.tasama.domain.model.SavingsInvitation
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.domain.repository.TransactionRepository
import com.example.tasama.util.formatAmount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val userFlow = authRepository.userId.flatMapLatest { uid ->
                if (uid != null) authRepository.getUserFlow(uid) else kotlinx.coroutines.flow.flowOf(null)
            }

            combine(
                repository.getTransactionsFlow(),
                savingsRepository.getSavingsSpaces(),
                savingsRepository.getMyInvitations(),
                savingsRepository.getGlobalActivityHistory(),
                chatRepository.getChannels(),
                userFlow
            ) { args: Array<Any?> ->
                val transactions = args[0] as List<Transaction>
                val spaces = args[1] as List<SavingsSpace>
                val invitations = args[2] as List<SavingsInvitation>
                val activities = args[3] as List<SavingsActivity>
                val channels = args[4] as List<ChatChannel>
                val user = args[5] as User?

                updateDashboardWith(transactions, spaces, invitations, activities, channels, user)
                _uiState.update { it.copy(isLoading = false) }
            }.collect { }
        }
    }

    private fun updateDashboardWith(
        transactions: List<Transaction>,
        spaces: List<SavingsSpace>,
        invitations: List<SavingsInvitation>,
        savingsActivities: List<SavingsActivity>,
        channels: List<ChatChannel>,
        user: User?
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
        val hasPendingPartnerRequest = user?.partnerRequestFrom != null

        val currentUid = authRepository.getCurrentUserId()

        // Combine activities
        val activities = savingsActivities.map { act ->
            DashboardActivity(
                id = act.id,
                title = when (act.type) {
                    SavingsActivityType.TRANSACTION_ADDED -> "Contribution Added"
                    SavingsActivityType.TRANSACTION_UPDATED -> "Contribution Edited"
                    SavingsActivityType.TRANSACTION_DELETED -> "Contribution Removed"
                    SavingsActivityType.SPACE_CREATED -> "Space Created"
                    SavingsActivityType.SPACE_UPDATED -> "Space Updated"
                    SavingsActivityType.INVITATION_SENT -> "Invitation Sent"
                    SavingsActivityType.INVITATION_ACCEPTED -> "New Member Joined"
                    SavingsActivityType.INVITATION_DECLINED -> "Invitation Declined"
                    SavingsActivityType.MEMBER_JOINED -> "Member Joined"
                    SavingsActivityType.MEMBER_LEFT -> "Member Left"
                    SavingsActivityType.MEMBER_REMOVED -> "Member Removed"
                    SavingsActivityType.OWNERSHIP_TRANSFERRED -> "Ownership Transferred"
                },
                description = act.details,
                icon = getSavingsActivityIcon(act.type),
                timestamp = act.timestamp,
                type = DashboardActivityType.SAVINGS
            )
        }

        val sortedActivities = activities.sortedByDescending { it.timestamp }.take(10)
        val hasUnread = pendingInvitations.isNotEmpty() || 
                       hasPendingPartnerRequest ||
                       channels.any { (it.unreadCounts[currentUid] ?: 0) > 0 }

        _uiState.update { it.copy(
            balance = income - expense,
            income = income,
            expense = expense,
            transactions = transactions.sortedByDescending { it.createdAt }.take(5),
            totalSavingsBalance = totalSavingsBalance,
            recentSavingsSpaces = recentSpaces,
            pendingInvitations = pendingInvitations,
            hasPendingPartnerRequest = hasPendingPartnerRequest,
            recentActivities = sortedActivities,
            hasUnreadNotifications = hasUnread
        ) }
    }

    private fun getSavingsActivityIcon(type: SavingsActivityType): String {
        return when (type) {
            SavingsActivityType.TRANSACTION_ADDED -> "📥"
            SavingsActivityType.TRANSACTION_UPDATED -> "📝"
            SavingsActivityType.TRANSACTION_DELETED -> "🗑️"
            SavingsActivityType.SPACE_CREATED -> "✨"
            SavingsActivityType.SPACE_UPDATED -> "⚙️"
            SavingsActivityType.INVITATION_SENT -> "✉️"
            SavingsActivityType.INVITATION_ACCEPTED -> "🤝"
            SavingsActivityType.INVITATION_DECLINED -> "❌"
            SavingsActivityType.MEMBER_JOINED -> "👤"
            SavingsActivityType.MEMBER_LEFT -> "🚪"
            SavingsActivityType.MEMBER_REMOVED -> "🚫"
            SavingsActivityType.OWNERSHIP_TRANSFERRED -> "👑"
        }
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
