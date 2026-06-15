package com.example.tasama.data.repository

import com.example.tasama.domain.model.SavingsGoal
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
                collection.where { "userId" equalTo uid }.snapshots.map { snapshot ->
                    snapshot.documents.map { it.data() }
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
}
