package com.example.tasama.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.*
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.presentation.components.TransientFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SavingsViewModel(
    private val repository: SavingsRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: com.example.tasama.domain.repository.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState = _uiState.asStateFlow()

    private var dataJob: Job? = null
    private var detailsJob: Job? = null
    private var transactionJob: Job? = null
    private var invitationJob: Job? = null
    private var activityJob: Job? = null
    private var myInvitationsJob: Job? = null

    init {
        observeUserSession()
    }

    private fun observeUserSession() {
        viewModelScope.launch {
            authRepository.userId.collect { uid ->
                if (uid == null) {
                    cancelAllJobs()
                    _uiState.value = SavingsUiState()
                } else {
                    loadSavings()
                    loadContacts(uid)
                    loadMyInvitations()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(userCurrency = settings.currency) }
            }
        }
    }

    private fun cancelAllJobs() {
        dataJob?.cancel()
        detailsJob?.cancel()
        transactionJob?.cancel()
        invitationJob?.cancel()
        activityJob?.cancel()
        myInvitationsJob?.cancel()
    }

    private fun loadContacts(uid: String) {
        viewModelScope.launch {
            val user = authRepository.getUser(uid)
            _uiState.update { it.copy(currentUser = user) }
            val contacts = user?.contactIds?.mapNotNull { authRepository.getUser(it) } ?: emptyList()
            _uiState.update { 
                it.copy(
                    contacts = contacts,
                    filteredContacts = getRankedContacts(contacts)
                ) 
            }
        }
    }

    private fun getRankedContacts(contacts: List<User>): List<User> {
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

    private fun loadMyInvitations() {
        myInvitationsJob?.cancel()
        myInvitationsJob = viewModelScope.launch {
            repository.getMyInvitations().collect { invitations ->
                _uiState.update { it.copy(myInvitations = invitations) }
            }
        }
    }

    fun onSpaceClick(spaceId: String) {
        _uiState.update { it.copy(selectedSpaceId = spaceId) }
    }

    fun onSpaceHandled() {
        _uiState.update { it.copy(selectedSpaceId = null) }
    }

    fun loadSpaceDetails(spaceId: String) {
        _uiState.update { it.copy(selectedSpaceId = spaceId, showSpaceDetails = true) }
        detailsJob?.cancel()
        transactionJob?.cancel()
        invitationJob?.cancel()
        activityJob?.cancel()

        val currentUid = authRepository.getCurrentUserId()

        detailsJob = viewModelScope.launch {
            repository.getSavingsSpace(spaceId).collect { space ->
                if (space == null || (currentUid != null && !space.memberIds.contains(currentUid))) {
                    if (_uiState.value.showSpaceDetails && _uiState.value.selectedSpaceId == spaceId) {
                        // Only show the removed dialog if the user didn't leave/delete voluntarily
                        if (!_uiState.value.hasLeftSpace) {
                            _uiState.update { it.copy(showRemovedFromSpaceDialog = true) }
                        }

                        // Clear details but keep selectedSpaceId for the dialog to know which one was lost
                        // Actually, we need to stop further updates
                        detailsJob?.cancel()
                        transactionJob?.cancel()
                        invitationJob?.cancel()
                        activityJob?.cancel()
                    }
                } else {
                    _uiState.update { state ->
                        val updatedSpaces = state.savingsSpaces.toMutableList()
                        val index = updatedSpaces.indexOfFirst { it.id == space.id }
                        if (index != -1) {
                            updatedSpaces[index] = space
                        } else {
                            updatedSpaces.add(space)
                        }
                        state.copy(savingsSpaces = updatedSpaces)
                    }
                }
            }
        }

        transactionJob = viewModelScope.launch {
            repository.getTransactions(spaceId).collect { txs ->
                _uiState.update { it.copy(transactions = txs) }
            }
        }

        invitationJob = viewModelScope.launch {
            repository.getPendingInvitations(spaceId).collect { invs ->
                _uiState.update { it.copy(pendingInvitations = invs) }
            }
        }

        activityJob = viewModelScope.launch {
            repository.getActivityHistory(spaceId).collect { activities ->
                _uiState.update { it.copy(activityHistory = activities) }
            }
        }
    }

    fun onDismissSpaceDetails() {
        _uiState.update { it.copy(showSpaceDetails = false, selectedSpaceId = null, showRemovedFromSpaceDialog = false, hasLeftSpace = false) }
        detailsJob?.cancel()
        transactionJob?.cancel()
        invitationJob?.cancel()
        activityJob?.cancel()
    }

    fun onRemovedDialogConfirm() {
        onDismissSpaceDetails()
    }

    fun addSpace(space: SavingsSpace) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepository.getCurrentUserId() ?: return@launch
                val memberIds = if (space.type == SavingsSpaceType.COUPLE) {
                    val user = authRepository.getUser(currentUserId)
                    val partnerId = user?.partnerId
                    if (partnerId != null) {
                        listOf(currentUserId, partnerId)
                    } else {
                        listOf(currentUserId)
                    }
                } else {
                    listOf(currentUserId)
                }

                repository.createSavingsSpace(
                    space.copy(
                        name = space.name.trim(),
                        description = space.description.trim(),
                        ownerId = currentUserId,
                        memberIds = memberIds
                    )
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to create space") }
            }
        }
    }

    fun updateSpace(space: SavingsSpace) {
        viewModelScope.launch {
            try {
                repository.updateSavingsSpace(
                    space.copy(
                        name = space.name.trim(),
                        description = space.description.trim()
                    )
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to update space") }
            }
        }
    }

    fun deleteSpace(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteSavingsSpace(id)
                _uiState.update { it.copy(hasLeftSpace = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete space") }
            }
        }
    }

    fun archiveSpace(id: String) {
        viewModelScope.launch {
            try {
                repository.archiveSpace(id)
                onDismissSpaceDetails()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to archive space") }
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
                searchQuery = "",
                searchedUser = null
            ) 
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        val isNumericId = query.length == 12 && query.all { it.isDigit() }
        
        // Filter contacts by name or shortId
        val filtered = uiState.value.contacts.filter { 
            it.name.contains(query, ignoreCase = true) || it.shortId.contains(query)
        }
        _uiState.update { 
            it.copy(
                filteredContacts = if (query.isEmpty()) getRankedContacts(uiState.value.contacts) else filtered
            ) 
        }

        // Only search globally if it's a full 12-digit ID and not already in filtered contacts
        if (isNumericId) {
            val alreadyInContacts = filtered.any { it.shortId == query }
            if (!alreadyInContacts) {
                searchUser(query)
            } else {
                _uiState.update { it.copy(searchedUser = null) }
            }
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

    fun inviteMember(userId: String, onFeedback: (TransientFeedback) -> Unit = {}) {
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
                
                // Add to contacts if not already there
                if (currentUserId != null) {
                    val isAlreadyContact = _uiState.value.contacts.any { it.id == userId }
                    if (!isAlreadyContact) {
                        authRepository.addContact(currentUserId, userId)
                        loadContacts(currentUserId)
                    }
                }

                onDismissInvite()
                onFeedback(TransientFeedback.Copy("Invitation sent successfully"))
            } catch (e: Exception) {
                onDismissInvite()
                _uiState.update { it.copy(error = e.message ?: "Failed to invite member") }
            }
        }
    }

    fun cancelInvitation(invitationId: String) {
        viewModelScope.launch {
            try {
                repository.cancelInvitation(invitationId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to cancel invitation") }
            }
        }
    }

    fun acceptInvitation(invitationId: String) {
        viewModelScope.launch {
            try {
                repository.acceptInvitation(invitationId)
                
                // Refresh contacts to include the inviter
                authRepository.getCurrentUserId()?.let { uid ->
                    loadContacts(uid)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to accept invitation") }
            }
        }
    }

    fun declineInvitation(invitationId: String) {
        viewModelScope.launch {
            try {
                repository.declineInvitation(invitationId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to decline invitation") }
            }
        }
    }

    fun removeMember(userId: String) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        viewModelScope.launch {
            try {
                repository.removeMember(spaceId, userId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to remove member") }
            }
        }
    }

    fun leaveSpace() {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        viewModelScope.launch {
            try {
                repository.leaveSpace(spaceId)
                _uiState.update { it.copy(hasLeftSpace = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to leave space") }
            }
        }
    }

    fun transferOwnership(newOwnerId: String) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        viewModelScope.launch {
            try {
                repository.transferOwnership(spaceId, newOwnerId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to transfer ownership") }
            }
        }
    }

    fun convertToGroupSpace() {
        val spaceId = _uiState.value.selectedSpaceId
        println("DEBUG: Convert to Group - Step 3: User confirmed conversion")
        println("DEBUG: Convert to Group - Step 5: Savings Space ID: $spaceId")
        
        if (spaceId == null) {
            println("ERROR: Convert to Group - Space ID is NULL")
            return
        }

        val currentUserId = getCurrentUserId()
        println("DEBUG: Convert to Group - Step 4: Current user ID: $currentUserId")

        viewModelScope.launch {
            try {
                println("DEBUG: Convert to Group - Step 8: Calling repository.convertToGroupSpace")
                repository.convertToGroupSpace(spaceId)
                println("DEBUG: Convert to Group - Step 13: Firestore transaction completed successfully")
                
                _uiState.update { it.copy(showConvertToGroupDialog = false) }
                println("DEBUG: Convert to Group - Step 14: ViewModel state updated (dialog hidden)")
                
                // Reload space details to refresh UI
                loadSpaceDetails(spaceId)
                println("DEBUG: Convert to Group - Step 15: Navigation/UI refresh triggered via loadSpaceDetails")
            } catch (e: Exception) {
                println("ERROR: Convert to Group - Failed at step 8-13: ${e.message}")
                e.printStackTrace()
                _uiState.update { it.copy(error = e.message ?: "Failed to convert space", showConvertToGroupDialog = false) }
            }
        }
    }

    fun onConvertToGroupClick() {
        val space = _uiState.value.selectedSpace
        println("DEBUG: Convert to Group - Step 1: Convert button clicked")
        println("DEBUG: Convert to Group - Step 6: Current Savings Space type: ${space?.type}")
        println("DEBUG: Convert to Group - Step 7: Current user is owner: ${isOwner(space)}")
        
        _uiState.update { it.copy(showConvertToGroupDialog = true) }
        println("DEBUG: Convert to Group - Step 2: Confirmation dialog opened")
    }

    fun onDismissConvertToGroup() {
        _uiState.update { it.copy(showConvertToGroupDialog = false) }
    }

    fun onAddTransactionClick(spaceId: String) {
        _uiState.update { it.copy(showAddTransactionDialog = true, selectedSpaceId = spaceId) }
    }

    fun onDismissAddTransaction() {
        _uiState.update { it.copy(showAddTransactionDialog = false) }
    }

    fun addTransaction(amount: Long, type: TransactionType, note: String) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        val space = _uiState.value.selectedSpace ?: return
        val userId = authRepository.getCurrentUserId() ?: ""
        
        viewModelScope.launch {
            try {
                repository.addTransaction(
                    spaceId = spaceId,
                    transaction = SavingsTransaction(
                        spaceId = spaceId,
                        userId = userId,
                        amount = amount,
                        currency = space.currency,
                        type = type,
                        note = note
                    )
                )
                onDismissAddTransaction()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to add transaction") }
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        viewModelScope.launch {
            try {
                repository.deleteTransaction(spaceId, transactionId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete transaction") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun isOwner(space: SavingsSpace?): Boolean {
        val uid = authRepository.getCurrentUserId()
        return space?.ownerId == uid
    }
    
    fun onMemberClick(member: SavingsMember) {
        _uiState.update { it.copy(selectedMember = member) }
    }

    fun onDismissMemberProfile() {
        _uiState.update { it.copy(selectedMember = null) }
    }

    fun getCurrentUserId(): String? = authRepository.getCurrentUserId()
}
