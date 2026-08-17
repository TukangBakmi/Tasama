package com.example.tasama.data.repository

import com.example.tasama.domain.model.*
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.Direction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

class FirebaseSavingsRepository(
    private val authRepository: AuthRepository
) : SavingsRepository {
    private val firestore = Firebase.firestore
    private val spacesCollection = firestore.collection("savings_spaces")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSavingsSpaces(): Flow<List<SavingsSpace>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                spacesCollection.snapshots.map { snapshot ->
                    snapshot.documents
                        .map { it.data<SavingsSpace>() }
                        .filter { it.memberIds.contains(uid) }
                }.catch { e ->
                    println("Firestore Savings Error: ${e.message}")
                    emit(emptyList())
                }
            }
        }
    }

    override fun getSavingsSpace(id: String): Flow<SavingsSpace?> {
        return spacesCollection.document(id).snapshots.map { it.data<SavingsSpace>() }
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
            role = MemberRole.OWNER
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
        return id
    }

    override suspend fun updateSavingsSpace(space: SavingsSpace) {
        val now = Clock.System.now().toEpochMilliseconds()
        spacesCollection.document(space.id).set(space.copy(updatedAt = now))
    }

    override suspend fun deleteSavingsSpace(id: String) {
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
    }

    override suspend fun updateTransaction(spaceId: String, transaction: SavingsTransaction) {
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
            
            set(spaceDoc, space.copy(balance = newBalance, updatedAt = now))
            set(transRef, transaction)
        }
    }

    override suspend fun deleteTransaction(spaceId: String, transactionId: String) {
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
    }

    override suspend fun inviteMember(spaceId: String, email: String) {
        val userSnapshot = firestore.collection("users")
            .where { "email" equalTo email }
            .get()
        
        val userDoc = userSnapshot.documents.firstOrNull() ?: throw Exception("User not found")
        val userToAdd = userDoc.data<User>()
        
        val spaceDoc = spacesCollection.document(spaceId)
        val space = spaceDoc.get().data<SavingsSpace>()
        
        if (!space.memberIds.contains(userToAdd.id)) {
            val updatedMemberIds = space.memberIds + userToAdd.id
            val updatedMembers = space.members + SavingsMember(
                userId = userToAdd.id,
                name = userToAdd.name,
                avatarUrl = userToAdd.avatarUrl,
                role = MemberRole.MEMBER
            )
            spaceDoc.set(space.copy(
                memberIds = updatedMemberIds,
                members = updatedMembers,
                updatedAt = Clock.System.now().toEpochMilliseconds()
            ))
        }
    }
}
