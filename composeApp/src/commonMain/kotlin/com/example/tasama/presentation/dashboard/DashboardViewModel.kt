package com.example.tasama.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.InvitationStatus
import com.example.tasama.domain.model.SavingsActivity
import com.example.tasama.domain.model.SavingsActivityType
import com.example.tasama.domain.model.SavingsInvitation
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.SavingsTransaction
import com.example.tasama.domain.model.Transaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.ActivityRepository
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class DashboardViewModel(
    private val repository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val savingsRepository: SavingsRepository,
    private val chatRepository: ChatRepository,
    private val activityRepository: ActivityRepository,
    private val settingsRepository: SettingsRepository
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
                savingsRepository.getGlobalTransactions(),
                chatRepository.getChannels(),
                userFlow,
                settingsRepository.settings,
                activityRepository.hasUnread()
            ) { args: Array<Any?> ->
                val transactions = args[0] as List<Transaction>
                val spaces = args[1] as List<SavingsSpace>
                val invitations = args[2] as List<SavingsInvitation>
                val activities = args[3] as List<SavingsActivity>
                val savingsTransactions = args[4] as List<SavingsTransaction>
                val channels = args[5] as List<ChatChannel>
                val user = args[6] as User?
                val settings = args[7] as com.example.tasama.domain.model.AppSettings
                val hasUnifiedUnread = args[8] as Boolean

                updateDashboardWith(transactions, spaces, invitations, activities, savingsTransactions, channels, user, settings, hasUnifiedUnread)
                _uiState.update { it.copy(isLoading = false) }
            }.collect { }
        }
    }

    private fun updateDashboardWith(
        transactions: List<Transaction>,
        spaces: List<SavingsSpace>,
        invitations: List<SavingsInvitation>,
        savingsActivities: List<SavingsActivity>,
        savingsTransactions: List<SavingsTransaction>,
        channels: List<ChatChannel>,
        user: User?,
        settings: com.example.tasama.domain.model.AppSettings,
        hasUnifiedUnread: Boolean
    ) {
        val currentSpaceId = _uiState.value.selectedSpaceId
        val currentPeriod = _uiState.value.selectedPeriod

        // Filter transactions based on selection
        val filteredTransactions = if (currentSpaceId == null) {
            savingsTransactions
        } else {
            savingsTransactions.filter { it.spaceId == currentSpaceId }
        }

        val periodFiltered = filterByPeriod(filteredTransactions, currentPeriod)
        
        val income = periodFiltered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = periodFiltered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        val totalSavingsBalance = spaces.sumOf { it.balance }
        val recentSpaces = spaces.sortedByDescending { it.updatedAt }.take(2)
        val pendingInvitations = invitations.filter { it.status == InvitationStatus.PENDING }
        val hasPendingPartnerRequest = user?.partnerRequestFrom != null

        val currentUid = authRepository.getCurrentUserId()

        val hasUnread = hasUnifiedUnread || 
                       pendingInvitations.isNotEmpty() ||
                       hasPendingPartnerRequest ||
                       channels.any { (it.unreadCounts[currentUid] ?: 0) > 0 }

        _uiState.update { it.copy(
            recentSavingsSpaces = spaces, // Update all spaces for the filter
            financialSummary = FinancialSummary(income, expense, income - expense),
            trendChartData = calculateTrendData(filteredTransactions, currentPeriod),
            totalSavingsBalance = totalSavingsBalance,
            pendingInvitations = pendingInvitations,
            hasPendingPartnerRequest = hasPendingPartnerRequest,
            hasUnreadNotifications = hasUnread,
            currency = settings.currency
        ) }
    }

    private fun filterByPeriod(transactions: List<SavingsTransaction>, period: FinancialPeriod): List<SavingsTransaction> {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date
        
        return transactions.filter {
            val date = Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
            when (period) {
                FinancialPeriod.THIS_WEEK -> {
                    val daysSinceMonday = (date.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
                    val monday = today.minus(daysSinceMonday, DateTimeUnit.DAY)
                    date >= monday
                }
                FinancialPeriod.THIS_MONTH -> date.month == today.month && date.year == today.year
                FinancialPeriod.LAST_MONTH -> {
                    val lastMonthDate = today.minus(1, DateTimeUnit.MONTH)
                    date.month == lastMonthDate.month && date.year == lastMonthDate.year
                }
                FinancialPeriod.LAST_3_MONTHS -> {
                    val threeMonthsAgo = today.minus(3, DateTimeUnit.MONTH)
                    date >= threeMonthsAgo
                }
                FinancialPeriod.THIS_YEAR -> date.year == today.year
            }
        }
    }

    private fun calculateTrendData(transactions: List<SavingsTransaction>, period: FinancialPeriod): List<MonthlyTrend> {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = now.date

        return when (period) {
            FinancialPeriod.THIS_WEEK -> {
                val daysSinceMonday = (today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
                val monday = today.minus(daysSinceMonday, DateTimeUnit.DAY)
                (0..6).map { i ->
                    val date = monday.plus(i, DateTimeUnit.DAY)
                    val dayTransactions = transactions.filter {
                        Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date == date
                    }
                    MonthlyTrend(
                        label = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        income = dayTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                        expense = dayTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    )
                }
            }
            FinancialPeriod.THIS_MONTH -> {
                val daysInMonth = 30 // Simplified or could use Month.length(leapYear)
                (0 until 6).map { i ->
                    val startDay = i * 5 + 1
                    val endDay = (startDay + 4).coerceAtMost(daysInMonth)
                    val periodTransactions = transactions.filter {
                        val d = Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
                        d.month == today.month && d.year == today.year && d.day in startDay..endDay
                    }
                    MonthlyTrend(
                        label = "$startDay", // Start day of the bracket
                        income = periodTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                        expense = periodTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    )
                }
            }
            FinancialPeriod.LAST_MONTH -> {
                val lastMonthDate = today.minus(1, DateTimeUnit.MONTH)
                val daysInMonth = 30
                (0 until 6).map { i ->
                    val startDay = i * 5 + 1
                    val endDay = (startDay + 4).coerceAtMost(daysInMonth)
                    val periodTransactions = transactions.filter {
                        val d = Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
                        d.month == lastMonthDate.month && d.year == lastMonthDate.year && d.day in startDay..endDay
                    }
                    MonthlyTrend(
                        label = "$startDay",
                        income = periodTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                        expense = periodTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    )
                }
            }
            FinancialPeriod.LAST_3_MONTHS -> {
                (0..2).reversed().map { i ->
                    val monthDate = today.minus(i, DateTimeUnit.MONTH)
                    val monthTransactions = transactions.filter {
                        val d = Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
                        d.month == monthDate.month && d.year == monthDate.year
                    }
                    MonthlyTrend(
                        label = monthDate.month.name.take(3),
                        income = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                        expense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    )
                }
            }
            FinancialPeriod.THIS_YEAR -> {
                (1..12).map { monthIdx ->
                    val month = Month(monthIdx)
                    val monthTransactions = transactions.filter {
                        val d = Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
                        d.month == month && d.year == today.year
                    }
                    MonthlyTrend(
                        label = month.name.take(3),
                        income = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                        expense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    )
                }
            }
        }
    }

    fun onSpaceFilterSelected(spaceId: String?) {
        _uiState.update { it.copy(selectedSpaceId = spaceId) }
        // The observeData combine will naturally pick this up if it references _uiState,
        // but here it doesn't. We need to manually refresh or make combine react to selection.
        refreshData()
    }

    fun onPeriodFilterSelected(period: FinancialPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        refreshData()
    }

    private fun refreshData() {
        // We can just call observeData again or use a StateFlow for filters and combine it.
        // For simplicity, let's just trigger a data update.
        observeData()
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

}
