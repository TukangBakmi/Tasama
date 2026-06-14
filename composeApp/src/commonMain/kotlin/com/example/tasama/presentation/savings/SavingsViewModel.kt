package com.example.tasama.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SavingsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSavings()
    }

    private fun loadSavings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Mocking savings data
            val mockGoals = listOf(
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
                    currentAmount = 8000000.0,
                    emoji = "🏥"
                )
            )
            _uiState.value = _uiState.value.copy(
                savingsGoals = mockGoals,
                isLoading = false
            )
        }
    }
}
