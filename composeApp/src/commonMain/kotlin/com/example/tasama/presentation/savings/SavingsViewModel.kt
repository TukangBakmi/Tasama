package com.example.tasama.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.SavingsGoal
import com.example.tasama.domain.repository.SavingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SavingsViewModel(
    private val repository: SavingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSavings()
    }

    private fun loadSavings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getSavingsGoals().collect { goals ->
                _uiState.update { 
                    it.copy(
                        savingsGoals = goals,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.addSavingsGoal(goal)
        }
    }

    fun updateGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.updateSavingsGoal(goal)
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(id)
        }
    }

    fun onAddGoalClick() {
        _uiState.update { it.copy(showAddGoalDialog = true) }
    }

    fun onDismissAddGoal() {
        _uiState.update { it.copy(showAddGoalDialog = false) }
    }

    fun onInviteClick(goalId: String) {
        _uiState.update { it.copy(showInviteCollaboratorDialog = true, selectedGoalId = goalId) }
    }

    fun onDismissInvite() {
        _uiState.update { it.copy(showInviteCollaboratorDialog = false, selectedGoalId = null) }
    }

    fun inviteCollaborator(email: String) {
        val goalId = _uiState.value.selectedGoalId ?: return
        viewModelScope.launch {
            repository.inviteByEmail(goalId, email)
            onDismissInvite()
        }
    }

    fun onContributeClick(goalId: String) {
        _uiState.update { it.copy(showContributeDialog = true, selectedGoalId = goalId) }
    }

    fun onDismissContribute() {
        _uiState.update { it.copy(showContributeDialog = false, selectedGoalId = null) }
    }

    fun contribute(amount: Double) {
        val goalId = _uiState.value.selectedGoalId ?: return
        viewModelScope.launch {
            repository.contribute(goalId, amount)
            onDismissContribute()
        }
    }
}
