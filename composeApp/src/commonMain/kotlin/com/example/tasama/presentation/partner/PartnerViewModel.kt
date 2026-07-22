package com.example.tasama.presentation.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.RoutePoint
import com.example.tasama.domain.model.Story
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.DirectionsRepository
import com.example.tasama.domain.repository.EtaInfo
import com.example.tasama.domain.repository.PlaceRepository
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
    val etaInfo: EtaInfo? = null,
    val isPartnerComingToMe: Boolean = false,
    val isEtaLoading: Boolean = false,
    val etaError: String? = null,
    val travelMode: com.example.tasama.domain.repository.TravelMode = com.example.tasama.domain.repository.TravelMode.DRIVING,
    val weatherInfo: com.example.tasama.domain.model.WeatherInfo? = null,
    val isWeatherLoading: Boolean = false,
    val weatherError: String? = null,
    val selectedStoryForMap: Story? = null,
    val currentDayRoute: List<RoutePoint> = emptyList(),
    val isRouteLoading: Boolean = false
)

class PartnerViewModel(
    private val authRepository: AuthRepository,
    private val placeRepository: PlaceRepository,
    private val storyRepository: StoryRepository,
    private val directionsRepository: DirectionsRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PartnerUiState())
    val uiState = _uiState.asStateFlow()

    private var partnerObservationJob: Job? = null
    private var placesObservationJob: Job? = null
    private var storiesObservationJob: Job? = null
    private var currentUserJob: Job? = null
    private var etaJob: Job? = null
    private var weatherJob: Job? = null

    private var currentPartnerId: String? = null
    private var currentPlacesUserId: String? = null
    private var currentPlacesPartnerId: String? = null
    private var currentStoriesUserId: String? = null
    private var currentStoriesPartnerId: String? = null

    private var lastEtaRequestLocationMe: Pair<Double, Double>? = null
    private var lastEtaRequestLocationPartner: Pair<Double, Double>? = null
    private var lastEtaTimestamp: Long = 0
    private var lastDistanceMeters: Int? = null

    private var lastWeatherRequestLocation: Pair<Double, Double>? = null
    private var lastWeatherTimestamp: Long = 0

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
                            observeStories(user.id, user.partnerId)
                            checkAndFetchEta()
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
        if (partnerObservationJob?.isActive == true && currentPartnerId == partnerId) return
        currentPartnerId = partnerId
        
        partnerObservationJob?.cancel()
        partnerObservationJob = viewModelScope.launch {
            authRepository.getUserFlow(partnerId).collect { partner ->
                val oldPartner = _uiState.value.partner
                _uiState.update { it.copy(partner = partner) }
                
                if (partner != null) {
                    // Fetch weather for partner
                    if (partner.latitude != null && partner.longitude != null) {
                        checkAndFetchWeather(partner.latitude, partner.longitude)
                    }

                    // Trigger ETA if partner just started moving or moved significantly
                    val justStartedMoving = (partner.speed ?: 0f) > 0.3f && (oldPartner?.speed ?: 0f) <= 0.3f
                    checkAndFetchEta(force = justStartedMoving)
                }
            }
        }
    }

    private fun checkAndFetchEta(force: Boolean = false) {
        if (uiState.value.isEtaLoading) return
        
        val me = uiState.value.currentUser ?: return
        val partner = uiState.value.partner ?: return
        
        val myLat = me.latitude ?: return
        val myLon = me.longitude ?: return
        val pLat = partner.latitude ?: return
        val pLon = partner.longitude ?: return

        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val locationChangedSignificantly = lastEtaRequestLocationMe?.let { calculateDistance(it.first, it.second, myLat, myLon) > 30 } ?: true ||
                lastEtaRequestLocationPartner?.let { calculateDistance(it.first, it.second, pLat, pLon) > 30 } ?: true
        
        val timePassed = now - lastEtaTimestamp > 30_000 // 30 seconds

        if (force || locationChangedSignificantly || timePassed) {
            fetchEta(myLat, myLon, pLat, pLon, uiState.value.travelMode)
        }
    }

    private fun fetchEta(myLat: Double, myLon: Double, pLat: Double, pLon: Double, mode: com.example.tasama.domain.repository.TravelMode) {
        etaJob?.cancel()
        lastEtaTimestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
        // Update these even before the request to avoid immediate retries on failure
        lastEtaRequestLocationMe = myLat to myLon
        lastEtaRequestLocationPartner = pLat to pLon
        
        etaJob = viewModelScope.launch {
            _uiState.update { it.copy(isEtaLoading = true, etaError = null) }
            val result = directionsRepository.getEta(pLat, pLon, myLat, myLon, mode)
            result.onSuccess { etaInfo ->
                val isComing = lastDistanceMeters?.let { it > etaInfo.distanceMeters + 50 } ?: false
                _uiState.update { 
                    it.copy(
                        etaInfo = etaInfo, 
                        isPartnerComingToMe = isComing,
                        isEtaLoading = false,
                        etaError = null
                    ) 
                }
                lastDistanceMeters = etaInfo.distanceMeters
            }.onFailure { error ->
                _uiState.update { 
                    it.copy(
                        isEtaLoading = false,
                        etaError = error.message ?: "Failed to fetch ETA",
                        etaInfo = null
                    ) 
                }
            }
        }
    }

    private fun checkAndFetchWeather(lat: Double, lon: Double) {
        val now = Clock.System.now().toEpochMilliseconds()
        val lastLoc = lastWeatherRequestLocation
        val distance = if (lastLoc != null) {
            calculateDistance(lat, lon, lastLoc.first, lastLoc.second)
        } else {
            Double.MAX_VALUE
        }

        // Fetch if it's the first time, if they moved more than 500m, or if 15 minutes have passed
        if (lastWeatherRequestLocation == null || distance > 500 || (now - lastWeatherTimestamp) > 15 * 60 * 1000) {
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
        checkAndFetchEta(force = true)
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

    private fun compressImage(bytes: ByteArray): ByteArray {
        // Simple size-based check (placeholder for actual compression)
        // In a real app, use a platform-specific image library or a KMP-friendly one.
        // For now, we'll keep it as-is or implement a simple check.
        return bytes
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

    fun updateStory(story: Story) {
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            storyRepository.updateStory(uid, story)
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
        val uid = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            authRepository.updateLocation(uid, lat, lon, speed)
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
}
