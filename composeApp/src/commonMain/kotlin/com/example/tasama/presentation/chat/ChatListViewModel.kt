package com.example.tasama.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.model.ChatChannel
import com.example.tasama.domain.model.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val repository: ChatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState = _uiState.asStateFlow()

    private var dataJob: Job? = null
    private var usersJob: Job? = null
    private var contactsJob: Job? = null

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
                    usersJob?.cancel()
                    contactsJob?.cancel()
                    _uiState.value = ChatListUiState()
                } else {
                    loadChannels()
                    loadContacts(uid)
                }
            }
        }
    }

    private fun loadChannels() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getChannels().collectLatest { channels ->
                _uiState.update { it.copy(channels = channels, isLoading = false) }
                observeUsersStatus(channels)
            }
        }
    }

    private fun loadContacts(uid: String) {
        contactsJob?.cancel()
        contactsJob = viewModelScope.launch {
            authRepository.getUserFlow(uid).collectLatest { user ->
                val contactIds = user?.contactIds ?: emptyList()
                if (contactIds.isEmpty()) {
                    _uiState.update { it.copy(contacts = emptyList()) }
                    observeUsersStatus(uiState.value.channels)
                } else {
                    val contactFlows = contactIds.map { authRepository.getUserFlow(it) }
                    combine(contactFlows) { contacts ->
                        contacts.filterNotNull()
                    }.collect { contactsList ->
                        _uiState.update { it.copy(contacts = contactsList) }
                        observeUsersStatus(uiState.value.channels)
                    }
                }
            }
        }
    }

    private fun observeUsersStatus(channels: List<ChatChannel>) {
        usersJob?.cancel()
        
        val channelUserIds = channels.flatMap { it.participantIds }
            .filter { it != currentUserId }
            .toSet()
            
        val contactIds = uiState.value.contacts.map { it.id }.toSet()
        
        val allOtherUserIds = (channelUserIds + contactIds).distinct()

        if (allOtherUserIds.isEmpty()) return

        usersJob = viewModelScope.launch {
            val userFlows = allOtherUserIds.map { uid ->
                authRepository.getUserFlow(uid)
            }

            combine(userFlows) { users ->
                users.filterNotNull().associateBy { it.id }
            }.collect { usersMap ->
                _uiState.update { it.copy(channelUsers = usersMap) }
            }
        }
    }

    fun createChannel(otherUserId: String, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val channelId = repository.createChannelWithUser(otherUserId)
                val currentUid = currentUserId
                if (currentUid != null) {
                    authRepository.addContact(currentUid, otherUserId)
                }
                _uiState.update { it.copy(searchedUser = null) }
                onResult(channelId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
                onResult(null)
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
