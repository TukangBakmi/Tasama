package com.example.tasama.presentation.savings

import com.example.tasama.domain.model.SavingsSpace

data class SavingsUiState(
    val savingsSpaces: List<SavingsSpace> = emptyList(),
    val isLoading: Boolean = false,
    val showAddSpaceDialog: Boolean = false,
    val showInviteMemberDialog: Boolean = false,
    val showAddTransactionDialog: Boolean = false,
    val selectedSpaceId: String? = null,
    val lastTransaction: com.example.tasama.domain.model.SavingsTransaction? = null,
    val searchQuery: String = "",
    val searchedUser: com.example.tasama.domain.model.User? = null,
    val contacts: List<com.example.tasama.domain.model.User> = emptyList(),
    val filteredContacts: List<com.example.tasama.domain.model.User> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)
