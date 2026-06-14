package com.example.tasama.presentation.savings

import com.example.tasama.domain.model.SavingsGoal

data class SavingsUiState(
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val isLoading: Boolean = false
)
