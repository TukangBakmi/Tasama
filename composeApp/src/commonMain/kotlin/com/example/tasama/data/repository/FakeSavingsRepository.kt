package com.example.tasama.data.repository

import com.example.tasama.domain.model.Collaborator
import com.example.tasama.domain.model.SavingsGoal
import com.example.tasama.domain.repository.SavingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSavingsRepository : SavingsRepository {
    private val _goals = MutableStateFlow(
        listOf(
            SavingsGoal(
                id = "1",
                title = "New Car",
                targetAmount = 50000000.0,
                currentAmount = 15000000.0,
                emoji = "🚗",
                isShared = true,
                collaborators = listOf(
                    Collaborator("1", "You"),
                    Collaborator("2", "Wife")
                )
            ),
            SavingsGoal(
                id = "2",
                title = "Japan Trip",
                targetAmount = 30000000.0,
                currentAmount = 25000000.0,
                emoji = "🗾"
            ),
            SavingsGoal(
                id = "3",
                title = "Emergency Fund",
                targetAmount = 10000000.0,
                currentAmount = 8500000.0,
                emoji = "💰"
            )
        )
    )

    override fun getSavingsGoals(): Flow<List<SavingsGoal>> = _goals.asStateFlow()

    override suspend fun addSavingsGoal(goal: SavingsGoal) {
        _goals.update { it + goal }
    }

    override suspend fun updateSavingsGoal(goal: SavingsGoal) {
        _goals.update { list ->
            list.map { if (it.id == goal.id) goal else it }
        }
    }

    override suspend fun deleteSavingsGoal(id: String) {
        _goals.update { it.filterNot { goal -> goal.id == id } }
    }
}
