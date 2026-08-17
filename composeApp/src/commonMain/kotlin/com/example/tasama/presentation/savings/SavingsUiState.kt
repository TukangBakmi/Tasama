package com.example.tasama.presentation.savings

import com.example.tasama.domain.model.SavingsSpace

data class SavingsUiState(
    val savingsSpaces: List<SavingsSpace> = emptyList(),
    val isLoading: Boolean = false,
    val showAddSpaceDialog: Boolean = false,
    val showInviteMemberDialog: Boolean = false,
    val showAddTransactionDialog: Boolean = false,
    val selectedSpaceId: String? = null,
    val error: String? = null
)
