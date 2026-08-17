package com.example.tasama.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SavingsSpaceType {
    PERSONAL, COUPLE, GROUP
}

@Serializable
enum class MemberRole {
    OWNER, MEMBER
}

@Serializable
data class SavingsMember(
    val userId: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val role: MemberRole = MemberRole.MEMBER
)

@Serializable
data class SavingsSpace(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val icon: String = "💰",
    val type: SavingsSpaceType = SavingsSpaceType.PERSONAL,
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    val members: List<SavingsMember> = emptyList(),
    val targetAmount: Double? = null,
    val balance: Double = 0.0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class SavingsTransaction(
    val id: String = "",
    val spaceId: String = "",
    val userId: String = "",
    val userName: String = "",
    val amount: Double = 0.0,
    val type: TransactionType = TransactionType.INCOME,
    val note: String = "",
    val timestamp: Long = 0
)
