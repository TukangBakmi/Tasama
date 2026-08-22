package com.example.tasama.data.repository

import com.example.tasama.domain.model.*
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

class FirebaseSavingsRepository(
    private val authRepository: AuthRepository
) : SavingsRepository {
    private val firestore = Firebase.firestore
    private val spacesCollection = firestore.collection("savings_spaces")
    private val invitationsCollection = firestore.collection("savings_invitations")

    private fun activitiesCollection(spaceId: String) = 
        spacesCollection.document(spaceId).collection("activities")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSavingsSpaces(): Flow<List<SavingsSpace>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                spacesCollection
                    .where(FieldPath("memberIds"), arrayContains = uid)
                    .snapshots
                    .map { snapshot ->
                        snapshot.documents
                            .map { it.data<SavingsSpace>() }
                            .filter { !it.isArchived }
                    }.catch { e ->
                        println("Firestore Savings Error: ${e.message}")
                        emit(emptyList())
                    }
            }
        }
    }

    override fun getSavingsSpace(id: String): Flow<SavingsSpace?> {
        return spacesCollection.document(id).snapshots.map { snapshot ->
            if (snapshot.exists) snapshot.data<SavingsSpace>() else null
        }
    }

    override fun getTransactions(spaceId: String): Flow<List<SavingsTransaction>> {
        return spacesCollection.document(spaceId).collection("transactions")
            .orderBy("timestamp", Direction.DESCENDING)
            .snapshots.map { snapshot ->
                snapshot.documents.map { it.data<SavingsTransaction>() }
            }
    }

    override suspend fun createSavingsSpace(space: SavingsSpace): String {
        val uid = authRepository.getCurrentUserId() ?: throw Exception("Not authenticated")
        val user = authRepository.getUser(uid) ?: throw Exception("User not found")
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "space_$now"
        
        val initialMember = SavingsMember(
            userId = uid,
            name = user.name,
            avatarUrl = user.avatarUrl,
            role = MemberRole.OWNER,
            joinedAt = now
        )

        val finalSpace = space.copy(
            id = id,
            ownerId = uid,
            memberIds = listOf(uid),
            members = listOf(initialMember),
            createdAt = now,
            updatedAt = now
        )
        
        spacesCollection.document(id).set(finalSpace)
        logActivity(id, uid, user.name, SavingsActivityType.SPACE_CREATED, "Space created")
        return id
    }

    override suspend fun updateSavingsSpace(space: SavingsSpace) {
        val uid = authRepository.getCurrentUserId() ?: return
        val userName = authRepository.getUserName(uid) ?: "User"
        val now = Clock.System.now().toEpochMilliseconds()
        spacesCollection.document(space.id).set(space.copy(updatedAt = now))
        logActivity(space.id, uid, userName, SavingsActivityType.SPACE_UPDATED, "Space details updated")
    }

    override suspend fun deleteSavingsSpace(id: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val space = spacesCollection.document(id).get().data<SavingsSpace>()
        
        if (space.ownerId != uid) throw Exception("Only owner can delete the space")
        
        spacesCollection.document(id).delete()
    }

    override suspend fun addTransaction(spaceId: String, transaction: SavingsTransaction) {
        val uid = authRepository.getCurrentUserId() ?: return
        val userName = authRepository.getUserName(uid) ?: "User"
        val now = Clock.System.now().toEpochMilliseconds()
        
        val transactionId = transaction.id.ifEmpty { "tx_$now" }
        val transRef = spacesCollection.document(spaceId).collection("transactions").document(transactionId)
        
        val finalTransaction = transaction.copy(
            id = transactionId,
            spaceId = spaceId,
            userId = uid,
            userName = userName,
            timestamp = now
        )
        
        firestore.runTransaction {
            val spaceDoc = spacesCollection.document(spaceId)
            val snapshot = get(spaceDoc)
            val space = snapshot.data<SavingsSpace>()
            val newBalance = if (finalTransaction.type == TransactionType.INCOME) {
                space.balance + finalTransaction.amount
            } else {
                space.balance - finalTransaction.amount
            }
            
            set(spaceDoc, space.copy(balance = newBalance, updatedAt = now))
            set(transRef, finalTransaction)
        }
        logActivity(spaceId, uid, userName, SavingsActivityType.TRANSACTION_ADDED, "Added transaction: ${transaction.note}")
    }

    override suspend fun updateTransaction(spaceId: String, transaction: SavingsTransaction) {
        val uid = authRepository.getCurrentUserId() ?: return
        val userName = authRepository.getUserName(uid) ?: "User"
        val now = Clock.System.now().toEpochMilliseconds()
        val transRef = spacesCollection.document(spaceId).collection("transactions").document(transaction.id)
        
        firestore.runTransaction {
            val spaceDoc = spacesCollection.document(spaceId)
            val snapshot = get(spaceDoc)
            val space = snapshot.data<SavingsSpace>()
            val oldTransaction = get(transRef).data<SavingsTransaction>()
            
            // Reverse old balance impact
            val intermediateBalance = if (oldTransaction.type == TransactionType.INCOME) {
                space.balance - oldTransaction.amount
            } else {
                space.balance + oldTransaction.amount
            }
            
            // Apply new balance impact
            val newBalance = if (transaction.type == TransactionType.INCOME) {
                intermediateBalance + transaction.amount
            } else {
                intermediateBalance - transaction.amount
            }
            
            set(spaceDoc, space.copy(balance = newBalance, updatedAt = now, currency = space.currency))
            set(transRef, transaction.copy(currency = space.currency))
        }
    }

    override suspend fun deleteTransaction(spaceId: String, transactionId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val userName = authRepository.getUserName(uid) ?: "User"
        val now = Clock.System.now().toEpochMilliseconds()
        val transRef = spacesCollection.document(spaceId).collection("transactions").document(transactionId)
        
        firestore.runTransaction {
            val spaceDoc = spacesCollection.document(spaceId)
            val snapshot = get(spaceDoc)
            val space = snapshot.data<SavingsSpace>()
            val transaction = get(transRef).data<SavingsTransaction>()
            
            val newBalance = if (transaction.type == TransactionType.INCOME) {
                space.balance - transaction.amount
            } else {
                space.balance + transaction.amount
            }
            
            set(spaceDoc, space.copy(balance = newBalance, updatedAt = now))
            delete(transRef)
        }
        logActivity(spaceId, uid, userName, SavingsActivityType.TRANSACTION_DELETED, "Deleted a transaction")
    }

    override suspend fun inviteMember(spaceId: String, inviteeId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val userName = authRepository.getUserName(uid) ?: "User"
        val invitee = authRepository.getUser(inviteeId) ?: throw Exception("Invitee not found")
        val space = spacesCollection.document(spaceId).get().data<SavingsSpace>()
        
        if (space.memberIds.contains(inviteeId)) throw Exception("User is already a member")
        
        // Check for existing pending invitation
        val existing = invitationsCollection
            .where("spaceId", spaceId)
            .where("inviteeId", inviteeId)
            .where("status", InvitationStatus.PENDING.name)
            .get()
        
        if (existing.documents.isNotEmpty()) throw Exception("Invitation already pending")
        
        val now = Clock.System.now().toEpochMilliseconds()
        val invitationId = "inv_$now"
        val invitation = SavingsInvitation(
            id = invitationId,
            spaceId = spaceId,
            spaceName = space.name,
            inviterId = uid,
            inviterName = userName,
            inviteeId = inviteeId,
            inviteeName = invitee.name,
            status = InvitationStatus.PENDING,
            timestamp = now
        )
        
        invitationsCollection.document(invitationId).set(invitation)
        logActivity(spaceId, uid, userName, SavingsActivityType.INVITATION_SENT, "Invited ${invitee.name}")
        
        // Send notification to invitee
        authRepository.sendNotification(
            targetUid = inviteeId,
            title = "Savings Space Invitation",
            body = "$userName invited you to join '${space.name}'",
            type = "SAVINGS_INVITATION",
            senderName = userName,
            senderPhoto = authRepository.getUser(uid)?.avatarUrl
        )
    }

    override suspend fun cancelInvitation(invitationId: String) {
        invitationsCollection.document(invitationId).update("status" to InvitationStatus.CANCELLED.name)
    }

    override suspend fun acceptInvitation(invitationId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        
        val invRef = invitationsCollection.document(invitationId)
        var successInfo: Pair<String, String>? = null
        
        firestore.runTransaction {
            // 1. READ PHASE: All reads must be executed before any writes
            val invitation = get(invRef).data<SavingsInvitation>()
            
            if (invitation.inviteeId != uid) throw Exception("Unauthorized")
            // Prevent redundant processing if already handled
            if (invitation.status != InvitationStatus.PENDING) return@runTransaction

            val spaceDoc = spacesCollection.document(invitation.spaceId)
            val space = get(spaceDoc).data<SavingsSpace>()
            
            val inviteeRef = firestore.collection("users").document(uid)
            val inviteeUser = get(inviteeRef).data<User>()
            
            val inviterRef = firestore.collection("users").document(invitation.inviterId)
            val inviterUser = get(inviterRef).data<User>()

            // 2. WRITE PHASE: All writes must be executed after all reads
            val now = Clock.System.now().toEpochMilliseconds()
            
            // Mark invitation as ACCEPTED
            update(invRef, "status" to InvitationStatus.ACCEPTED.name)
            
            // Add member to Savings Space
            if (!space.memberIds.contains(uid)) {
                val updatedMemberIds = space.memberIds + uid
                val updatedMembers = space.members + SavingsMember(
                    userId = uid,
                    name = inviteeUser.name,
                    avatarUrl = inviteeUser.avatarUrl,
                    role = MemberRole.MEMBER,
                    joinedAt = now
                )
                
                set(spaceDoc, space.copy(
                    memberIds = updatedMemberIds,
                    members = updatedMembers,
                    updatedAt = now
                ))
            }

            // Add inviter to invitee's contacts (User B adds User A)
            if (!inviteeUser.contactIds.contains(invitation.inviterId)) {
                update(inviteeRef, "contactIds" to inviteeUser.contactIds + invitation.inviterId)
            }
            
            // Add invitee to inviter's contacts (User A adds User B - mutual)
            if (!inviterUser.contactIds.contains(uid)) {
                update(inviterRef, "contactIds" to inviterUser.contactIds + uid)
            }
            
            successInfo = invitation.spaceId to inviteeUser.name
        }

        successInfo?.let { (spaceId, userName) ->
            logActivity(spaceId, uid, userName, SavingsActivityType.INVITATION_ACCEPTED, "Joined the space")
        }
    }

    override suspend fun declineInvitation(invitationId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val invRef = invitationsCollection.document(invitationId)
        val invitation = invRef.get().data<SavingsInvitation>()
        
        if (invitation.inviteeId != uid) throw Exception("Unauthorized")
        
        invRef.update("status" to InvitationStatus.DECLINED.name)
        logActivity(invitation.spaceId, uid, authRepository.getUserName(uid) ?: "User", SavingsActivityType.INVITATION_DECLINED, "Declined invitation")
    }

    override fun getPendingInvitations(spaceId: String): Flow<List<SavingsInvitation>> {
        return invitationsCollection
            .where("spaceId", spaceId)
            .where("status", InvitationStatus.PENDING.name)
            .snapshots.map { snapshot ->
                snapshot.documents.map { it.data<SavingsInvitation>() }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMyInvitations(): Flow<List<SavingsInvitation>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                invitationsCollection
                    .where("inviteeId", uid)
                    .where("status", InvitationStatus.PENDING.name)
                    .snapshots.map { snapshot ->
                        snapshot.documents.map { it.data<SavingsInvitation>() }
                    }
            }
        }
    }

    override suspend fun removeMember(spaceId: String, userId: String) {
        val currentUid = authRepository.getCurrentUserId() ?: return
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        if (space.ownerId != currentUid) throw Exception("Only owner can remove members")
        if (userId == space.ownerId) throw Exception("Owner cannot be removed")
        
        val updatedMemberIds = space.memberIds - userId
        val updatedMembers = space.members.filter { it.userId != userId }
        
        spaceDoc.set(space.copy(
            memberIds = updatedMemberIds,
            members = updatedMembers,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        ))
        
        val removedUserName = authRepository.getUserName(userId) ?: "User"
        logActivity(spaceId, currentUid, authRepository.getUserName(currentUid) ?: "Owner", SavingsActivityType.MEMBER_REMOVED, "Removed $removedUserName")
    }

    override suspend fun leaveSpace(spaceId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        if (space.ownerId == uid) throw Exception("Owner must transfer ownership before leaving")
        
        val updatedMemberIds = space.memberIds - uid
        val updatedMembers = space.members.filter { it.userId != uid }
        
        spaceDoc.set(space.copy(
            memberIds = updatedMemberIds,
            members = updatedMembers,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        ))
        
        logActivity(spaceId, uid, authRepository.getUserName(uid) ?: "User", SavingsActivityType.MEMBER_LEFT, "Left the space")
    }

    override suspend fun transferOwnership(spaceId: String, newOwnerId: String) {
        val currentUid = authRepository.getCurrentUserId() ?: return
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        if (space.ownerId != currentUid) throw Exception("Only owner can transfer ownership")
        if (!space.memberIds.contains(newOwnerId)) throw Exception("New owner must be a member")
        
        val updatedMembers = space.members.map { 
            when (it.userId) {
                currentUid -> it.copy(role = MemberRole.MEMBER)
                newOwnerId -> it.copy(role = MemberRole.OWNER)
                else -> it
            }
        }
        
        spaceDoc.set(space.copy(
            ownerId = newOwnerId,
            members = updatedMembers,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        ))
        
        val newOwnerName = authRepository.getUserName(newOwnerId) ?: "User"
        logActivity(spaceId, currentUid, authRepository.getUserName(currentUid) ?: "Owner", SavingsActivityType.OWNERSHIP_TRANSFERRED, "Transferred ownership to $newOwnerName")
    }

    override fun getActivityHistory(spaceId: String): Flow<List<SavingsActivity>> {
        return activitiesCollection(spaceId)
            .orderBy("timestamp", Direction.DESCENDING)
            .snapshots.map { snapshot ->
                snapshot.documents.map { it.data<SavingsActivity>() }
            }
    }

    override suspend fun archiveSpace(spaceId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        if (space.ownerId != uid) throw Exception("Only owner can archive the space")
        
        spaceDoc.update("isArchived" to true)
    }

    private suspend fun logActivity(
        spaceId: String, 
        userId: String, 
        userName: String, 
        type: SavingsActivityType, 
        details: String
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val activityId = "act_$now"
        val activity = SavingsActivity(
            id = activityId,
            spaceId = spaceId,
            userId = userId,
            userName = userName,
            type = type,
            details = details,
            timestamp = now
        )
        activitiesCollection(spaceId).document(activityId).set(activity)
    }
}
