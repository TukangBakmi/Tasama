package com.example.tasama.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.SavingsGoal
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SavingsViewModel(
    private val repository: SavingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState = _uiState.asStateFlow()

    private var dataJob: Job? = null

    init {
        observeUserSession()
    }

    private fun observeUserSession() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                if (uid == null) {
                    dataJob?.cancel()
                    _uiState.value = SavingsUiState()
                } else {
                    loadSavings()
                }
            }
        }
    }

    private fun loadSavings() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
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
            try {
                repository.addSavingsGoal(goal)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to add goal") }
            }
        }
    }

    fun updateGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            try {
                repository.updateSavingsGoal(goal)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update goal") }
            }
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteSavingsGoal(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete goal") }
            }
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
            try {
                repository.inviteByEmail(goalId, email)
                onDismissInvite()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to invite collaborator") }
            }
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
            try {
                repository.contribute(goalId, amount)
                onDismissContribute()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to add contribution") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
