package com.example.tasama.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val repository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState = _uiState.asStateFlow()

    private var dataJob: Job? = null

    val currentUserId: String?
        get() = repository.getCurrentUserId()

    init {
        observeUserSession()
    }

    private fun observeUserSession() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                if (uid == null) {
                    dataJob?.cancel()
                    _uiState.value = ChatListUiState()
                } else {
                    loadChannels()
                }
            }
        }
    }

    private fun loadChannels() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getChannels().collect { channels ->
                _uiState.update { it.copy(channels = channels, isLoading = false) }
            }
        }
    }

    fun createChannel(otherUserId: String) {
        viewModelScope.launch {
            try {
                repository.createChannelWithUser(otherUserId)
                _uiState.update { it.copy(searchedUser = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun searchUser(query: String) {
        if (query.isBlank()) return
        val currentUid = repository.getCurrentUserId()
        _uiState.update { it.copy(isSearchingUser = true, error = null, searchedUser = null) }
        viewModelScope.launch {
            try {
                val userId = if (query.length == 12 && query.all { it.isDigit() }) {
                    repository.getUserIdFromShortId(query)
                } else {
                    query // Assume it might be a full UID for now, or just fallback
                }

                if (userId != null) {
                    if (userId == currentUid) {
                        _uiState.update { it.copy(error = "You cannot chat with yourself", isSearchingUser = false) }
                        return@launch
                    }
                    val name = repository.getUserName(userId)
                    if (name != null) {
                        _uiState.update { it.copy(searchedUser = SearchedUser(userId, name), isSearchingUser = false) }
                    } else {
                        _uiState.update { it.copy(error = "User not found", isSearchingUser = false) }
                    }
                } else {
                    _uiState.update { it.copy(error = "User not found", isSearchingUser = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSearchingUser = false) }
            }
        }
    }

    fun deleteChannel(channelId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChannel(channelId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchedUser = null, error = null) }
    }
}
