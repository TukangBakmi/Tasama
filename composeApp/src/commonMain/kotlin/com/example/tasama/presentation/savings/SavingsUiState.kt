package com.example.tasama.presentation.savings

import com.example.tasama.domain.model.SavingsGoal

data class SavingsUiState(
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val isLoading: Boolean = false,
    val showAddGoalDialog: Boolean = false,
    val showInviteCollaboratorDialog: Boolean = false,
    val showContributeDialog: Boolean = false,
    val selectedGoalId: String? = null
)
