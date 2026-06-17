package com.example.tasama.data.repository

import com.example.tasama.domain.model.SavingsGoal
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class FirebaseSavingsRepository(
    private val authRepository: AuthRepository
) : SavingsRepository {
    private val firestore = Firebase.firestore
    private val collection = firestore.collection("savings_goals")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSavingsGoals(): Flow<List<SavingsGoal>> {
        return authRepository.userId.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                collection.snapshots.map { snapshot ->
                    snapshot.documents
                        .map { it.data<SavingsGoal>() }
                        .filter { it.userId == uid || it.collaboratorIds.contains(uid) }
                }.catch { e ->
                    println("Firestore Savings Error: ${e.message}")
                    emit(emptyList())
                }
            }
        }
    }

    override suspend fun addSavingsGoal(goal: SavingsGoal) {
        val userId = authRepository.getCurrentUserId() ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        val id = if (goal.id.isEmpty()) "goal_$now" else goal.id
        val doc = collection.document(id)
        val finalGoal = goal.copy(id = id, userId = userId)
        doc.set(finalGoal)
    }

    override suspend fun updateSavingsGoal(goal: SavingsGoal) {
        val userId = authRepository.getCurrentUserId() ?: return
        if (goal.id.isNotEmpty()) {
            collection.document(goal.id).set(goal.copy(userId = userId))
        }
    }

    override suspend fun deleteSavingsGoal(id: String) {
        collection.document(id).delete()
    }

    override suspend fun inviteByEmail(goalId: String, email: String) {
        try {
            val userSnapshot = firestore.collection("users")
                .where { "email" equalTo email }
                .get()
            
            val userDoc = userSnapshot.documents.firstOrNull() ?: return
            val userIdToAdd = userDoc.id
            val userName = userDoc.data<com.example.tasama.domain.model.User>().name
            
            val goalDoc = collection.document(goalId)
            val goal = goalDoc.get().data<SavingsGoal>()
            
            if (!goal.collaboratorIds.contains(userIdToAdd)) {
                val updatedCollaborators = goal.collaboratorIds + userIdToAdd
                val updatedCollaboratorList = goal.collaborators + com.example.tasama.domain.model.Collaborator(userIdToAdd, userName)
                goalDoc.set(goal.copy(
                    collaboratorIds = updatedCollaborators, 
                    collaborators = updatedCollaboratorList,
                    isShared = true
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun contribute(goalId: String, amount: Double) {
        val uid = authRepository.getCurrentUserId() ?: return
        val userName = authRepository.getUserName(uid) ?: "User"
        
        try {
            val goalDoc = collection.document(goalId)
            val goal = goalDoc.get().data<SavingsGoal>()
            
            val newContribution = com.example.tasama.domain.model.Contribution(
                userId = uid,
                userName = userName,
                amount = amount,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
            
            val updatedContributions = goal.contributions + newContribution
            val updatedAmount = goal.currentAmount + amount
            
            goalDoc.set(goal.copy(
                currentAmount = updatedAmount,
                contributions = updatedContributions
            ))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
