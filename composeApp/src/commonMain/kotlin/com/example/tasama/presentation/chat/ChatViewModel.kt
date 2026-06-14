package com.example.tasama.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeMessages()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getMessages().collect { messages ->
                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onMessageChange(message: String) {
        _uiState.update { it.copy(currentMessage = message) }
    }

    fun sendMessage() {
        val messageText = _uiState.value.currentMessage
        if (messageText.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(messageText)
            _uiState.update { it.copy(currentMessage = "") }
        }
    }
}
