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
            repository.getMessages().collect { messages ->
                _uiState.update { state ->
                    // Merge logic: Keep paged messages (older than the latest batch)
                    // but replace the latest batch with the new data from the flow
                    val latestOldestTimestamp = messages.firstOrNull()?.timestamp ?: 0L
                    val pagedMessages = state.messages.filter { it.timestamp < latestOldestTimestamp }
                    
                    val combined = (pagedMessages + messages)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                    state.copy(messages = combined)
                }
            }
        }
    }

    fun loadMoreMessages() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMessages) return

        val oldestMessage = _uiState.value.messages.firstOrNull() ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            
            val moreMessages = repository.getMoreMessages(
                limit = 20,
                beforeTimestamp = oldestMessage.timestamp
            )
            
            if (moreMessages.isEmpty()) {
                _uiState.update { it.copy(isLoadingMore = false, hasMoreMessages = false) }
            } else {
                _uiState.update { state ->
                    val combined = (moreMessages + state.messages)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                    state.copy(
                        messages = combined,
                        isLoadingMore = false,
                        hasMoreMessages = moreMessages.size >= 20
                    )
                }
            }
        }
    }

    fun onMessageChange(message: String) {
        _uiState.update { it.copy(inputText = message) }
    }

    fun sendMessage() {
        val messageText = _uiState.value.inputText
        if (messageText.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(messageText)
            _uiState.update { it.copy(inputText = "") }
        }
    }
}
