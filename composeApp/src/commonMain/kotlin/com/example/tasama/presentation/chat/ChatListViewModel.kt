package com.example.tasama.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState = _uiState.asStateFlow()

    val currentUserId: String?
        get() = repository.getCurrentUserId()

    init {
        loadChannels()
    }

    private fun loadChannels() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
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

    fun searchUser(userId: String) {
        if (userId.isBlank()) return
        _uiState.update { it.copy(isSearchingUser = true, error = null, searchedUser = null) }
        viewModelScope.launch {
            try {
                // Since there is no searchUser in ChatRepository, I'll use authRepository if I can, 
                // but ChatRepository has createChannelWithUser which uses authRepository.getUserName.
                // Let's assume for now we can get it via a new method in ChatRepository or just use the current logic.
                // For "real-time user name fetching", I should probably add a method to ChatRepository.
                val name = repository.getUserName(userId)
                if (name != null) {
                    _uiState.update { it.copy(searchedUser = SearchedUser(userId, name), isSearchingUser = false) }
                } else {
                    _uiState.update { it.copy(error = "User not found", isSearchingUser = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSearchingUser = false) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchedUser = null, error = null) }
    }
}
