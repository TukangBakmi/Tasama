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

class ChatViewModel(
    private val repository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private var currentChannelId: String? = null
    private var messagesJob: Job? = null
    private var channelInfoJob: Job? = null

    private var otherUserJob: Job? = null

    private val _isResumed = MutableStateFlow(false)

    init {
        observeUserSession()
    }

    fun setResumed(resumed: Boolean) {
        _isResumed.value = resumed
        val channelId = currentChannelId
        if (resumed && channelId != null) {
            markAsRead(channelId)
            viewModelScope.launch {
                try {
                    repository.setActiveChannel(channelId)
                } catch (_: Exception) {}
            }
        } else {
            viewModelScope.launch {
                try {
                    repository.setActiveChannel(null)
                } catch (_: Exception) {}
            }
        }
    }

    private fun observeUserSession() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                if (uid == null) {
                    messagesJob?.cancel()
                    channelInfoJob?.cancel()
                    otherUserJob?.cancel()
                    _uiState.value = ChatUiState()
                    currentChannelId = null
                }
            }
        }
    }

    fun setChannel(channelId: String) {
        currentChannelId = channelId
        observeMessages(channelId)
        markAsRead(channelId)
        loadChannelInfo(channelId)
        
        // Register this channel as active for the current user
        viewModelScope.launch {
            try {
                repository.setActiveChannel(channelId)
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clear active channel when leaving the chat
        viewModelScope.launch {
            try {
                repository.setActiveChannel(null)
            } catch (_: Exception) {}
        }
    }

    private fun loadChannelInfo(channelId: String) {
        channelInfoJob?.cancel()
        channelInfoJob = viewModelScope.launch {
            val currentUserId = repository.getCurrentUserId() ?: return@launch
            repository.getChannel(channelId).collect { channel ->
                channel?.let { ch ->
                    val otherParticipantName = ch.participantNames.entries
                        .find { entry -> entry.key != currentUserId }?.value ?: "Chat"
                    
                    _uiState.update { state -> state.copy(channelName = otherParticipantName) }
                    
                    val otherParticipantId = ch.participantIds.find { it != currentUserId }
                    if (otherParticipantId != null) {
                        observeOtherUserStatus(otherParticipantId)
                    }
                }
            }
        }
    }

    fun observeOtherUserStatus(userId: String) {
        otherUserJob?.cancel()
        otherUserJob = viewModelScope.launch {
            authRepository.getUserFlow(userId).collect { user ->
                _uiState.update { it.copy(otherUser = user) }
            }
        }
    }

    private fun markAsRead(channelId: String) {
        viewModelScope.launch {
            repository.markChannelAsRead(channelId)
        }
    }

    private fun observeMessages(channelId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
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
                messages.filter { it.userId != currentUserId && !it.readBy.containsKey(currentUserId) }.forEach { msg ->
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

    fun toggleMessageSelection(messageId: String) {
        _uiState.update { state ->
            val newSelected = state.selectedMessageIds.toMutableSet()
            if (newSelected.contains(messageId)) {
                newSelected.remove(messageId)
            } else {
                newSelected.add(messageId)
            }
            state.copy(
                selectedMessageIds = newSelected,
                isSelectionMode = newSelected.isNotEmpty()
            )
        }
    }

    fun enterSelectionMode(messageId: String) {
        _uiState.update { it.copy(
            isSelectionMode = true,
            selectedMessageIds = setOf(messageId)
        ) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(
            isSelectionMode = false,
            selectedMessageIds = emptySet(),
            showDeleteConfirmation = false
        ) }
    }

    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun deleteSelectedMessages() {
        val channelId = currentChannelId ?: return
        val selectedIds = _uiState.value.selectedMessageIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            try {
                repository.deleteMessages(channelId, selectedIds)
                exitSelectionMode()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun sendMessage() {
        val channelId = currentChannelId ?: return
        val messageText = _uiState.value.inputText
        if (messageText.isBlank()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(channelId, messageText)
                _uiState.update { it.copy(inputText = "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to send message") }
            }
        }
    }

}
