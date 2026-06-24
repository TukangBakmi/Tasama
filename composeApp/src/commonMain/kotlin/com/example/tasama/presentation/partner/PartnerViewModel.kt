package com.example.tasama.presentation.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PartnerUiState(
    val partner: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLinked: Boolean = false,
    val isGuest: Boolean = false
)

class PartnerViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PartnerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeUser()
    }

    private fun observeUser() {
        viewModelScope.launch {
            authRepository.userId.collectLatest { uid ->
                if (uid != null) {
                    val isGuest = authRepository.isGuest()
                    _uiState.update { it.copy(
                        isGuest = isGuest,
                        isLoading = true,
                        partner = null,
                        isLinked = false
                    ) }
                    
                    // Initial load
                    refreshPartnerData(uid)
                    
                    // Periodic updates (automatically canceled when uid changes or is null)
                    while (true) {
                        delay(30000)
                        refreshPartnerData(uid)
                    }
                } else {
                    _uiState.value = PartnerUiState()
                }
            }
        }
    }

    fun refresh() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            refreshPartnerData(uid)
        }
    }

    private suspend fun refreshPartnerData(uid: String) {
        val user = authRepository.getUser(uid)
        if (user?.partnerId != null) {
            val partner = authRepository.getUser(user.partnerId)
            _uiState.update { it.copy(
                partner = partner,
                isLinked = true,
                isLoading = false
            ) }
        } else {
            _uiState.update { it.copy(
                partner = null,
                isLinked = false,
                isLoading = false
            ) }
        }
    }

    fun updateLocation(lat: Double, lon: Double) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.updateLocation(uid, lat, lon)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
