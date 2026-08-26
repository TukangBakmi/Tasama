package com.example.tasama.presentation.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.BatteryMode
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.PlaceRepository
import com.example.tasama.domain.repository.PresenceRepository
import com.example.tasama.domain.repository.PresenceState
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.domain.repository.WeatherRepository
import com.example.tasama.util.compressImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.time.Clock

data class PartnerUiState(
    val currentUser: User? = null,
    val partner: User? = null,
    val places: List<Place> = emptyList(),
    val pendingRequestFrom: User? = null,
    val pendingRequestTo: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val isLinked: Boolean = false,
    val isGuest: Boolean = false,
    val partnerShortIdInput: String = "",
    val isOperationSuccess: Boolean = false,
    val distanceInfo: com.example.tasama.domain.repository.DistanceInfo? = null,
    val isPartnerComingToMe: Boolean = false,
    val isDistanceLoading: Boolean = false,
    val distanceError: String? = null,
    val weatherInfo: com.example.tasama.domain.model.WeatherInfo? = null,
    val isWeatherLoading: Boolean = false,
    val weatherError: String? = null,
    val partnerPresence: PresenceState = PresenceState.Offline(0L),
    val settings: AppSettings = AppSettings()
)

class PartnerViewModel(
    private val authRepository: AuthRepository,
    private val placeRepository: PlaceRepository,
    private val directionsRepository: DirectionsRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val presenceRepository: PresenceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PartnerUiState())
    val uiState = _uiState.asStateFlow()

    private var settingsJob: Job? = null
    private var partnerObservationJob: Job? = null
    private var presenceObservationJob: Job? = null
    private var placesObservationJob: Job? = null
    private var currentUserJob: Job? = null
    private var distanceJob: Job? = null
    private var weatherJob: Job? = null

    private var currentPartnerId: String? = null
    private var currentPlacesUserId: String? = null
    private var currentPlacesPartnerId: String? = null

    private var lastDistanceRequestLocationMe: Pair<Double, Double>? = null
    private var lastDistanceRequestLocationPartner: Pair<Double, Double>? = null
    private var lastDistanceTimestamp: Long = 0
    private var lastDistanceMeters: Int? = null

    private var lastWeatherRequestLocation: Pair<Double, Double>? = null
    private var lastWeatherTimestamp: Long = 0

    init {
        observeSettings()
        refresh()
    }

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val previousSettings = _uiState.value.settings
                _uiState.update { it.copy(settings = settings) }

                if (!settings.partnerMapEnabled) {
                    stopAllActivities()
                } else if (!previousSettings.partnerMapEnabled) {
                    refresh()
                }
            }
        }
    }

    private fun stopAllActivities() {
        partnerObservationJob?.cancel()
        presenceObservationJob?.cancel()
        placesObservationJob?.cancel()
        distanceJob?.cancel()
        weatherJob?.cancel()
        _uiState.update {
            it.copy(
                partner = null,
                partnerPresence = PresenceState.Offline(0L),
                places = emptyList(),
                distanceInfo = null,
                weatherInfo = null
            )
        }
    }

    fun refresh() {
        if (!_uiState.value.settings.partnerMapEnabled) return

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
                            checkAndFetchDistance()
                        }
                    }
                } else {
                    _uiState.value = PartnerUiState()
                }
            }
        }
    }

    private suspend fun handlePartnerAndRequests(user: User) {
        if (user.partnerId != null) {
            observePartner(user.partnerId)
            observePresence(user.partnerId)
            _uiState.update { it.copy(isLinked = true, pendingRequestFrom = null, pendingRequestTo = null) }
        } else {
            _uiState.update { it.copy(partner = null, partnerPresence = PresenceState.Offline(0L), isLinked = false) }
            presenceObservationJob?.cancel()

            if (user.partnerRequestFrom != null) {
                val requester = authRepository.getUser(user.partnerRequestFrom)
                _uiState.update { it.copy(pendingRequestFrom = requester) }
            } else {
                _uiState.update { it.copy(pendingRequestFrom = null) }
            }

            if (user.partnerRequestTo != null) {
                val requested = authRepository.getUser(user.partnerRequestTo)
                _uiState.update { it.copy(pendingRequestTo = requested) }
            } else {
                _uiState.update { it.copy(pendingRequestTo = null) }
            }
        }
    }

    private fun observePartner(partnerId: String) {
        if (!_uiState.value.settings.partnerMapEnabled) return
        if (partnerObservationJob?.isActive == true && currentPartnerId == partnerId) return
        currentPartnerId = partnerId
        
        partnerObservationJob?.cancel()
        partnerObservationJob = viewModelScope.launch {
            authRepository.getUserFlow(partnerId)
                .distinctUntilChanged { old, new ->
                    // Throttling: Only update UI if significant fields changed
                    if (old == null || new == null) return@distinctUntilChanged false
                    
                    val posChanged = (old.latitude != new.latitude || old.longitude != new.longitude)
                    val batteryChanged = abs((old.batteryLevel ?: 0f) - (new.batteryLevel ?: 0f)) > 0.05f || old.isCharging != new.isCharging
                    val statusChanged = old.connectionType != new.connectionType || old.name != new.name
                    
                    // Include timestamp and accuracy to keep connection status "Live" when stationary
                    val timeUpdated = old.lastLocationUpdate != new.lastLocationUpdate
                    val accuracyChanged = abs((old.accuracy ?: 0f) - (new.accuracy ?: 0f)) > 10f
                    
                    !posChanged && !batteryChanged && !statusChanged && !timeUpdated && !accuracyChanged
                }
                .collect { partner ->
                    _uiState.update { it.copy(partner = partner) }
                    
                    if (partner != null) {
                        // Fetch weather for partner
                        if (partner.latitude != null && partner.longitude != null) {
                            checkAndFetchWeather(partner.latitude, partner.longitude)
                        }

                        // Trigger distance update if partner moved
                        checkAndFetchDistance()
                    }
                }
        }
    }

    private fun observePresence(userId: String) {
        if (!_uiState.value.settings.partnerMapEnabled) return
        presenceObservationJob?.cancel()
        presenceObservationJob = viewModelScope.launch {
            presenceRepository.getPresence(userId).collect { presence ->
                _uiState.update { it.copy(partnerPresence = presence) }
            }
        }
    }

    private fun checkAndFetchDistance(force: Boolean = false) {
        if (!_uiState.value.settings.partnerMapEnabled) return
        
        val me = uiState.value.currentUser ?: return
        val partner = uiState.value.partner ?: return
        
        val myLat = me.latitude ?: return
        val myLon = me.longitude ?: return
        val pLat = partner.latitude ?: return
        val pLon = partner.longitude ?: return

        val now = Clock.System.now().toEpochMilliseconds()
        
        // Define thresholds based on battery mode
        val (distThreshold, timeThreshold) = when (_uiState.value.settings.batteryMode) {
            BatteryMode.PERFORMANCE -> 5.0 to 5_000L // 5m, 5s
            BatteryMode.BALANCED -> 15.0 to 15_000L // 15m, 15s
            BatteryMode.BATTERY_SAVER -> 50.0 to 60_000L // 50m, 60s
        }

        val locationChangedSignificantly = lastDistanceRequestLocationMe?.let { calculateDistance(it.first, it.second, myLat, myLon) > distThreshold } ?: true ||
                lastDistanceRequestLocationPartner?.let { calculateDistance(it.first, it.second, pLat, pLon) > distThreshold } ?: true
        
        val timePassed = now - lastDistanceTimestamp > timeThreshold

        if (force || locationChangedSignificantly || timePassed) {
            updateDistance(myLat, myLon, pLat, pLon)
        }
    }

    private fun updateDistance(myLat: Double, myLon: Double, pLat: Double, pLon: Double) {
        lastDistanceTimestamp = Clock.System.now().toEpochMilliseconds()
        lastDistanceRequestLocationMe = myLat to myLon
        lastDistanceRequestLocationPartner = pLat to pLon
        
        val distanceInfo = directionsRepository.getDistance(pLat, pLon, myLat, myLon)
        
        // Filter out tiny jitter to prevent unnecessary UI updates
        if (lastDistanceMeters != null && abs(lastDistanceMeters!! - distanceInfo.distanceMeters) < 2 && !(_uiState.value.isDistanceLoading)) {
             return
        }

        val isComing = lastDistanceMeters?.let { it > distanceInfo.distanceMeters + 2 } ?: false
        
        _uiState.update { 
            it.copy(
                distanceInfo = distanceInfo, 
                isPartnerComingToMe = isComing,
                isDistanceLoading = false,
                distanceError = null
            ) 
        }
        lastDistanceMeters = distanceInfo.distanceMeters
    }

    private fun checkAndFetchWeather(lat: Double, lon: Double) {
        if (!_uiState.value.settings.partnerMapEnabled || !_uiState.value.settings.weatherWidgetEnabled) return
        val now = Clock.System.now().toEpochMilliseconds()
        
        val (distThreshold, timeThreshold) = when (_uiState.value.settings.batteryMode) {
            BatteryMode.PERFORMANCE -> 500.0 to 10 * 60 * 1000L // 500m, 10m
            BatteryMode.BALANCED -> 1000.0 to 20 * 60 * 1000L // 1km, 20m
            BatteryMode.BATTERY_SAVER -> 3000.0 to 60 * 60 * 1000L // 3km, 1h
        }

        val lastLoc = lastWeatherRequestLocation
        val distance = if (lastLoc != null) {
            calculateDistance(lat, lon, lastLoc.first, lastLoc.second)
        } else {
            Double.MAX_VALUE
        }

        if (lastWeatherRequestLocation == null || distance > distThreshold || (now - lastWeatherTimestamp) > timeThreshold) {
            fetchWeather(lat, lon)
        }
    }

    private fun fetchWeather(lat: Double, lon: Double) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            _uiState.update { it.copy(isWeatherLoading = true, weatherError = null) }
            weatherRepository.getWeather(lat, lon)
                .onSuccess { weatherInfo ->
                    _uiState.update { 
                        it.copy(
                            weatherInfo = weatherInfo,
                            isWeatherLoading = false,
                            weatherError = null
                        )
                    }
                    lastWeatherRequestLocation = lat to lon
                    lastWeatherTimestamp = Clock.System.now().toEpochMilliseconds()
                }
                .onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isWeatherLoading = false,
                            weatherError = error.message ?: "Failed to fetch weather"
                            // WeatherInfo is kept as a cache
                        )
                    }
                }
        }
    }


    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2).pow(2) + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun observePlaces(userId: String, partnerId: String?) {
        if (!_uiState.value.settings.partnerMapEnabled || !_uiState.value.settings.placesEnabled) {
            _uiState.update { it.copy(places = emptyList()) }
            currentPlacesUserId = null
            currentPlacesPartnerId = null
            placesObservationJob?.cancel()
            return
        }

        // Use a stable identifier for the relationship
        val relationshipId = if (partnerId != null) {
            listOf(userId, partnerId).sorted().joinToString("_")
        } else {
            userId // Fallback for single users
        }

        if (placesObservationJob?.isActive == true && currentPlacesUserId == relationshipId) return
        currentPlacesUserId = relationshipId

        placesObservationJob?.cancel()
        placesObservationJob = viewModelScope.launch {
            placeRepository.getPlaces(relationshipId).collect { allPlaces ->
                _uiState.update { it.copy(places = allPlaces) }
            }
        }
    }

    fun addPlace(place: Place) {
        val user = _uiState.value.currentUser ?: return
        val partnerId = _uiState.value.partner?.id
        
        val relationshipId = if (partnerId != null) {
            listOf(user.id, partnerId).sorted().joinToString("_")
        } else {
            user.id
        }

        viewModelScope.launch {
            placeRepository.addPlace(
                place.copy(
                    relationshipId = relationshipId,
                    createdBy = user.id
                )
            )
        }
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            placeRepository.deletePlace(placeId)
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
            val result = authRepository.sendPartnerRequest(uid, shortId)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Request sent successfully!", partnerShortIdInput = "") }
            } else {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "Failed to send request") }
            }
        }
    }

    fun acceptPartnerRequest(anniversaryDate: Long) {
        val uid = authRepository.getCurrentUserId() ?: return
        val partnerUid = _uiState.value.pendingRequestFrom?.id
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isOperationSuccess = false) }
            val result = authRepository.acceptPartnerRequest(uid, anniversaryDate)
            if (result.isSuccess) {
                // When linking, we migrate user-specific places to the new shared relationship collection
                // or just delete them to start fresh as per the previous logic, but now targeting the right identifier.
                placeRepository.deleteAllPlaces(uid)
                if (partnerUid != null) {
                    placeRepository.deleteAllPlaces(partnerUid)
                }
                _uiState.update { it.copy(isLoading = false, successMessage = "Partner linked!", isOperationSuccess = true) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "Failed to link partner") }
            }
        }
    }

    fun declinePartnerRequest() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            val result = authRepository.declinePartnerRequest(uid)
            if (result.isSuccess) {
                _uiState.update { it.copy(successMessage = "Request declined") }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message ?: "Failed to decline request") }
            }
        }
    }

    fun cancelPartnerRequest() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            val result = authRepository.cancelPartnerRequest(uid)
            if (result.isSuccess) {
                _uiState.update { it.copy(successMessage = "Request cancelled") }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message ?: "Failed to cancel request") }
            }
        }
    }

    fun unlinkPartner() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            val result = authRepository.unlinkPartner(uid)
            if (result.isSuccess) {
                _uiState.update { it.copy(successMessage = "Partner unlinked") }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message ?: "Failed to unlink partner") }
            }
        }
    }

    fun updateAnniversaryDate(date: Long) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isOperationSuccess = false) }
            authRepository.updateAnniversaryDate(uid, date).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isOperationSuccess = true, successMessage = "Anniversary updated") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
            )
        }
    }

    fun updateBatteryLevel(level: Float, isCharging: Boolean) {
        if (!_uiState.value.settings.partnerMapEnabled) return
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

    fun onIdCopied() {
        _uiState.update { it.copy(successMessage = "ID copied to clipboard") }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    // Settings update methods
    fun updatePartnerMapEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updatePartnerMapEnabled(enabled) }
    }

    fun updateBatteryMode(mode: BatteryMode) {
        viewModelScope.launch { settingsRepository.updateBatteryMode(mode) }
    }

    fun updateSmartFollowEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSmartFollowEnabled(enabled) }
    }

    fun updateWeatherWidgetEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateWeatherWidgetEnabled(enabled) }
    }

    fun updateDashboardEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateDashboardEnabled(enabled) }
    }

    fun updatePlacesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updatePlacesEnabled(enabled) }
    }

    fun updateReminderNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateReminderNotificationsEnabled(enabled) }
    }

    fun updateReminderMarkersEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateReminderMarkersEnabled(enabled) }
    }

    fun updateTrafficLayerEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateTrafficLayerEnabled(enabled) }
    }

    fun updateMapDarkThemeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMapDarkThemeEnabled(enabled) }
    }
}
