package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SavingsGoal(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val emoji: String = "💰",
    val isShared: Boolean = false,
    val collaboratorIds: List<String> = emptyList(),
    val collaborators: List<Collaborator> = emptyList(),
    val contributions: List<Contribution> = emptyList()
)

@Serializable
data class Collaborator(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String? = null
)

@Serializable
data class Contribution(
    val userId: String = "",
    val userName: String = "",
    val amount: Double = 0.0,
    val timestamp: Long = 0
)
