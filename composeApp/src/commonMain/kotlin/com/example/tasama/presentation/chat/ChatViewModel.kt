package com.example.tasama.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.ChatMessage
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.PresenceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Instant

class ChatViewModel(
    private val repository: ChatRepository,
    private val authRepository: AuthRepository,
    private val presenceRepository: PresenceRepository,
    private val notificationService: com.example.tasama.domain.service.NotificationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private var currentChannelId: String? = null
    private var messagesJob: Job? = null
    private var channelInfoJob: Job? = null
    private var typingJob: Job? = null
    private var typingStatusJob: Job? = null

    private var otherUserJob: Job? = null
    private var presenceJob: Job? = null

    private val _isResumed = MutableStateFlow(false)

    init {
        observeUserSession()
    }

    fun setResumed(resumed: Boolean) {
        _isResumed.value = resumed
        val channelId = currentChannelId
        if (resumed && channelId != null) {
            // When returning to foreground, mark all messages as read and clear notifications
            markAsRead(channelId)
            notificationService.clearNotifications(channelId)
            
            viewModelScope.launch {
                try {
                    repository.setActiveChannel(channelId)
                } catch (_: Exception) {}
            }
        } else {
            // When going to background or leaving the screen, clear active channel status
            // so new messages will trigger notifications and increment unread counts
            viewModelScope.launch {
                try {
                    repository.setActiveChannel(null)
                    currentChannelId?.let { repository.setTypingStatus(it, false) }
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
                    typingJob?.cancel()
                    typingStatusJob?.cancel()
                    otherUserJob?.cancel()
                    presenceJob?.cancel()
                    _uiState.value = ChatUiState()
                    currentChannelId = null
                    lastTypingStatusSent = null
                }
            }
        }
    }

    fun setChannel(channelId: String) {
        val oldChannelId = currentChannelId
        if (oldChannelId != null && oldChannelId != channelId) {
            viewModelScope.launch {
                try {
                    repository.setTypingStatus(oldChannelId, false)
                } catch (_: Exception) {}
            }
            lastTypingStatusSent = null
        }

        currentChannelId = channelId
        observeMessages(channelId)
        observeTypingUsers(channelId)
        
        // Only mark as read and set active channel if the app is in foreground
        if (_isResumed.value) {
            markAsRead(channelId)
            notificationService.clearNotifications(channelId)
            viewModelScope.launch {
                try {
                    repository.setActiveChannel(channelId)
                } catch (_: Exception) {}
            }
        } else {
            // If we're setting a channel but not resumed (e.g. initialization)
            // we still want to clear notifications if this was triggered by user action
            notificationService.clearNotifications(channelId)
        }
        
        loadChannelInfo(channelId)
    }

    override fun onCleared() {
        super.onCleared()
        // Clear active channel when leaving the chat
        viewModelScope.launch {
            try {
                repository.setActiveChannel(null)
                currentChannelId?.let { repository.setTypingStatus(it, false) }
            } catch (_: Exception) {}
        }
    }

    private fun observeTypingUsers(channelId: String) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            val currentUserId = repository.getCurrentUserId()
            
            combine(
                repository.getTypingUsers(channelId),
                _uiState.map { it.participantNames }.distinctUntilChanged()
            ) { typingIds, names ->
                val otherTypingIds = typingIds.filter { it != currentUserId }
                
                val typingText = when {
                    otherTypingIds.isEmpty() -> null
                    otherTypingIds.size == 1 -> {
                        val userId = otherTypingIds.first()
                        val name = names[userId] ?: "Someone"
                        "$name is typing..."
                    }
                    otherTypingIds.size == 2 -> {
                        val name1 = names[otherTypingIds.first()] ?: "Someone"
                        val name2 = names[otherTypingIds.last()] ?: "Someone"
                        "$name1 and $name2 are typing..."
                    }
                    else -> "${otherTypingIds.size} people are typing..."
                }
                
                otherTypingIds.toSet() to typingText
            }.collect { (typingUsers, typingText) ->
                _uiState.update { it.copy(
                    typingUsers = typingUsers,
                    typingIndicatorText = typingText
                ) }
            }
        }
    }

    private var lastTypingStatusSent: Boolean? = null
    private fun updateTypingStatus(isTyping: Boolean) {
        val channelId = currentChannelId ?: return
        
        typingStatusJob?.cancel()
        typingStatusJob = viewModelScope.launch {
            if (isTyping != lastTypingStatusSent) {
                try {
                    repository.setTypingStatus(channelId, isTyping)
                    lastTypingStatusSent = isTyping
                } catch (_: Exception) {}
            }
            
            if (isTyping) {
                kotlinx.coroutines.delay(2000)
                try {
                    repository.setTypingStatus(channelId, false)
                    lastTypingStatusSent = false
                } catch (_: Exception) {}
            }
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
                    
                    _uiState.update { state -> 
                        state.copy(
                            channelName = otherParticipantName,
                            participantNames = ch.participantNames
                        ) 
                    }
                    
                    val otherParticipantId = ch.participantIds.find { it != currentUserId }
                    if (otherParticipantId != null) {
                        observeOtherUserStatus(otherParticipantId)
                        observePresence(otherParticipantId)
                    }
                }
            }
        }
    }

    private fun observePresence(userId: String) {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            presenceRepository.getPresence(userId).collect { presence ->
                _uiState.update { it.copy(presence = presence) }
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
        observePresence(userId)
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

                // Mark unread messages as read ONLY if the app is currently in foreground
                if (_isResumed.value) {
                    val currentUserId = repository.getCurrentUserId()
                    messages.filter { it.userId != currentUserId && !it.readBy.containsKey(currentUserId) }.forEach { msg ->
                        repository.markMessageAsRead(channelId, msg.id)
                    }
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
        updateTypingStatus(message.isNotEmpty())
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

    fun restoreMessages(messageIds: List<String>) {
        val channelId = currentChannelId ?: return
        viewModelScope.launch {
            try {
                repository.restoreMessages(channelId, messageIds)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun getSelectedMessagesText(): String {
        val state = _uiState.value
        val selectedIds = state.selectedMessageIds
        if (selectedIds.isEmpty()) return ""

        return state.messages
            .filter { it.id in selectedIds }
            .sortedBy { it.timestamp }
            .joinToString("\n") { message ->
                val instant = Instant.fromEpochMilliseconds(message.timestamp)
                val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                val month = localDateTime.monthNumber
                val day = localDateTime.dayOfMonth
                val hour = localDateTime.hour.toString().padStart(2, '0')
                val minute = localDateTime.minute.toString().padStart(2, '0')
                
                val senderName = if (message.isFromMe) "You" else message.senderName
                "[$month/$day, $hour:$minute] $senderName: ${message.text}"
            }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setReplyingTo(message: ChatMessage?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun scrollToMessage(messageId: String) {
        _uiState.update { it.copy(scrollToMessageId = messageId) }
    }

    fun onScrollToMessageComplete() {
        _uiState.update { it.copy(scrollToMessageId = null) }
    }

    fun setHighlightedMessage(messageId: String?) {
        _uiState.update { it.copy(highlightedMessageId = messageId) }
        if (messageId != null) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(1400)
                if (_uiState.value.highlightedMessageId == messageId) {
                    _uiState.update { it.copy(highlightedMessageId = null) }
                }
            }
        }
    }

    fun jumpToMessage(messageId: String) {
        val exists = _uiState.value.messages.any { it.id == messageId }
        if (exists) {
            scrollToMessage(messageId)
            setHighlightedMessage(messageId)
        } else {
            _uiState.update { it.copy(error = "Message not found or deleted") }
        }
    }

    fun sendMessage() {
        val channelId = currentChannelId ?: return
        val trimmedMessage = _uiState.value.inputText.trim()
        if (trimmedMessage.isEmpty() || _uiState.value.isSending) return

        val replyingTo = _uiState.value.replyingToMessage

        // Lock sending immediately to prevent duplicates from fast taps
        _uiState.update { it.copy(isSending = true) }
        updateTypingStatus(false)

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    channelId = channelId,
                    text = trimmedMessage,
                    repliedMessageId = replyingTo?.id,
                    repliedMessageSenderId = replyingTo?.userId,
                    repliedMessageSenderName = if (replyingTo?.isFromMe == true) "You" else replyingTo?.senderName,
                    repliedMessageText = replyingTo?.text,
                    repliedMessageType = null, // For now, we only have text messages
                    repliedMessageTimestamp = replyingTo?.timestamp
                )
                // Clear input only after successful send
                _uiState.update {
                    it.copy(
                        isSending = false,
                        inputText = "",
                        replyingToMessage = null
                    )
                }
            } catch (e: Exception) {
                // Restore sending state on error so user can retry
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = e.message ?: "Failed to send message"
                    )
                }
            }
        }
    }

}
