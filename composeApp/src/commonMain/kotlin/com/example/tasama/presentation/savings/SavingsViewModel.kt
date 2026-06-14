package com.example.tasama.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
}
