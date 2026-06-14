package com.example.tasama.domain.model

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
