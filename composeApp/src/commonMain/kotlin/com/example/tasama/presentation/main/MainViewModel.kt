package com.example.tasama.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val isGuest: Boolean) : AuthState()
    data object Unauthenticated : AuthState()
}

class MainViewModel(
    private val authRepository: AuthRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val authState: StateFlow<AuthState> = authRepository.userId
        .flatMapLatest { uid ->
            flow {
                if (uid != null) {
                    emit(AuthState.Authenticated(isGuest = authRepository.isGuest()))
                } else {
                    emit(AuthState.Unauthenticated)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Loading)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val hasPartner: StateFlow<Boolean> = authRepository.userId
        .flatMapLatest { uid ->
            if (uid != null) {
                authRepository.getUserFlow(uid).map { it?.partnerId != null }
            } else {
                flow { emit(false) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun updateActiveStatus() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.updateLastActive(uid)
        }
    }

    fun setOffline() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            // Use negative timestamp to signify "explicitly offline" while preserving the time
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            authRepository.updateLastActive(uid, timestamp = now)
        }
    }
}
