package com.example.tasama.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.Activity
import com.example.tasama.domain.model.ActivityCategory
import com.example.tasama.domain.repository.ActivityRepository
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val savingsActivities: List<Activity> = emptyList(),
    val partnerActivities: List<Activity> = emptyList(),
    val hasUnreadSavings: Boolean = false,
    val hasUnreadPartner: Boolean = false,
    val selectedTab: Int = 0, // 0 for Savings, 1 for Partner
    val currentUserId: String? = null
)

class NotificationsViewModel(
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
    private val savingsRepository: SavingsRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    
    val uiState: StateFlow<NotificationsUiState> = combine(
        activityRepository.getActivities(ActivityCategory.SAVINGS),
        activityRepository.getActivities(ActivityCategory.PARTNER),
        activityRepository.hasUnread(ActivityCategory.SAVINGS),
        activityRepository.hasUnread(ActivityCategory.PARTNER),
        _selectedTab,
        authRepository.userId
    ) { flows ->
        val savings = flows[0] as List<Activity>
        val partner = flows[1] as List<Activity>
        val unreadSavings = flows[2] as Boolean
        val unreadPartner = flows[3] as Boolean
        val tab = flows[4] as Int
        val uid = flows[5] as String?

        NotificationsUiState(
            savingsActivities = savings,
            partnerActivities = partner,
            hasUnreadSavings = unreadSavings,
            hasUnreadPartner = unreadPartner,
            selectedTab = tab,
            currentUserId = uid
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationsUiState())

    fun onTabSelected(index: Int) {
        val previousTab = _selectedTab.value
        _selectedTab.value = index
        
        // When switching away from a tab, mark its items as read
        if (previousTab != index) {
            markTabAsRead(previousTab)
        }
    }

    fun onScreenLeft() {
        // Mark current tab as read when leaving the screen
        markTabAsRead(_selectedTab.value)
    }

    private fun markTabAsRead(tabIndex: Int) {
        val category = if (tabIndex == 0) ActivityCategory.SAVINGS else ActivityCategory.PARTNER
        viewModelScope.launch {
            activityRepository.markAllAsRead(category)
        }
    }

    fun onActivityClicked(activity: Activity, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            val currentUid = authRepository.getCurrentUserId() ?: return@launch

            // Mark as read when clicked
            activityRepository.markAsRead(listOf(activity.id))

            when (activity.category) {
                ActivityCategory.SAVINGS -> {
                    val spaceId = activity.metadata["spaceId"] ?: return@launch
                    
                    // Requirement 1: Member Invited -> Navigate to Savings main/tab screen
                    // Only for the invitee to allow them to accept.
                    if (activity.type == "INVITATION_SENT" && activity.affectedUserId == currentUid) {
                        try {
                            val invitations = savingsRepository.getMyInvitations().first()
                            val isStillPending = invitations.any { it.spaceId == spaceId }
                            if (isStillPending) {
                                onNavigate("tabs/savings")
                                return@launch
                            }
                        } catch (e: Exception) {
                            // Fall through to membership check
                        }
                    }

                    try {
                        // Requirement 2 & 4: Open detail if member
                        // This handles accepted invitations and other savings notifications.
                        val space = savingsRepository.getSavingsSpace(spaceId).first()
                        val isMember = space != null && space.memberIds.contains(currentUid)

                        if (isMember) {
                            onNavigate("savings_detail/$spaceId")
                        } else {
                            // Requirement 3: Invalid/Declined/Removed -> Stay on Notifications
                            // The notification was already marked as read above.
                        }
                    } catch (e: Exception) {
                        // Space no longer accessible -> Stay on Notifications
                    }
                }
                ActivityCategory.PARTNER -> {
                    // Navigate to Partner tab
                    onNavigate("tabs/partner")
                }
                else -> {}
            }
        }
    }
}
