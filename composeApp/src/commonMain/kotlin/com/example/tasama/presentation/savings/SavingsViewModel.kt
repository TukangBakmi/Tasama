package com.example.tasama.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.SavingsSpace
import com.example.tasama.domain.model.SavingsTransaction
import com.example.tasama.domain.model.TransactionType
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SavingsViewModel(
    private val repository: SavingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState = _uiState.asStateFlow()

    private var dataJob: Job? = null
    private var lastTxJob: Job? = null

    init {
        observeUserSession()
    }

    private fun observeUserSession() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                if (uid == null) {
                    dataJob?.cancel()
                    _uiState.value = SavingsUiState()
                } else {
                    loadSavings()
                    loadContacts(uid)
                }
            }
        }
    }

    private fun loadContacts(uid: String) {
        viewModelScope.launch {
            val user = authRepository.getUser(uid)
            val contacts = user?.contactIds?.mapNotNull { authRepository.getUser(it) } ?: emptyList()
            _uiState.update { 
                it.copy(
                    contacts = contacts,
                    filteredContacts = getRankedContacts(contacts)
                ) 
            }
        }
    }

    private fun getRankedContacts(contacts: List<com.example.tasama.domain.model.User>): List<com.example.tasama.domain.model.User> {
        // Simple ranking for savings: alphabetical or could be by common space participation
        return contacts.sortedBy { it.name }
    }

    private fun loadSavings() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getSavingsSpaces().collect { spaces ->
                _uiState.update { 
                    it.copy(
                        savingsSpaces = spaces,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addSpace(space: SavingsSpace) {
        viewModelScope.launch {
            try {
                repository.createSavingsSpace(space)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create space") }
            }
        }
    }

    fun updateSpace(space: SavingsSpace) {
        viewModelScope.launch {
            try {
                repository.updateSavingsSpace(space)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update space") }
            }
        }
    }

    fun deleteSpace(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteSavingsSpace(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete space") }
            }
        }
    }

    fun onAddSpaceClick() {
        _uiState.update { it.copy(showAddSpaceDialog = true) }
    }

    fun onDismissAddSpace() {
        _uiState.update { it.copy(showAddSpaceDialog = false) }
    }

    fun onInviteClick(spaceId: String) {
        _uiState.update { 
            it.copy(
                showInviteMemberDialog = true, 
                selectedSpaceId = spaceId,
                searchQuery = "",
                searchedUser = null
            ) 
        }
    }

    fun onDismissInvite() {
        _uiState.update { 
            it.copy(
                showInviteMemberDialog = false, 
                selectedSpaceId = null,
                searchQuery = "",
                searchedUser = null
            ) 
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        val isNumericId = query.length == 12 && query.all { it.isDigit() }
        
        // Filter contacts by name in real-time
        val filtered = uiState.value.contacts.filter { 
            it.name.contains(query, ignoreCase = true) || it.shortId == query 
        }
        _uiState.update { 
            it.copy(
                filteredContacts = if (query.isEmpty()) getRankedContacts(uiState.value.contacts) else filtered
            ) 
        }

        if (isNumericId) {
            searchUser(query)
        } else {
            _uiState.update { it.copy(searchedUser = null) }
        }
    }

    private fun searchUser(shortId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val userId = authRepository.getUserIdFromShortId(shortId)
                if (userId != null) {
                    val user = authRepository.getUser(userId)
                    _uiState.update { it.copy(searchedUser = user, isSearching = false) }
                } else {
                    _uiState.update { it.copy(searchedUser = null, isSearching = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, error = "Search failed") }
            }
        }
    }

    fun inviteMember(userId: String) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        val currentUserId = authRepository.getCurrentUserId()
        
        if (userId == currentUserId) {
            _uiState.update { it.copy(error = "You cannot invite yourself") }
            return
        }

        val space = _uiState.value.savingsSpaces.find { it.id == spaceId }
        if (space?.memberIds?.contains(userId) == true) {
            _uiState.update { it.copy(error = "User is already a member") }
            return
        }

        viewModelScope.launch {
            try {
                repository.inviteMember(spaceId, userId)
                onDismissInvite()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to invite member") }
            }
        }
    }

    fun onAddTransactionClick(spaceId: String) {
        _uiState.update { it.copy(showAddTransactionDialog = true, selectedSpaceId = spaceId) }
        observeLastTransaction(spaceId)
    }

    private fun observeLastTransaction(spaceId: String) {
        lastTxJob?.cancel()
        lastTxJob = viewModelScope.launch {
            repository.getTransactions(spaceId).collect { transactions ->
                _uiState.update { it.copy(lastTransaction = transactions.firstOrNull()) }
            }
        }
    }

    fun onDismissAddTransaction() {
        _uiState.update { it.copy(showAddTransactionDialog = false, selectedSpaceId = null, lastTransaction = null) }
        lastTxJob?.cancel()
    }

    fun undoLastTransaction(spaceId: String) {
        val lastTx = _uiState.value.lastTransaction ?: return
        viewModelScope.launch {
            try {
                repository.deleteTransaction(spaceId, lastTx.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to undo transaction") }
            }
        }
    }

    fun addTransaction(amount: Double, type: TransactionType, note: String) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        val userId = authRepository.getCurrentUserId() ?: ""
        
        viewModelScope.launch {
            try {
                repository.addTransaction(
                    spaceId = spaceId,
                    transaction = SavingsTransaction(
                        spaceId = spaceId,
                        userId = userId,
                        amount = amount,
                        type = type,
                        note = note,
                        timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    )
                )
                onDismissAddTransaction()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to add transaction") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
