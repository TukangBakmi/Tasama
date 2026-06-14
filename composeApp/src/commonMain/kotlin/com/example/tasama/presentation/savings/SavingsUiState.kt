package com.example.tasama.presentation.savings

data class SavingsUiState(
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val isLoading: Boolean = false
)

data class SavingsGoal(
    val id: String,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val emoji: String,
    val isShared: Boolean = false,
    val collaborators: List<Collaborator> = emptyList()
)

data class Collaborator(
    val id: String,
    val name: String,
    val avatarUrl: String? = null
)
