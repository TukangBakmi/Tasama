package com.example.tasama.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.SavingsTransaction
import com.example.tasama.domain.model.TransactionType
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
            repository.getSavingsSpaces().collect { spaces ->
                _uiState.update { 
                    it.copy(
                        savingsSpaces = spaces,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addSpace(space: SavingsSpace) {
        viewModelScope.launch {
            try {
                repository.createSavingsSpace(space)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create space") }
            }
        }
    }

    fun updateSpace(space: SavingsSpace) {
        viewModelScope.launch {
            try {
                repository.updateSavingsSpace(space)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update space") }
            }
        }
    }

    fun deleteSpace(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteSavingsSpace(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete space") }
            }
        }
    }

    fun onAddSpaceClick() {
        _uiState.update { it.copy(showAddSpaceDialog = true) }
    }

    fun onDismissAddSpace() {
        _uiState.update { it.copy(showAddSpaceDialog = false) }
    }

    fun onInviteClick(spaceId: String) {
        _uiState.update { it.copy(showInviteMemberDialog = true, selectedSpaceId = spaceId) }
    }

    fun onDismissInvite() {
        _uiState.update { it.copy(showInviteMemberDialog = false, selectedSpaceId = null) }
    }

    fun inviteMember(email: String) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        viewModelScope.launch {
            try {
                repository.inviteMember(spaceId, email)
                onDismissInvite()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to invite member") }
            }
        }
    }

    fun onAddTransactionClick(spaceId: String) {
        _uiState.update { it.copy(showAddTransactionDialog = true, selectedSpaceId = spaceId) }
    }

    fun onDismissAddTransaction() {
        _uiState.update { it.copy(showAddTransactionDialog = false, selectedSpaceId = null) }
    }

    fun addTransaction(amount: Double, type: TransactionType, note: String) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        val userId = authRepository.getCurrentUserId() ?: ""
        
        viewModelScope.launch {
            try {
                repository.addTransaction(
                    spaceId = spaceId,
                    transaction = SavingsTransaction(
                        spaceId = spaceId,
                        userId = userId,
                        amount = amount,
                        type = type,
                        note = note,
                        timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    )
                )
                onDismissAddTransaction()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to add transaction") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
