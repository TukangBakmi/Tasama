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
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = 0
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
    val targetDate: Long? = null,
    val balance: Double = 0.0,
    val isArchived: Boolean = false,
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

@Serializable
enum class InvitationStatus {
    PENDING, ACCEPTED, DECLINED, CANCELLED
}

@Serializable
data class SavingsInvitation(
    val id: String = "",
    val spaceId: String = "",
    val spaceName: String = "",
    val inviterId: String = "",
    val inviterName: String = "",
    val inviteeId: String = "",
    val status: InvitationStatus = InvitationStatus.PENDING,
    val timestamp: Long = 0
)

@Serializable
enum class SavingsActivityType {
    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_REMOVED,
    INVITATION_SENT,
    INVITATION_ACCEPTED,
    INVITATION_DECLINED,
    TRANSACTION_ADDED,
    TRANSACTION_DELETED,
    SPACE_CREATED,
    SPACE_UPDATED,
    OWNERSHIP_TRANSFERRED
}

@Serializable
data class SavingsActivity(
    val id: String = "",
    val spaceId: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: SavingsActivityType,
    val details: String = "",
    val timestamp: Long = 0
)
