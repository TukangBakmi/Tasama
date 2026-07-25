package com.example.tasama.presentation.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.model.BatteryMode
import com.example.tasama.domain.model.DefaultRouteType
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.RoutePoint
import com.example.tasama.domain.model.Story
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.PlaceRepository
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.domain.repository.StoryRepository
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
    val stories: List<Story> = emptyList(),
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
    val travelMode: com.example.tasama.domain.repository.TravelMode = com.example.tasama.domain.repository.TravelMode.DRIVING,
    val routeInfo: com.example.tasama.domain.repository.RouteInfo? = null,
    val isRouteToPartnerLoading: Boolean = false,
    val routeToPartnerError: String? = null,
    val weatherInfo: com.example.tasama.domain.model.WeatherInfo? = null,
    val isWeatherLoading: Boolean = false,
    val weatherError: String? = null,
    val selectedStoryForMap: Story? = null,
    val currentDayRoute: List<RoutePoint> = emptyList(),
    val isRouteLoading: Boolean = false,
    val settings: AppSettings = AppSettings()
)

class PartnerViewModel(
    private val authRepository: AuthRepository,
    private val placeRepository: PlaceRepository,
    private val storyRepository: StoryRepository,
    private val directionsRepository: DirectionsRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PartnerUiState())
    val uiState = _uiState.asStateFlow()

    private var settingsJob: Job? = null
    private var partnerObservationJob: Job? = null
    private var placesObservationJob: Job? = null
    private var storiesObservationJob: Job? = null
    private var currentUserJob: Job? = null
    private var distanceJob: Job? = null
    private var routeToPartnerJob: Job? = null
    private var weatherJob: Job? = null

    private var currentPartnerId: String? = null
    private var currentPlacesUserId: String? = null
    private var currentPlacesPartnerId: String? = null
    private var currentStoriesUserId: String? = null
    private var currentStoriesPartnerId: String? = null

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

                // If travel mode changed in settings, update UI and fetch ETA
                val newTravelMode = when (settings.defaultRouteType) {
                    DefaultRouteType.CAR -> com.example.tasama.domain.repository.TravelMode.DRIVING
                    DefaultRouteType.MOTORCYCLE -> com.example.tasama.domain.repository.TravelMode.MOTORCYCLE
                    DefaultRouteType.WALKING -> com.example.tasama.domain.repository.TravelMode.WALKING
                }
                if (_uiState.value.travelMode != newTravelMode) {
                    setTravelMode(newTravelMode)
                }
            }
        }
    }

    private fun stopAllActivities() {
        partnerObservationJob?.cancel()
        placesObservationJob?.cancel()
        storiesObservationJob?.cancel()
        distanceJob?.cancel()
        weatherJob?.cancel()
        _uiState.update {
            it.copy(
                partner = null,
                places = emptyList(),
                stories = emptyList(),
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
                            observeStories(user.id, user.partnerId)
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
            _uiState.update { it.copy(isLinked = true, pendingRequestFrom = null, pendingRequestTo = null) }
        } else {
            _uiState.update { it.copy(partner = null, isLinked = false) }

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
                    
                    !posChanged && !batteryChanged && !statusChanged
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

    private fun checkAndFetchDistance(force: Boolean = false) {
        if (!_uiState.value.settings.partnerMapEnabled) return
        
        val me = uiState.value.currentUser ?: return
        val partner = uiState.value.partner ?: return
        
        val myLat = me.latitude ?: return
        val myLon = me.longitude ?: return
        val pLat = partner.latitude ?: return
        val pLon = partner.longitude ?: return

        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        
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
            if (_uiState.value.settings.liveEtaEnabled) {
                fetchRouteToPartner(myLat, myLon, pLat, pLon)
            }
        }
    }

    private fun fetchRouteToPartner(myLat: Double, myLon: Double, pLat: Double, pLon: Double) {
        routeToPartnerJob?.cancel()
        routeToPartnerJob = viewModelScope.launch {
            _uiState.update { it.copy(isRouteToPartnerLoading = true, routeToPartnerError = null) }
            directionsRepository.getRoute(myLat, myLon, pLat, pLon, _uiState.value.travelMode)
                .onSuccess { routeInfo ->
                    _uiState.update {
                        it.copy(
                            routeInfo = routeInfo,
                            isRouteToPartnerLoading = false,
                            routeToPartnerError = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRouteToPartnerLoading = false,
                            routeToPartnerError = error.message ?: "Failed to fetch route"
                        )
                    }
                }
        }
    }

    private fun updateDistance(myLat: Double, myLon: Double, pLat: Double, pLon: Double) {
        lastDistanceTimestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
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

    fun setTravelMode(mode: com.example.tasama.domain.repository.TravelMode) {
        if (_uiState.value.travelMode == mode) return
        _uiState.update { it.copy(travelMode = mode) }
        checkAndFetchDistance(force = true)
    }

    fun openNavigation() {
        val partner = _uiState.value.partner ?: return
        val lat = partner.latitude ?: return
        val lon = partner.longitude ?: return
        directionsRepository.openExternalNavigation(lat, lon, _uiState.value.travelMode)
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
        if (placesObservationJob?.isActive == true && currentPlacesUserId == userId && currentPlacesPartnerId == partnerId) return
        currentPlacesUserId = userId
        currentPlacesPartnerId = partnerId

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

    fun addPlace(place: Place) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            placeRepository.addPlace(uid, place)
        }
    }

    fun deletePlace(placeId: String) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            placeRepository.deletePlace(uid, placeId)
        }
    }

    private fun observeStories(userId: String, partnerId: String?) {
        if (!_uiState.value.settings.partnerMapEnabled || !_uiState.value.settings.storyMarkersEnabled) {
            _uiState.update { it.copy(stories = emptyList()) }
            currentStoriesUserId = null
            currentStoriesPartnerId = null
            storiesObservationJob?.cancel()
            return
        }
        if (storiesObservationJob?.isActive == true && currentStoriesUserId == userId && currentStoriesPartnerId == partnerId) return
        currentStoriesUserId = userId
        currentStoriesPartnerId = partnerId

        storiesObservationJob?.cancel()
        storiesObservationJob = viewModelScope.launch {
            val myStoriesFlow = storyRepository.getStories(userId)
            val partnerStoriesFlow = partnerId?.let { storyRepository.getStories(it) } ?: flowOf(emptyList())

            combine(myStoriesFlow, partnerStoriesFlow) { myStories, pStories ->
                (myStories + pStories).distinctBy { it.id }
            }.collect { allStories ->
                _uiState.update { it.copy(stories = allStories) }
            }
        }
    }

    fun addStory(story: Story, photoBytes: List<ByteArray> = emptyList()) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Image compression before upload
                val compressedPhotos = photoBytes.map { bytes ->
                    compressImage(bytes, 80)
                }
                
                val photoUrls = compressedPhotos.map { bytes ->
                    storyRepository.uploadStoryPhoto(uid, bytes)
                }
                val storyWithPhotos = story.copy(photoUrls = story.photoUrls + photoUrls)
                storyRepository.addStory(uid, storyWithPhotos)
                _uiState.update { it.copy(isLoading = false, successMessage = "Story added successfully!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to add story") }
            }
        }
    }

    fun deleteStory(story: Story) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                storyRepository.deleteStory(uid, story)
                _uiState.update { it.copy(isLoading = false, successMessage = "Story deleted successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to delete story") }
            }
        }
    }

    fun updateStory(story: Story, photoBytes: List<ByteArray> = emptyList()) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Compress and upload new photos
                val compressedPhotos = photoBytes.map { bytes ->
                    compressImage(bytes, 80)
                }
                
                val newPhotoUrls = compressedPhotos.map { bytes ->
                    storyRepository.uploadStoryPhoto(uid, bytes)
                }
                
                val storyWithPhotos = story.copy(photoUrls = story.photoUrls + newPhotoUrls)
                storyRepository.updateStory(uid, storyWithPhotos)
                _uiState.update { it.copy(isLoading = false, successMessage = "Story updated successfully!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to update story") }
            }
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

    fun updateLocation(lat: Double, lon: Double, speed: Float? = null) {
        if (!_uiState.value.settings.partnerMapEnabled) return
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.updateLocation(uid, lat, lon, speed)
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

    fun selectStoryForMap(story: Story?) {
        _uiState.update { it.copy(selectedStoryForMap = story) }
    }

    fun fetchTodayRoute() {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRouteLoading = true) }
            val now = Clock.System.now().toEpochMilliseconds()
            // Start of day (00:00:00)
            val startOfDay = now - (now % (24 * 60 * 60 * 1000))
            
            val route = authRepository.getRouteForDay(uid, startOfDay, now)
            _uiState.update { it.copy(currentDayRoute = route, isRouteLoading = false) }
        }
    }

    fun saveJourneyAsStory(title: String, description: String, category: String, photoBytes: List<ByteArray>) {
        val uid = authRepository.getCurrentUserId() ?: return
        val route = _uiState.value.currentDayRoute
        if (route.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val distance = calculateTotalRouteDistance(route)
                val duration = if (route.size > 1) route.last().timestamp - route.first().timestamp else 0L
                
                // Compress and upload photos
                val compressedPhotos = photoBytes.map { bytes -> compressImage(bytes, 80) }
                val photoUrls = compressedPhotos.map { bytes -> storyRepository.uploadStoryPhoto(uid, bytes) }

                val story = Story(
                    title = title,
                    description = description,
                    category = category,
                    date = Clock.System.now().toEpochMilliseconds(),
                    latitude = route.first().latitude,
                    longitude = route.first().longitude,
                    photoUrls = photoUrls,
                    route = route,
                    totalDistance = distance,
                    totalDuration = duration,
                    createdBy = uid
                )
                
                storyRepository.addStory(uid, story)
                _uiState.update { it.copy(isLoading = false, successMessage = "Journey saved as story!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to save journey") }
            }
        }
    }

    private fun calculateTotalRouteDistance(route: List<RoutePoint>): Double {
        var total = 0.0
        for (i in 0 until route.size - 1) {
            total += calculateDistance(
                route[i].latitude, route[i].longitude,
                route[i + 1].latitude, route[i + 1].longitude
            )
        }
        return total
    }

    fun onIdCopied() {
        _uiState.update { it.copy(successMessage = "ID copied to clipboard") }
    }

    fun clearOperationSuccess() {
        _uiState.update { it.copy(isOperationSuccess = false) }
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

    fun updateLiveEtaEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateLiveEtaEnabled(enabled) }
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

    fun updateStoryMarkersEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateStoryMarkersEnabled(enabled) }
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

    fun updateDefaultRouteType(type: DefaultRouteType) {
        viewModelScope.launch { settingsRepository.updateDefaultRouteType(type) }
    }
}
