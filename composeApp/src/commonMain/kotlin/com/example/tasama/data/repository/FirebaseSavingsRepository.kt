package com.example.tasama.data.repository

import com.example.tasama.domain.model.*
import com.example.tasama.domain.repository.ActivityRepository
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.util.formatAmount
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

class FirebaseSavingsRepository(
    private val authRepository: AuthRepository,
    private val activityRepository: ActivityRepository
) : SavingsRepository {
    private val firestore = Firebase.firestore
    private val spacesCollection = firestore.collection("savings_spaces")
    private val invitationsCollection = firestore.collection("savings_invitations")

    private fun activitiesCollection(spaceId: String) = 
        spacesCollection.document(spaceId).collection("activities")

    override suspend fun cleanup() {
        println("DEBUG: Cleaning up FirebaseSavingsRepository")
        // SavingsRepository currently only uses flow-based snapshots which 
        // will be automatically cancelled when the collecting scope is cancelled.
    }

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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSavingsSpace(id: String): Flow<SavingsSpace?> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else {
                spacesCollection.document(id).snapshots.map { snapshot ->
                    if (snapshot.exists) snapshot.data<SavingsSpace>() else null
                }.catch { e ->
                    if (e.message?.contains("permission", ignoreCase = true) == true) {
                        println("DEBUG: [SAVINGS] Permission denied in getSavingsSpace (expected during logout)")
                    } else {
                        println("ERROR: [SAVINGS] Error in getSavingsSpace: ${e.message}")
                    }
                    emit(null)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTransactions(spaceId: String): Flow<List<SavingsTransaction>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                spacesCollection.document(spaceId).collection("transactions")
                    .orderBy("timestamp", Direction.DESCENDING)
                    .snapshots.map { snapshot ->
                        snapshot.documents.map { it.data<SavingsTransaction>() }
                    }.catch { e ->
                        if (e.message?.contains("permission", ignoreCase = true) == true) {
                            println("DEBUG: [SAVINGS] Permission denied in getTransactions (expected during logout)")
                        } else {
                            println("ERROR: [SAVINGS] Error in getTransactions: ${e.message}")
                        }
                        emit(emptyList())
                    }
            }
        }
    }

    override suspend fun createSavingsSpace(space: SavingsSpace): String {
        val uid = authRepository.getCurrentUserId() ?: throw Exception("Not authenticated")
        val user = authRepository.getUser(uid) ?: throw Exception("User not found")
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "space_$now"
        
        val members = mutableListOf<SavingsMember>()
        val memberIds = mutableListOf<String>()

        val ownerIds = mutableListOf<String>()

        // Add creator as owner/member
        memberIds.add(uid)
        ownerIds.add(uid)
        members.add(SavingsMember(
            userId = uid,
            name = user.name,
            avatarUrl = user.avatarUrl,
            role = MemberRole.OWNER,
            joinedAt = now
        ))

        // Special logic for COUPLE type
        if (space.type == SavingsSpaceType.COUPLE) {
            val partnerId = user.partnerId ?: throw Exception("Partner required for Couple Space")
            val partner = authRepository.getUser(partnerId) ?: throw Exception("Partner not found")
            
            if (!memberIds.contains(partnerId)) {
                memberIds.add(partnerId)
                ownerIds.add(partnerId)
                members.add(SavingsMember(
                    userId = partnerId,
                    name = partner.name,
                    avatarUrl = partner.avatarUrl,
                    role = MemberRole.OWNER, // Both are owners/full access
                    joinedAt = now
                ))
            }
        }

        val finalSpace = space.copy(
            id = id,
            ownerId = uid,
            ownerIds = ownerIds,
            memberIds = memberIds,
            members = members,
            createdAt = now,
            updatedAt = now
        )
        
        spacesCollection.document(id).set(finalSpace)
        logActivity(
            spaceId = id,
            userId = uid,
            userName = user.name,
            type = SavingsActivityType.SPACE_CREATED,
            details = "Space created",
            extraMetadata = mapOf("spaceName" to space.name)
        )
        return id
    }

    override suspend fun updateSavingsSpace(space: SavingsSpace) {
        val uid = authRepository.getCurrentUserId() ?: return
        val user = authRepository.getUser(uid) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        
        val oldSpace = spacesCollection.document(space.id).get().data<SavingsSpace>()
        
        spacesCollection.document(space.id).set(space.copy(updatedAt = now))
        
        if (oldSpace.targetDate != space.targetDate) {
            logActivity(
                spaceId = space.id,
                userId = uid,
                userName = user.name,
                type = SavingsActivityType.TARGET_DATE_UPDATED,
                details = "Updated target date to ${space.targetDate}",
                extraMetadata = mapOf("spaceName" to space.name)
            )
        } else {
            logActivity(
                spaceId = space.id,
                userId = uid,
                userName = user.name,
                type = SavingsActivityType.SPACE_UPDATED,
                details = "Space details updated",
                extraMetadata = mapOf("spaceName" to space.name)
            )
        }
    }

    override suspend fun deleteSavingsSpace(id: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val user = authRepository.getUser(uid) ?: return
        val spaceDoc = spacesCollection.document(id)
        
        // 1. Fetch space details to check ownership and existence
        val spaceSnapshot = try { spaceDoc.get() } catch (e: Exception) { null }
        
        if (spaceSnapshot == null || !spaceSnapshot.exists) {
            println("DEBUG: [SAVINGS] deleteSavingsSpace: Space $id not found or inaccessible")
            return
        }
        
        val space = spaceSnapshot.data<SavingsSpace>()
        val isOwner = space.members.any { it.userId == uid && it.role == MemberRole.OWNER }
        if (!isOwner) throw Exception("Only owner can delete the space")

        logActivity(
            spaceId = id,
            userId = uid,
            userName = user.name,
            type = SavingsActivityType.SPACE_DELETED,
            details = "Deleted savings space '${space.name}'",
            customTargetUids = space.memberIds,
            extraMetadata = mapOf("spaceName" to space.name)
        )
        
        println("DEBUG: [SAVINGS] deleteSavingsSpace: Initiating cleanup for Space ID: $id")
        // ... rest of the code for cleanup ...
        try {
            val statusPending = InvitationStatus.PENDING.name
            val invites = invitationsCollection.where("spaceId", id).get()
            invites.documents.forEach { doc ->
                if (try { doc.get<String>("status") } catch (e: Exception) { null } == statusPending) {
                    doc.reference.delete()
                }
            }
        } catch (e: Exception) {}

        spaceDoc.delete()
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
        
        var spaceName = "Space"
        firestore.runTransaction {
            val spaceDoc = spacesCollection.document(spaceId)
            val snapshot = get(spaceDoc)
            val space = snapshot.data<SavingsSpace>()
            spaceName = space.name
            val newBalance = if (finalTransaction.type == TransactionType.INCOME) {
                space.balance + finalTransaction.amount
            } else {
                space.balance - finalTransaction.amount
            }
            
            set(spaceDoc, space.copy(balance = newBalance, updatedAt = now))
            set(transRef, finalTransaction)
        }
        val amountStr = "Rp ${transaction.amount.formatAmount()}"
        logActivity(
            spaceId = spaceId,
            userId = uid,
            userName = userName,
            type = SavingsActivityType.TRANSACTION_ADDED,
            details = "Added contribution: ${transaction.note} • $amountStr",
            extraMetadata = mapOf("amount" to amountStr, "spaceName" to spaceName)
        )
    }

    override suspend fun updateTransaction(spaceId: String, transaction: SavingsTransaction) {
        val uid = authRepository.getCurrentUserId() ?: return
        val userName = authRepository.getUserName(uid) ?: "User"
        val now = Clock.System.now().toEpochMilliseconds()
        val transRef = spacesCollection.document(spaceId).collection("transactions").document(transaction.id)
        
        var spaceName = "Space"
        firestore.runTransaction {
            val spaceDoc = spacesCollection.document(spaceId)
            val snapshot = get(spaceDoc)
            val space = snapshot.data<SavingsSpace>()
            spaceName = space.name
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
        val amountStr = "Rp ${transaction.amount.formatAmount()}"
        logActivity(
            spaceId = spaceId,
            userId = uid,
            userName = userName,
            type = SavingsActivityType.TRANSACTION_UPDATED,
            details = "Edited contribution: ${transaction.note} • $amountStr",
            extraMetadata = mapOf("amount" to amountStr, "spaceName" to spaceName)
        )
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
        val spaceName = try {
            spacesCollection.document(spaceId).get().data<SavingsSpace>().name
        } catch (e: Exception) {
            "Space"
        }
        logActivity(
            spaceId = spaceId,
            userId = uid,
            userName = userName,
            type = SavingsActivityType.TRANSACTION_DELETED,
            details = "Deleted a contribution",
            extraMetadata = mapOf("spaceName" to spaceName)
        )
    }

    override suspend fun inviteMember(spaceId: String, inviteeId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val user = authRepository.getUser(uid) ?: return
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
            inviterName = user.name,
            inviteeId = inviteeId,
            inviteeName = invitee.name,
            status = InvitationStatus.PENDING,
            timestamp = now
        )
        
        invitationsCollection.document(invitationId).set(invitation)
        
        // Log activity: include invited user + current members in targetUids
        logActivity(
            spaceId = spaceId,
            userId = uid,
            userName = user.name,
            type = SavingsActivityType.INVITATION_SENT,
            details = "Invited ${invitee.name} to ${space.name}",
            affectedUserId = inviteeId,
            affectedUserName = invitee.name,
            customTargetUids = space.memberIds + inviteeId,
            extraMetadata = mapOf(
                "spaceName" to space.name,
                "invitationId" to invitationId
            )
        )
        
        // Send notification to invitee
        authRepository.sendNotification(
            targetUid = inviteeId,
            title = "Savings Space Invitation",
            body = "${user.name} invited you to join '${space.name}'",
            type = "SAVINGS_INVITATION",
            senderName = user.name,
            senderPhoto = user.avatarUrl
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

        var spaceName = "Space"
        successInfo?.let { (spaceId, userName) ->
            // Try to get space name. Note: this is outside the transaction but for logging it's fine.
            try {
                val space = spacesCollection.document(spaceId).get().data<SavingsSpace>()
                spaceName = space.name
            } catch (e: Exception) {}

            logActivity(
                spaceId = spaceId,
                userId = uid,
                userName = userName,
                type = SavingsActivityType.MEMBER_JOINED,
                details = "Joined the space",
                extraMetadata = mapOf(
                    "spaceName" to spaceName,
                    "invitationId" to invitationId
                )
            )
        }
    }

    override suspend fun declineInvitation(invitationId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val invRef = invitationsCollection.document(invitationId)
        val invitation = invRef.get().data<SavingsInvitation>()
        
        if (invitation.inviteeId != uid) throw Exception("Unauthorized")
        
        invRef.update("status" to InvitationStatus.DECLINED.name)
        logActivity(
            spaceId = invitation.spaceId,
            userId = uid,
            userName = authRepository.getUserName(uid) ?: "User",
            type = SavingsActivityType.INVITATION_DECLINED,
            details = "Declined invitation",
            extraMetadata = mapOf(
                "spaceName" to invitation.spaceName,
                "invitationId" to invitationId
            )
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPendingInvitations(spaceId: String): Flow<List<SavingsInvitation>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                invitationsCollection
                    .where("spaceId", spaceId)
                    .where("status", InvitationStatus.PENDING.name)
                    .snapshots.map { snapshot ->
                        snapshot.documents.map { it.data<SavingsInvitation>() }
                    }.catch { e ->
                        if (e.message?.contains("permission", ignoreCase = true) == true) {
                            println("DEBUG: [SAVINGS] Permission denied in getPendingInvitations (expected during logout)")
                        } else {
                            println("ERROR: [SAVINGS] Error in getPendingInvitations: ${e.message}")
                        }
                        emit(emptyList())
                    }
            }
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
                    }.catch { e ->
                        if (e.message?.contains("permission", ignoreCase = true) == true) {
                            println("DEBUG: [SAVINGS] Permission denied in getMyInvitations (expected during logout)")
                        } else {
                            println("ERROR: [SAVINGS] Error in getMyInvitations: ${e.message}")
                        }
                        emit(emptyList())
                    }
            }
        }
    }

    override suspend fun removeMember(spaceId: String, userId: String) {
        val currentUid = authRepository.getCurrentUserId() ?: return
        val user = authRepository.getUser(currentUid) ?: return
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        val isOwner = space.members.any { it.userId == currentUid && it.role == MemberRole.OWNER }
        if (!isOwner) throw Exception("Only owner can remove members")
        
        val targetIsOwner = space.members.any { it.userId == userId && it.role == MemberRole.OWNER }
        if (targetIsOwner) throw Exception("Owners cannot be removed")

        val removedUserName = authRepository.getUserName(userId) ?: "User"
        
        // Log activity BEFORE changing membership to ensure removed user is in targetUids
        logActivity(
            spaceId = spaceId,
            userId = currentUid,
            userName = user.name,
            type = SavingsActivityType.MEMBER_REMOVED,
            details = "Removed $removedUserName",
            affectedUserId = userId,
            affectedUserName = removedUserName,
            customTargetUids = space.memberIds, // Current members including the one being removed
            extraMetadata = mapOf("spaceName" to space.name)
        )
        
        val updatedMemberIds = space.memberIds - userId
        val updatedMembers = space.members.filter { it.userId != userId }
        
        spaceDoc.set(space.copy(
            memberIds = updatedMemberIds,
            members = updatedMembers,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        ))
    }

    override suspend fun leaveSpace(spaceId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        val isOwner = space.members.any { it.userId == uid && it.role == MemberRole.OWNER }
        if (isOwner) throw Exception("Owner must transfer ownership before leaving")
        
        val updatedMemberIds = space.memberIds - uid
        val updatedMembers = space.members.filter { it.userId != uid }
        
        spaceDoc.set(space.copy(
            memberIds = updatedMemberIds,
            members = updatedMembers,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        ))
        
        logActivity(
            spaceId = spaceId,
            userId = uid,
            userName = authRepository.getUserName(uid) ?: "User",
            type = SavingsActivityType.MEMBER_LEFT,
            details = "Left the space",
            extraMetadata = mapOf("spaceName" to space.name)
        )

        // Cleanup: If the user had a pending invitation to this space (shouldn't happen if they are already a member, 
        // but for safety), we clean it up.
        try {
            val pendingInvites = invitationsCollection
                .where("spaceId", spaceId)
                .where("inviteeId", uid)
                .where("status", InvitationStatus.PENDING.name)
                .get()
            pendingInvites.documents.forEach { it.reference.delete() }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override suspend fun transferOwnership(spaceId: String, newOwnerId: String) {
        val currentUid = authRepository.getCurrentUserId() ?: return
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        val isOwner = space.members.any { it.userId == currentUid && it.role == MemberRole.OWNER }
        if (!isOwner) throw Exception("Only owner can transfer ownership")
        if (!space.memberIds.contains(newOwnerId)) throw Exception("New owner must be a member")
        
        val updatedMembers = space.members.map { 
            when (it.userId) {
                currentUid -> it.copy(role = MemberRole.MEMBER)
                newOwnerId -> it.copy(role = MemberRole.OWNER)
                else -> it
            }
        }

        val newOwnerIds = updatedMembers.filter { it.role == MemberRole.OWNER }.map { it.userId }
        
        spaceDoc.set(space.copy(
            ownerId = newOwnerId,
            ownerIds = newOwnerIds,
            members = updatedMembers,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        ))
        
        val newOwnerName = authRepository.getUserName(newOwnerId) ?: "User"
        logActivity(
            spaceId = spaceId,
            userId = currentUid,
            userName = authRepository.getUserName(currentUid) ?: "Owner",
            type = SavingsActivityType.OWNERSHIP_TRANSFERRED,
            details = "Transferred ownership to $newOwnerName",
            affectedUserId = newOwnerId,
            affectedUserName = newOwnerName,
            extraMetadata = mapOf("spaceName" to space.name)
        )
    }

    override suspend fun convertToGroupSpace(spaceId: String) {
        val uid = authRepository.getCurrentUserId() ?: throw Exception("Not authenticated")
        val userName = authRepository.getUserName(uid) ?: "User"
        val spaceDoc = spacesCollection.document(spaceId)
        
        println("DEBUG: Convert to Group - Step 8: Firestore transaction started for spaceId: $spaceId")
        
        try {
            firestore.runTransaction {
                val spaceSnapshot = get(spaceDoc)
                if (!spaceSnapshot.exists) {
                    println("ERROR: Convert to Group - Step 9: Space document does not exist!")
                    throw Exception("Space not found")
                }
                
                val space = spaceSnapshot.data<SavingsSpace>()
                println("DEBUG: Convert to Group - Step 9: Savings Space document read successfully: ${space.name}")
                
                val isOwner = space.members.any { it.userId == uid && it.role == MemberRole.OWNER }
                if (!isOwner) {
                    println("ERROR: Convert to Group - Step 10: Validation failed. Current user $uid is not an owner")
                    throw Exception("Only owner can convert the space")
                }
                
                if (space.type != SavingsSpaceType.PERSONAL) {
                    println("ERROR: Convert to Group - Step 10: Validation failed. Space type is ${space.type}, expected PERSONAL")
                    throw Exception("Only Personal spaces can be converted to Group")
                }
                
                println("DEBUG: Convert to Group - Step 10: Validation passed")
                
                val now = Clock.System.now().toEpochMilliseconds()
                println("DEBUG: Convert to Group - Step 11: Firestore update started")
                
                update(spaceDoc, 
                    "type" to SavingsSpaceType.GROUP.name,
                    "updatedAt" to now
                )
                println("DEBUG: Convert to Group - Step 12: Type changed from PERSONAL to GROUP in transaction")
            }
        } catch (e: Exception) {
            println("ERROR: Convert to Group - Firestore transaction failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
        
        val spaceName = try {
            spacesCollection.document(spaceId).get().data<SavingsSpace>().name
        } catch (e: Exception) {
            "Space"
        }
        
        println("DEBUG: Convert to Group - Step 13: Logging activity")
        logActivity(
            spaceId = spaceId,
            userId = uid,
            userName = userName,
            type = SavingsActivityType.SPACE_UPDATED,
            details = "Converted Personal Space to Group Space",
            extraMetadata = mapOf("spaceName" to spaceName)
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getActivityHistory(spaceId: String): Flow<List<SavingsActivity>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                activitiesCollection(spaceId)
                    .orderBy("timestamp", Direction.DESCENDING)
                    .snapshots.map { snapshot ->
                        snapshot.documents.map { it.data<SavingsActivity>() }
                    }.catch { e ->
                        if (e.message?.contains("permission", ignoreCase = true) == true) {
                            println("DEBUG: [SAVINGS] Permission denied in getActivityHistory (expected during logout)")
                        } else {
                            println("ERROR: [SAVINGS] Error in getActivityHistory: ${e.message}")
                        }
                        emit(emptyList())
                    }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getGlobalActivityHistory(): Flow<List<SavingsActivity>> {
        return getSavingsSpaces().flatMapLatest { spaces ->
            if (spaces.isEmpty()) return@flatMapLatest flowOf(emptyList())
            
            val activityFlows = spaces.map { getActivityHistory(it.id) }
            combine(activityFlows) { arrays ->
                arrays.flatMap { it }.sortedByDescending { it.timestamp }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getGlobalTransactions(): Flow<List<SavingsTransaction>> {
        return getSavingsSpaces().flatMapLatest { spaces ->
            if (spaces.isEmpty()) return@flatMapLatest flowOf(emptyList())
            
            val transactionFlows = spaces.map { getTransactions(it.id) }
            combine(transactionFlows) { arrays ->
                arrays.flatMap { it }.sortedByDescending { it.timestamp }
            }
        }
    }

    override suspend fun archiveSpace(spaceId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        val isOwner = space.members.any { it.userId == uid && it.role == MemberRole.OWNER }
        if (!isOwner) throw Exception("Only owner can archive the space")
        
        spaceDoc.update("isArchived" to true)
    }

    private suspend fun logActivity(
        spaceId: String,
        userId: String,
        userName: String,
        type: SavingsActivityType,
        details: String,
        affectedUserId: String? = null,
        affectedUserName: String? = null,
        customTargetUids: List<String>? = null,
        extraMetadata: Map<String, String> = emptyMap()
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val activityId = "act_$now"
        
        // Log to unified activity repository
        val targetUids = customTargetUids ?: try {
            spacesCollection.document(spaceId).get().data<SavingsSpace>().memberIds
        } catch (e: Exception) {
            listOf(userId) // Fallback to at least the performer
        }
        
        val metadata = mutableMapOf(
            "spaceId" to spaceId,
            "targetUids" to targetUids.distinct().joinToString(",")
        )
        metadata.putAll(extraMetadata)

        activityRepository.logActivity(
            Activity(
                id = activityId,
                userId = userId,
                userName = userName,
                affectedUserId = affectedUserId,
                affectedUserName = affectedUserName,
                category = ActivityCategory.SAVINGS,
                type = type.name,
                title = when(type) {
                    SavingsActivityType.SPACE_CREATED -> "New Space"
                    SavingsActivityType.TRANSACTION_ADDED -> "Contribution Added"
                    SavingsActivityType.TRANSACTION_DELETED -> "Contribution Removed"
                    SavingsActivityType.MEMBER_JOINED -> "Member Joined"
                    SavingsActivityType.MEMBER_LEFT -> "Member Left"
                    SavingsActivityType.MEMBER_REMOVED -> "Member Removed"
                    SavingsActivityType.INVITATION_SENT -> "Member Invited"
                    SavingsActivityType.OWNERSHIP_TRANSFERRED -> "Ownership Transferred"
                    SavingsActivityType.SPACE_UPDATED -> "Space Updated"
                    SavingsActivityType.SPACE_DELETED -> "Space Deleted"
                    SavingsActivityType.TARGET_DATE_UPDATED -> "Target Date Updated"
                    else -> "Savings Update"
                },
                details = details,
                timestamp = now,
                metadata = metadata
            )
        )
    }
}
