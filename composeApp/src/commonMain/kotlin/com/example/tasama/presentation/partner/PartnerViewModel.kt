package com.example.tasama.presentation.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
            authRepository.userId.collect { uid ->
                if (uid != null) {
                    val isGuest = authRepository.isGuest()
                    _uiState.update { it.copy(isGuest = isGuest) }
                    loadPartnerData(uid)
                    startLocationUpdates(uid)
                } else {
                    _uiState.value = PartnerUiState()
                }
            }
        }
    }

    private fun loadPartnerData(uid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
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
                    isLinked = false,
                    isLoading = false
                ) }
            }
        }
    }

    private fun startLocationUpdates(uid: String) {
        viewModelScope.launch {
            while (true) {
                val user = authRepository.getUser(uid)
                if (user?.partnerId != null) {
                    val partner = authRepository.getUser(user.partnerId)
                    _uiState.update { it.copy(partner = partner) }
                } else {
                    _uiState.update { it.copy(partner = null, isLinked = false) }
                }
                delay(30000) // Update every 30 seconds
            }
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
}
