package com.example.tasama.domain.repository

import com.example.tasama.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

interface SavingsRepository {
    fun getSavingsGoals(): Flow<List<SavingsGoal>>
    suspend fun addSavingsGoal(goal: SavingsGoal)
    suspend fun updateSavingsGoal(goal: SavingsGoal)
    suspend fun deleteSavingsGoal(id: String)
}
