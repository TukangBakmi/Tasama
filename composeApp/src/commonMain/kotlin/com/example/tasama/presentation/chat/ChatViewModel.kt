package com.example.tasama.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.MessageStatus
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

    private var currentChannelId: String? = null

    fun setChannel(channelId: String) {
        currentChannelId = channelId
        observeMessages(channelId)
        markAsRead(channelId)
        loadChannelInfo(channelId)
    }

    private fun loadChannelInfo(channelId: String) {
        viewModelScope.launch {
            val currentUserId = repository.getCurrentUserId() ?: return@launch
            repository.getChannels().collect { channels ->
                val channel = channels.find { it.id == channelId }
                channel?.let {
                    val otherParticipantName = it.participantNames.entries
                        .find { entry -> entry.key != currentUserId }?.value ?: "Chat"
                    _uiState.update { state -> state.copy(channelName = otherParticipantName) }
                }
            }
        }
    }

    private fun markAsRead(channelId: String) {
        viewModelScope.launch {
            repository.markChannelAsRead(channelId)
        }
    }

    private fun observeMessages(channelId: String) {
        viewModelScope.launch {
            repository.getMessages(channelId).collect { messages ->
                _uiState.update { state ->
                    val latestOldestTimestamp = messages.firstOrNull()?.timestamp ?: 0L
                    val pagedMessages = state.messages.filter { it.timestamp < latestOldestTimestamp }
                    
                    val combined = (pagedMessages + messages)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                    state.copy(messages = combined)
                }

                // Mark unread messages as read
                val currentUserId = repository.getCurrentUserId()
                messages.filter { it.userId != currentUserId && it.status != MessageStatus.READ }.forEach { msg ->
                    repository.markMessageAsRead(channelId, msg.id)
                }
            }
        }
    }

    fun loadMoreMessages() {
        val channelId = currentChannelId ?: return
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreMessages) return

        val oldestMessage = _uiState.value.messages.firstOrNull() ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            
            val moreMessages = repository.getMoreMessages(
                channelId = channelId,
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
        val channelId = currentChannelId ?: return
        val messageText = _uiState.value.inputText
        if (messageText.isBlank()) return

        viewModelScope.launch {
            repository.sendMessage(channelId, messageText)
            _uiState.update { it.copy(inputText = "") }
        }
    }

    fun createChannel(otherUserId: String) {
        viewModelScope.launch {
            val channelId = repository.createChannelWithUser(otherUserId)
            setChannel(channelId)
        }
    }
}
