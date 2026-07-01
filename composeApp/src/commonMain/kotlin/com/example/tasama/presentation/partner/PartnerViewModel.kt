package com.example.tasama.presentation.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.PlaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PartnerUiState(
    val currentUser: User? = null,
    val partner: User? = null,
    val places: List<Place> = emptyList(),
    val pendingRequestFrom: User? = null,
    val pendingRequestTo: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLinked: Boolean = false,
    val isGuest: Boolean = false,
    val partnerShortIdInput: String = ""
)

class PartnerViewModel(
    private val authRepository: AuthRepository,
    private val placeRepository: PlaceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PartnerUiState())
    val uiState = _uiState.asStateFlow()

    private var partnerObservationJob: Job? = null
    private var placesObservationJob: Job? = null
    private var currentUserJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        currentUserJob?.cancel()
        currentUserJob = viewModelScope.launch {
            authRepository.userId.collectLatest { uid ->
                if (uid != null) {
                    val isGuest = authRepository.isGuest()
                    _uiState.update { it.copy(isGuest = isGuest, isLoading = true) }
                    
                    authRepository.getUserFlow(uid).collectLatest { user ->
                        _uiState.update { it.copy(currentUser = user, isLoading = false) }
                        if (user != null) {
                            handlePartnerAndRequests(user)
                            observePlaces(user.id, user.partnerId)
                        }
                    }
                } else {
                    _uiState.value = PartnerUiState()
                }
            }
        }
    }

    private suspend fun handlePartnerAndRequests(user: User) {
        // Handle Linked Partner
        if (user.partnerId != null) {
            observePartner(user.partnerId)
            _uiState.update { it.copy(isLinked = true, pendingRequestFrom = null, pendingRequestTo = null) }
        } else {
            _uiState.update { it.copy(partner = null, isLinked = false) }
            
            // Handle Incoming Request
            if (user.partnerRequestFrom != null) {
                val requester = authRepository.getUser(user.partnerRequestFrom)
                _uiState.update { it.copy(pendingRequestFrom = requester) }
            } else {
                _uiState.update { it.copy(pendingRequestFrom = null) }
            }

            // Handle Outgoing Request
            if (user.partnerRequestTo != null) {
                val requested = authRepository.getUser(user.partnerRequestTo)
                _uiState.update { it.copy(pendingRequestTo = requested) }
            } else {
                _uiState.update { it.copy(pendingRequestTo = null) }
            }
        }
    }

    private fun observePartner(partnerId: String) {
        partnerObservationJob?.cancel()
        partnerObservationJob = viewModelScope.launch {
            authRepository.getUserFlow(partnerId).collect { partner ->
                _uiState.update { it.copy(partner = partner) }
            }
        }
    }

    private fun observePlaces(userId: String, partnerId: String?) {
        placesObservationJob?.cancel()
        placesObservationJob = viewModelScope.launch {
            val myPlacesFlow = placeRepository.getPlaces(userId)
            val partnerPlacesFlow = partnerId?.let { placeRepository.getPlaces(it) } ?: flowOf(emptyList())
            
            combine(myPlacesFlow, partnerPlacesFlow) { myPlaces, pPlaces ->
                (myPlaces + pPlaces).distinctBy { it.id }
            }.collect { allPlaces ->
                _uiState.update { it.copy(places = allPlaces) }
            }
        }
    }

    fun addPlace(name: String, lat: Double, lon: Double, radius: Double) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            val place = Place(name = name, latitude = lat, longitude = lon, radius = radius)
            placeRepository.addPlace(uid, place)
        }
    }

    fun deletePlace(placeId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            placeRepository.deletePlace(uid, placeId)
        }
    }

    fun onPartnerShortIdChange(shortId: String) {
        _uiState.update { it.copy(partnerShortIdInput = shortId) }
    }

    fun sendPartnerRequest() {
        val uid = authRepository.getCurrentUserId() ?: return
        val shortId = _uiState.value.partnerShortIdInput
        if (shortId.length != 12) {
            _uiState.update { it.copy(error = "Invalid Partner ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.sendPartnerRequest(uid, shortId).onFailure { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun acceptPartnerRequest(anniversaryDate: Long) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.acceptPartnerRequest(uid, anniversaryDate).onFailure { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun declinePartnerRequest() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.declinePartnerRequest(uid).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun cancelPartnerRequest() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.cancelPartnerRequest(uid).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun unlinkPartner() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.unlinkPartner(uid).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateAnniversaryDate(date: Long) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authRepository.updateAnniversaryDate(uid, date).onFailure { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun updateLocation(lat: Double, lon: Double) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.updateLocation(uid, lat, lon)
        }
    }

    fun updateBatteryLevel(level: Float, isCharging: Boolean) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.updateBatteryLevel(uid, level, isCharging)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
