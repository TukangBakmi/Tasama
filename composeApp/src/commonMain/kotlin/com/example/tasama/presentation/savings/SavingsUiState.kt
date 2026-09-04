package com.example.tasama.presentation.savings

import com.example.tasama.domain.model.*

data class SavingsUiState(
    val savingsSpaces: List<SavingsSpace> = emptyList(),
    val isLoading: Boolean = false,
    val showAddSpaceDialog: Boolean = false,
    val showInviteMemberDialog: Boolean = false,
    val showAddTransactionDialog: Boolean = false,
    val showSpaceDetails: Boolean = false,
    val selectedSpaceId: String? = null,
    val transactions: List<SavingsTransaction> = emptyList(),
    val pendingInvitations: List<SavingsInvitation> = emptyList(),
    val myInvitations: List<SavingsInvitation> = emptyList(),
    val activityHistory: List<SavingsActivity> = emptyList(),
    
    val searchQuery: String = "",
    val searchedUser: User? = null,
    val contacts: List<User> = emptyList(),
    val filteredContacts: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val userCurrency: String = "IDR",
    val currentUser: User? = null,
    val showRemovedFromSpaceDialog: Boolean = false,
    val hasLeftSpace: Boolean = false,
    val showConvertToGroupDialog: Boolean = false,
    val selectedMember: SavingsMember? = null
) {
    val selectedSpace: SavingsSpace? = savingsSpaces.find { it.id == selectedSpaceId }
}
