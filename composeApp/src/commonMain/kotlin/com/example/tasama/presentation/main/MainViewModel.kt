package com.example.tasama.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.PresenceRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.domain.service.GeofenceMonitor
import com.example.tasama.presentation.components.TransientFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val isGuest: Boolean) : AuthState()
    data object Unauthenticated : AuthState()
}

class MainViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val savingsRepository: SavingsRepository,
    private val presenceRepository: PresenceRepository,
    private val geofenceMonitor: GeofenceMonitor,
    private val aiChatRepository: com.example.tasama.domain.repository.AIChatRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    init {
        observeAuthState()
    }

    fun setForeground(isForeground: Boolean) {
        val uid = authRepository.getCurrentUserId()
        if (uid != null) {
            presenceRepository.setForeground(uid, isForeground)
        }
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.userId.collectLatest { uid ->
                if (uid != null) {
                    presenceRepository.startMonitoring(uid)
                    geofenceMonitor.startMonitoring()
                } else {
                    presenceRepository.stopMonitoring()
                }
            }
        }
    }

    val unreadChannelsCount: StateFlow<Int> = combine(
        authRepository.userId,
        chatRepository.getChannels()
    ) { userId, channels ->
        if (userId == null) 0
        else {
            channels.count { channel ->
                (channel.unreadCounts[userId] ?: 0) > 0
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val authState: StateFlow<AuthState> = authRepository.userId
        .mapLatest { uid ->
            if (uid != null) {
                AuthState.Authenticated(isGuest = authRepository.isGuest())
            } else {
                AuthState.Unauthenticated
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.Loading
        )

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val hasPendingPartnerRequest: StateFlow<Boolean> = authRepository.userId
        .flatMapLatest { uid ->
            if (uid != null) {
                authRepository.getUserFlow(uid).map { it?.partnerRequestFrom != null }
            } else {
                flow { emit(false) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasPendingSavingsInvitations: StateFlow<Boolean> = savingsRepository.getMyInvitations()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _transientFeedback = MutableStateFlow<TransientFeedback?>(null)
    val transientFeedback: StateFlow<TransientFeedback?> = _transientFeedback.asStateFlow()

    private var feedbackJob: Job? = null

    fun showTransientFeedback(feedback: TransientFeedback) {
        feedbackJob?.cancel()
        _transientFeedback.value = feedback
        feedbackJob = viewModelScope.launch {
            delay(3000)
            _transientFeedback.value = null
        }
    }

    fun hideTransientFeedback() {
        feedbackJob?.cancel()
        _transientFeedback.value = null
    }

    fun restoreMessages(feedback: TransientFeedback.UndoDelete) {
        viewModelScope.launch {
            try {
                if (feedback.channelId != null) {
                    // Regular Chat
                    chatRepository.restoreMessages(feedback.channelId, feedback.messageIds)
                } else {
                    // AI Chat
                    if (feedback.messages.isNotEmpty()) {
                        aiChatRepository.restoreMessages(feedback.messages)
                    }
                }
                hideTransientFeedback()
            } catch (_: Exception) {
            }
        }
    }
}
