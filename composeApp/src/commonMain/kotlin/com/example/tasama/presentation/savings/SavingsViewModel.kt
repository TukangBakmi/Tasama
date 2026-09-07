package com.example.tasama.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.*
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.presentation.components.TransientFeedback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock

class SavingsViewModel(
    private val repository: SavingsRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SavingsEvent>()
    val events = _events.asSharedFlow()

    private var dataJob: Job? = null
    private var detailsJob: Job? = null
    private var transactionJob: Job? = null
    private var invitationJob: Job? = null
    private var activityJob: Job? = null
    private var myInvitationsJob: Job? = null
    private var partnerJob: Job? = null

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
                    observePartnerStatus(uid)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(userCurrency = settings.currency) }
            }
        }
    }

    private fun observePartnerStatus(uid: String) {
        partnerJob?.cancel()
        partnerJob = viewModelScope.launch {
            authRepository.getUserFlow(uid).collect { user ->
                _uiState.update { it.copy(currentUser = user) }
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
        partnerJob?.cancel()
    }

    private fun loadContacts(uid: String) {
        viewModelScope.launch {
            val user = authRepository.getUser(uid)
            _uiState.update { it.copy(currentUser = user) }
            val contacts = user?.contactIds?.mapNotNull { authRepository.getUser(it) } ?: emptyList()
            _uiState.update { 
                it.copy(
                    contacts = contacts,
                    filteredContacts = contacts
                ) 
            }
        }
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
        val space = _uiState.value.savingsSpaces.find { it.id == spaceId }
        _uiState.update { it.copy(selectedSpaceId = spaceId, selectedSpace = space) }
    }

    fun onSpaceHandled() {
        _uiState.update {
            it.copy(
                selectedSpaceId = null,
                selectedSpace = null,
                showSpaceDetails = false,
                transactions = emptyList(),
                activityHistory = emptyList(),
                pendingInvitations = emptyList()
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadSpaceDetails(spaceId: String) {
        detailsJob?.cancel()
        transactionJob?.cancel()
        invitationJob?.cancel()
        activityJob?.cancel()

        _uiState.update { it.copy(isLoading = true, showSpaceDetails = true) }

        detailsJob = viewModelScope.launch {
            repository.getSavingsSpace(spaceId).collect { space ->
                val lastKnownSpace = _uiState.value.selectedSpace
                
                if (space == null) {
                    // Check if the space was deleted by someone else while a user was viewing it
                    if (lastKnownSpace != null) {
                        println("DEBUG: [Savings] Space deleted. Notifying user.")
                        _events.emit(SavingsEvent.NavigateToSavingsList)
                        _events.emit(SavingsEvent.ShowFeedback("This Savings Space has been deleted by the owner."))
                    }
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    _uiState.update { it.copy(selectedSpaceId = spaceId, selectedSpace = space, isLoading = false) }
                }
            }
        }

        transactionJob = viewModelScope.launch {
            repository.getTransactions(spaceId).collect { transactions ->
                _uiState.update { it.copy(transactions = transactions) }
            }
        }

        invitationJob = viewModelScope.launch {
            repository.getPendingInvitations(spaceId).collect { invitations ->
                _uiState.update { it.copy(pendingInvitations = invitations) }
            }
        }

        activityJob = viewModelScope.launch {
            repository.getActivityHistory(spaceId).collect { activity ->
                _uiState.update { it.copy(activityHistory = activity) }
            }
        }
    }

    fun onDismissSpaceDetails() {
        detailsJob?.cancel()
        transactionJob?.cancel()
        invitationJob?.cancel()
        activityJob?.cancel()
        _uiState.update { 
            it.copy(
                selectedSpaceId = null, 
                selectedSpace = null,
                showSpaceDetails = false,
                transactions = emptyList(),
                activityHistory = emptyList(),
                pendingInvitations = emptyList()
            ) 
        }
    }

    fun onRemovedDialogConfirm() {
        // No-op, removed in favor of event-driven navigation and toast
    }

    fun addSpace(space: SavingsSpace) {
        viewModelScope.launch {
            try {
                repository.createSavingsSpace(space)
                onDismissAddSpace()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to add space") }
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

    fun deleteSpace(id: String, onFeedback: (TransientFeedback) -> Unit = {}) {
        viewModelScope.launch {
            try {
                println("DEBUG: [Savings] deleteSpace started for id: $id")

                // Cancel detail jobs BEFORE the actual deletion to avoid the collector 
                // in loadSpaceDetails receiving a null space and showing the "deleted" toast 
                // to the person who is actually performing the deletion.
                detailsJob?.cancel()
                transactionJob?.cancel()
                invitationJob?.cancel()
                activityJob?.cancel()

                repository.deleteSavingsSpace(id)
                println("DEBUG: [Savings] repository.deleteSavingsSpace SUCCESS")
                
                // Clear state locally immediately to ensure no stale data
                _uiState.update { 
                    it.copy(
                        selectedSpaceId = null,
                        selectedSpace = null,
                        showSpaceDetails = false,
                        transactions = emptyList(),
                        activityHistory = emptyList(),
                        pendingInvitations = emptyList(),
                        isLoading = false
                    )
                }
                
                println("DEBUG: [Savings] Emitting NavigateToSavingsList event")
                _events.emit(SavingsEvent.NavigateToSavingsList)
            } catch (e: Exception) {
                if (e.message == "Only owner can delete the space") {
                    onFeedback(TransientFeedback.Copy("Only owner can delete the space"))
                } else {
                    _uiState.update { it.copy(error = e.message ?: "Failed to delete space") }
                }
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
        val space = _uiState.value.selectedSpace
        val pendingIds = _uiState.value.pendingInvitations.map { it.inviteeId }
        val memberIds = space?.memberIds ?: emptyList()
        val ownerIds = space?.ownerIds ?: emptyList()
        val ownerId = space?.ownerId ?: ""
        val excludedIds = (pendingIds + memberIds + ownerIds + ownerId).toSet()

        _uiState.update { 
            it.copy(
                showInviteMemberDialog = true,
                selectedSpaceId = spaceId,
                searchQuery = "",
                searchedUser = null,
                filteredContacts = it.contacts.filter { contact -> contact.id !in excludedIds }
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
        
        if (query.length >= 3) {
            searchUser(query)
        } else {
            _uiState.update { it.copy(searchedUser = null) }
        }

        val space = _uiState.value.selectedSpace
        val pendingIds = _uiState.value.pendingInvitations.map { it.inviteeId }
        val memberIds = space?.memberIds ?: emptyList()
        val ownerIds = space?.ownerIds ?: emptyList()
        val ownerId = space?.ownerId ?: ""
        
        val excludedIds = (pendingIds + memberIds + ownerIds + ownerId).toSet()

        val filtered = if (query.isEmpty()) {
            _uiState.value.contacts.filter { it.id !in excludedIds }
        } else {
            _uiState.value.contacts.filter { 
                (it.name.contains(query, ignoreCase = true) || 
                it.shortId.contains(query) ||
                it.id.contains(query, ignoreCase = true)) &&
                it.id !in excludedIds
            }
        }
        _uiState.update { it.copy(filteredContacts = filtered) }
    }

    fun searchUser(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val user = authRepository.getUserIdByName(query)?.let { authRepository.getUser(it) }
                ?: authRepository.getUserIdFromShortId(query)?.let { authRepository.getUser(it) }
            
            val space = _uiState.value.selectedSpace
            val pendingIds = _uiState.value.pendingInvitations.map { it.inviteeId }
            val memberIds = space?.memberIds ?: emptyList()
            val ownerIds = space?.ownerIds ?: emptyList()
            val ownerId = space?.ownerId ?: ""
            val excludedIds = (pendingIds + memberIds + ownerIds + ownerId).toSet()

            val filteredUser = if (user != null && user.id in excludedIds) null else user

            _uiState.update { 
                it.copy(
                    searchedUser = filteredUser,
                    isSearching = false
                ) 
            }
        }
    }

    fun inviteMember(userId: String, onFeedback: (TransientFeedback) -> Unit) {
        val spaceId = _uiState.value.selectedSpaceId ?: return
        viewModelScope.launch {
            try {
                repository.inviteMember(spaceId, userId)
                onDismissInvite()
                onFeedback(TransientFeedback.Copy("Invitation sent successfully"))
            } catch (e: Exception) {
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
                println("DEBUG: [Savings] leaveSpace started for id: $spaceId")
                
                // Cancel all detail-related jobs immediately to stop UI updates 
                // and avoid "deleted by owner" false positives if access is lost
                detailsJob?.cancel()
                transactionJob?.cancel()
                invitationJob?.cancel()
                activityJob?.cancel()

                repository.leaveSpace(spaceId)
                println("DEBUG: [Savings] repository.leaveSpace SUCCESS")
                
                // Clear state locally immediately to ensure no stale data
                _uiState.update { 
                    it.copy(
                        selectedSpaceId = null,
                        selectedSpace = null,
                        showSpaceDetails = false,
                        transactions = emptyList(),
                        activityHistory = emptyList(),
                        pendingInvitations = emptyList(),
                        isLoading = false
                    )
                }
                
                println("DEBUG: [Savings] Emitting NavigateToSavingsList event")
                _events.emit(SavingsEvent.NavigateToSavingsList)
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
        val spaceId = _uiState.value.selectedSpaceId ?: return
        viewModelScope.launch {
            try {
                repository.convertToGroupSpace(spaceId)
                onDismissConvertToGroup()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to convert to group space") }
            }
        }
    }

    fun onConvertToGroupClick() {
        _uiState.update { it.copy(showConvertToGroupDialog = true) }
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
        val space = _uiState.value.selectedSpace
        val uid = authRepository.getCurrentUserId() ?: return
        
        viewModelScope.launch {
            try {
                val transaction = SavingsTransaction(
                    id = "tx_${Clock.System.now().toEpochMilliseconds()}",
                    spaceId = spaceId,
                    userId = uid,
                    userName = authRepository.getUserName(uid) ?: "User",
                    amount = amount,
                    currency = space?.currency ?: "IDR",
                    type = type,
                    note = note,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
                repository.addTransaction(spaceId, transaction)
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
        return space?.ownerId == uid || space?.ownerIds?.contains(uid) == true
    }

    fun onMemberClick(member: SavingsMember) {
        _uiState.update { it.copy(selectedMember = member) }
    }

    fun onDismissMemberProfile() {
        _uiState.update { it.copy(selectedMember = null) }
    }

    fun getCurrentUserId(): String? = authRepository.getCurrentUserId()
}
