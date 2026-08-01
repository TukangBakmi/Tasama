package com.example.tasama.presentation.partner

import android.graphics.Point
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.Story
import com.example.tasama.domain.model.User
import com.example.tasama.domain.model.WeatherInfo
import com.example.tasama.domain.model.AppSettings
import com.example.tasama.domain.repository.DistanceInfo
import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.theme.LocalIsDarkTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.tasama.util.reverseGeocode
import com.example.tasama.util.calculateDistance
import com.example.tasama.util.Location
import com.example.tasama.util.format
import com.example.tasama.util.clipSegmentToRect
import com.example.tasama.util.findRayIntersection
import com.example.tasama.util.applyUIAvoidance
import com.example.tasama.util.disableHardwareBitmaps
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlin.math.*
import com.example.tasama.R
import kotlin.time.Clock
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.datetime.*

val MapHeaderHeight = 88.dp

@Composable
fun animateLatLngAsState(
    targetValue: LatLng,
    animationSpec: AnimationSpec<LatLng> = tween(durationMillis = 1500, easing = LinearEasing)
): State<LatLng> {
    val typeConverter = remember {
        TwoWayConverter<LatLng, AnimationVector2D>(
            convertToVector = { AnimationVector2D(it.latitude.toFloat(), it.longitude.toFloat()) },
            convertFromVector = { LatLng(it.v1.toDouble(), it.v2.toDouble()) }
        )
    }
    
    // Use a state that is initialized with the first non-zero targetValue to avoid animating from (0,0)
    val animatable = remember { 
        Animatable(targetValue, typeConverter) 
    }

    LaunchedEffect(targetValue) {
        if (animatable.value.latitude == 0.0 && animatable.value.longitude == 0.0 && 
            (targetValue.latitude != 0.0 || targetValue.longitude != 0.0)) {
            animatable.snapTo(targetValue)
        } else {
            animatable.animateTo(targetValue, animationSpec)
        }
    }

    return animatable.asState()
}

@Composable
fun rememberUpdatedMarkerState(position: LatLng): MarkerState {
    val state = remember { MarkerState(position = position) }
    LaunchedEffect(position) {
        state.position = position
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place>,
    stories: List<Story>,
    anniversaryDate: Long?,
    distanceInfo: DistanceInfo?,
    weatherInfo: WeatherInfo?,
    isWeatherLoading: Boolean,
    isPartnerComingToMe: Boolean,
    isDistanceLoading: Boolean,
    distanceError: String?,
    onEditAnniversary: () -> Unit,
    onAddPlace: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onAddStory: (Story, List<ByteArray>) -> Unit,
    onDeleteStory: (Story) -> Unit,
    onUpdateStory: (Story, List<ByteArray>) -> Unit,
    onUnlink: () -> Unit,
    onSelectStory: (Story?) -> Unit,
    selectedStoryForMap: Story?,
    onClearSelectedStory: () -> Unit,
    settings: AppSettings,
    onOpenSettings: () -> Unit
) {
    val density = LocalDensity.current

    var showDeletePlaceDialog by remember { mutableStateOf<Place?>(null) }
    var showDeleteStoryDialog by remember { mutableStateOf<Story?>(null) }
    var isPartnerInfoVisible by remember { mutableStateOf(false) }
    var showAddPlaceSheet by remember { mutableStateOf<LatLng?>(null) }
    var showAddStorySheet by remember { mutableStateOf<LatLng?>(null) }
    var editingPlace by remember { mutableStateOf<Place?>(null) }
    var editingStory by remember { mutableStateOf<Story?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isFollowModeEnabled by rememberSaveable { mutableStateOf(true) }
    
    var hasInitialFit by remember { mutableStateOf(false) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val followZoom = 16.5f

    // Shared ticker for status updates (Optimization 1)
    var currentTime by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentTime = Clock.System.now().toEpochMilliseconds()
        }
    }

    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val mapProperties = remember(isDarkTheme, settings.trafficLayerEnabled) {
        MapProperties(
            mapStyleOptions = if (isDarkTheme) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
            } else null,
            isTrafficEnabled = settings.trafficLayerEnabled
        )
    }

    val myLocation = remember(currentUser?.latitude, currentUser?.longitude) {
        if (currentUser?.latitude != null && currentUser.longitude != null) {
            LatLng(currentUser.latitude, currentUser.longitude)
        } else null
    }

    val partnerLocation = remember(partner?.latitude, partner?.longitude) {
        if (partner?.latitude != null && partner.longitude != null) {
            LatLng(partner.latitude, partner.longitude)
        } else null
    }

    val animatedMyLocation by animateLatLngAsState(
        targetValue = myLocation ?: LatLng(0.0, 0.0),
        animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
    )
    val animatedPartnerLocation by animateLatLngAsState(
        targetValue = partnerLocation ?: LatLng(0.0, 0.0),
        animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
    )

    val currentMyLocation = if (myLocation != null) animatedMyLocation else null
    val currentPartnerLocation = if (partnerLocation != null) animatedPartnerLocation else null

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(partnerLocation ?: LatLng(-6.2000, 106.8166), 12f)
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false
        )
    }

    val distance by remember(currentMyLocation, currentPartnerLocation, distanceInfo) {
        derivedStateOf {
            if (currentMyLocation != null && currentPartnerLocation != null &&
                currentMyLocation.latitude != 0.0 && currentPartnerLocation.latitude != 0.0) {
                calculateDistance(Location(currentMyLocation.latitude, currentMyLocation.longitude), Location(currentPartnerLocation.latitude, currentPartnerLocation.longitude))
            } else null
        }
    }

    val isTogether by remember(distance) {
        derivedStateOf { (distance ?: Double.MAX_VALUE) < 25.0 }
    }

    val fitPaddingPx = with(density) { 100.dp.toPx().toInt() }
    val fitMarkers = {
        if (isMapLoaded && mapSize != IntSize.Zero) {
            isFollowModeEnabled = false
            val hasMyLoc = currentMyLocation != null && currentMyLocation.latitude != 0.0
            val hasPartnerLoc = currentPartnerLocation != null && currentPartnerLocation.latitude != 0.0

            scope.launch {
                val update = when {
                    hasMyLoc && hasPartnerLoc -> {
                        if (currentMyLocation == currentPartnerLocation) {
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.builder().target(currentMyLocation).zoom(followZoom).bearing(0f).tilt(0f).build()
                            )
                        } else {
                            val bounds = LatLngBounds.Builder().include(currentMyLocation).include(
                                currentPartnerLocation
                            ).build()
                            CameraUpdateFactory.newLatLngBounds(bounds, fitPaddingPx)
                        }
                    }
                    hasPartnerLoc -> {
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.builder().target(currentPartnerLocation).zoom(followZoom).bearing(0f).tilt(0f).build()
                        )
                    }
                    hasMyLoc -> {
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.builder().target(currentMyLocation).zoom(followZoom).bearing(0f).tilt(0f).build()
                        )
                    }
                    else -> null
                }
                update?.let { cameraPositionState.animate(it) }
            }
        }
    }

    LaunchedEffect(isMapLoaded, mapSize, currentMyLocation, currentPartnerLocation) {
        if (isMapLoaded && mapSize != IntSize.Zero && !hasInitialFit && (currentMyLocation != null || currentPartnerLocation != null)) {
            fitMarkers()
            hasInitialFit = true
        }
    }

    // Smart Follow Mode: Disable on user gesture
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            isFollowModeEnabled = false
        }
    }

    // Smart Follow Mode: Synchronized follow
    LaunchedEffect(currentPartnerLocation, isFollowModeEnabled) {
        if (isFollowModeEnabled && currentPartnerLocation != null) {
            // Using move() instead of animate() to keep the camera perfectly 
            // locked to the animated avatar position without any extra lag.
            cameraPositionState.move(CameraUpdateFactory.newLatLng(currentPartnerLocation))
        }
    }

    // Story Selection Logic
    LaunchedEffect(selectedStoryForMap) {
        selectedStoryForMap?.let { story ->
            isFollowModeEnabled = false
            
            if (story.latitude != 0.0) {
                val storyLatLng = LatLng(story.latitude, story.longitude)
                scope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(storyLatLng, 15f),
                        1000
                    )
                    delay(1500)
                    onClearSelectedStory()
                }
            }
        }
    }

    // Derived states for real-time intersection and visibility (Optimization 3)
    val markerData by remember(currentMyLocation, currentPartnerLocation, mapSize, cameraPositionState.isMoving, density) {
        derivedStateOf {
            val projection = cameraPositionState.projection ?: return@derivedStateOf null

            if (mapSize == IntSize.Zero || (currentMyLocation == null && currentPartnerLocation == null)) {
                return@derivedStateOf null
            }

            val width = mapSize.width.toFloat()
            val height = mapSize.height.toFloat()

            val pMe = if (currentMyLocation != null) projection.toScreenLocation(currentMyLocation).let { Offset(it.x.toFloat(), it.y.toFloat()) } else Offset.Zero
            val pPartner = if (currentPartnerLocation != null) projection.toScreenLocation(currentPartnerLocation).let { Offset(it.x.toFloat(), it.y.toFloat()) } else Offset.Zero

            val headerHeightPx = with(density) { MapHeaderHeight.toPx() }
            val indicatorRadius = with(density) { 28.dp.toPx() } // half of 56.dp
            val edgeMargin = with(density) { 8.dp.toPx() }

            // Buffer to determine visibility and avoid edge flickering
            val buffer = 5f

            // Check visibility using screen coordinates AND avoidance areas
            fun isPointOffScreen(p: Offset, latLng: LatLng?): Boolean {
                if (latLng == null) return false
                
                // 1. Physical Screen Bounds
                if (p.x < buffer || p.x > width - buffer || p.y < buffer || p.y > height - buffer) return true
                
                // 3. UI Avoidance Logic - Header (Full-width top area)
                if (p.y < headerHeightPx + indicatorRadius + edgeMargin) return true
                
                // 4. UI Avoidance Logic - Bottom Right (Previously FAB area)
                if (p.y > height - 256f - indicatorRadius - edgeMargin && 
                    p.x > width - 56f - indicatorRadius - edgeMargin) return true
                    
                return false
            }

            val isMeVisible = !isPointOffScreen(pMe, currentMyLocation)
            val isPartnerVisible = !isPointOffScreen(pPartner, currentPartnerLocation)

            var myEdge: Offset? = null
            var partnerEdge: Offset? = null
            var polyStart = currentMyLocation
            var polyEnd = currentPartnerLocation
            val showPolyline = currentMyLocation != null && currentPartnerLocation != null

            if (showPolyline && (!isMeVisible || !isPartnerVisible)) {
                val intersections = clipSegmentToRect(pMe, pPartner, width, height)

                if (intersections != null) {
                    val (clippedMe, clippedPartner) = intersections

                    if (!isMeVisible) {
                        val finalPos = applyUIAvoidance(clippedMe, width, height, edgeMargin, indicatorRadius, headerHeightPx, 56f, 256f)
                        myEdge = finalPos
                        polyStart = projection.fromScreenLocation(Point(finalPos.x.toInt(), finalPos.y.toInt()))
                    }
                    if (!isPartnerVisible) {
                        val finalPos = applyUIAvoidance(clippedPartner, width, height, edgeMargin, indicatorRadius, headerHeightPx, 56f, 256f)
                        partnerEdge = finalPos
                        polyEnd = projection.fromScreenLocation(Point(finalPos.x.toInt(), finalPos.y.toInt()))
                    }
                } else {
                    // Segment doesn't cross the screen. Place indicators using rays from center.
                    val center = Offset(width / 2, height / 2)
                    if (!isMeVisible) {
                        val edge = findRayIntersection(center, pMe, width, height)
                        val finalPos = applyUIAvoidance(edge, width, height, edgeMargin, indicatorRadius, headerHeightPx, 56f, 256f)
                        myEdge = finalPos
                        polyStart = projection.fromScreenLocation(Point(finalPos.x.toInt(), finalPos.y.toInt()))
                    }
                    if (!isPartnerVisible) {
                        val edge = findRayIntersection(center, pPartner, width, height)
                        val finalPos = applyUIAvoidance(edge, width, height, edgeMargin, indicatorRadius, headerHeightPx, 56f, 256f)
                        partnerEdge = finalPos
                        polyEnd = projection.fromScreenLocation(Point(finalPos.x.toInt(), finalPos.y.toInt()))
                    }
                }
            } else if (!showPolyline) {
                // Handle single marker off-screen indicators
                val center = Offset(width / 2, height / 2)
                if (currentMyLocation != null && !isMeVisible) {
                    val edge = findRayIntersection(center, pMe, width, height)
                    myEdge = applyUIAvoidance(edge, width, height, edgeMargin, indicatorRadius, headerHeightPx, 56f, 256f)
                }
                if (currentPartnerLocation != null && !isPartnerVisible) {
                    val edge = findRayIntersection(center, pPartner, width, height)
                    partnerEdge = applyUIAvoidance(edge, width, height, edgeMargin, indicatorRadius, headerHeightPx, 56f, 256f)
                }
            }

            val myAngle = if (myEdge != null) {
                val dx = pMe.x - myEdge.x
                val dy = pMe.y - myEdge.y
                (atan2(dy.toDouble(), dx.toDouble()) * 180 / PI).toFloat()
            } else 0f

            val partnerAngle = if (partnerEdge != null) {
                val dx = pPartner.x - partnerEdge.x
                val dy = pPartner.y - partnerEdge.y
                (atan2(dy.toDouble(), dx.toDouble()) * 180 / PI).toFloat()
            } else 0f

            MarkerVisibilityData(
                isMeVisible = isMeVisible,
                isPartnerVisible = isPartnerVisible,
                myEffectiveLocation = polyStart ?: LatLng(0.0, 0.0),
                partnerEffectiveLocation = polyEnd ?: LatLng(0.0, 0.0),
                myEdgePoint = myEdge,
                partnerEdgePoint = partnerEdge,
                myAngle = myAngle,
                partnerAngle = partnerAngle,
                showPolyline = showPolyline,
                partnerScreenPos = pPartner
            )
        }
    }


    Box(modifier = modifier.onSizeChanged { mapSize = it }) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            onMapLoaded = { isMapLoaded = true },
            onMapClick = { latLng ->
                println("DEBUG: Map clicked at $latLng")
                isPartnerInfoVisible = false
                onClearSelectedStory()
                
                val clickedStory = stories.find { story ->
                    calculateDistance(Location(latLng.latitude, latLng.longitude), Location(story.latitude, story.longitude)) <= 50.0
                }
                
                if (clickedStory != null) {
                    editingStory = clickedStory
                    showAddStorySheet = LatLng(clickedStory.latitude, clickedStory.longitude)
                } else {
                    val clickedPlace = places.find { place ->
                        val distanceValue = calculateDistance(Location(latLng.latitude, latLng.longitude), Location(place.latitude, place.longitude))
                        distanceValue <= maxOf(place.radius, 50.0) 
                    }
                    if (clickedPlace != null) {
                        println("DEBUG: Map click detected place: ${clickedPlace.name}")
                        editingPlace = clickedPlace
                        showAddPlaceSheet = LatLng(clickedPlace.latitude, clickedPlace.longitude)
                    }
                }
            },
            contentPadding = WindowInsets(0).asPaddingValues(),
            properties = mapProperties,
            onMapLongClick = { latLng ->
                val existingStory = stories.find { story ->
                    calculateDistance(Location(latLng.latitude, latLng.longitude), Location(story.latitude, story.longitude)) <= 50.0
                }
                if (existingStory != null) {
                    showDeleteStoryDialog = existingStory
                    return@GoogleMap
                }

                val existingPlace = places.find { place ->
                    calculateDistance(Location(latLng.latitude, latLng.longitude), Location(place.latitude, place.longitude)) <= place.radius
                }
                if (existingPlace != null) {
                    showDeletePlaceDialog = existingPlace
                } else {
                    editingPlace = null
                    editingStory = null
                    // Default to showing a selection dialog or the story sheet?
                    // Let's modify the flow to show a selection sheet.
                    showAddStorySheet = latLng
                }
            }
        ) {
            // Combined Together Marker
            if (isTogether && currentMyLocation != null && currentPartnerLocation != null) {
                val midpoint = LatLng(
                    (currentMyLocation.latitude + currentPartnerLocation.latitude) / 2,
                    (currentMyLocation.longitude + currentPartnerLocation.longitude) / 2
                )
                
                MarkerComposable(
                    keys = arrayOf<Any>(currentUser?.id ?: "me", partner?.id ?: "partner"),
                    state = rememberUpdatedMarkerState(position = midpoint),
                    anchor = Offset(0.5f, 0.5f),
                    visible = (markerData?.isMeVisible ?: true),
                    zIndex = 2f
                ) {
                    CombinedUserMarker(
                        currentUser = currentUser,
                        partner = partner
                    )
                }
            }

            currentMyLocation?.let { location ->
                val markerState = rememberUpdatedMarkerState(position = location)
                val status = rememberPartnerStatus(currentUser, currentTime)
                MarkerComposable(
                    keys = arrayOf<Any>(
                        currentUser?.avatarUrl ?: "",
                        currentUser?.name ?: "",
                        currentUser?.batteryLevel ?: 0f,
                        currentUser?.isCharging ?: false,
                        currentUser?.connectionType ?: "",
                        status,
                        isTogether
                    ),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f),
                    visible = (markerData?.isMeVisible ?: true) && !isTogether
                ) {
                    UserMarker(user = currentUser, isMe = true, status = status)
                }
            }

            currentPartnerLocation?.let { location ->
                val status = rememberPartnerStatus(partner, currentTime)
                val markerState = rememberUpdatedMarkerState(position = location)
                MarkerComposable(
                    keys = arrayOf<Any>(
                        partner?.avatarUrl ?: "",
                        partner?.name ?: "",
                        partner?.batteryLevel ?: 0f,
                        partner?.isCharging ?: false,
                        partner?.connectionType ?: "",
                        partner?.speed ?: 0f,
                        status,
                        isTogether
                    ),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f),
                    visible = (markerData?.isPartnerVisible ?: true) && !isTogether,
                    onClick = {
                        isPartnerInfoVisible = !isPartnerInfoVisible
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLng(location))
                        }
                        true
                    }
                ) {
                    UserMarker(user = partner, isMe = false, status = status)
                }
            }

            if (settings.placesEnabled) {
                places.forEach { place ->
                    val placeLatLng = LatLng(place.latitude, place.longitude)
                    val placeColor = place.color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
                    
                    Circle(
                        center = placeLatLng,
                        radius = place.radius,
                        fillColor = placeColor.copy(alpha = 0.2f),
                        strokeColor = placeColor,
                        strokeWidth = 2f
                    )
                    val markerState = rememberUpdatedMarkerState(position = placeLatLng)
                    MarkerComposable(
                        keys = arrayOf<Any>(place.id, place.name, place.latitude, place.longitude, place.color ?: 0L, place.iconName ?: ""),
                        state = markerState,
                        anchor = Offset(0.5f, 0.5f),
                        title = place.name,
                        onClick = {
                            println("DEBUG: Marker content clicked for place: ${place.name}")
                            editingPlace = place
                            showAddPlaceSheet = placeLatLng
                            true
                        },
                        onInfoWindowLongClick = {
                            showDeletePlaceDialog = place
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(2.dp, placeColor, CircleShape)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (place.iconName) {
                                "Home" -> Icons.Default.Home
                                "Work" -> Icons.Default.Work
                                "School" -> Icons.Default.School
                                "Shopping" -> Icons.Default.ShoppingCart
                                "Restaurant" -> Icons.Default.Restaurant
                                "Gym" -> Icons.Default.FitnessCenter
                                "Hospital" -> Icons.Default.LocalHospital
                                "Park" -> Icons.Default.Park
                                else -> Icons.Default.LocationOn
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = placeColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            if (settings.storyMarkersEnabled) {
                stories.forEach { story ->
                    val storyLatLng = LatLng(story.latitude, story.longitude)
                    val markerState = rememberUpdatedMarkerState(position = storyLatLng)
                    val isSelected = selectedStoryForMap?.id == story.id
                    
                    var isLoaded by remember(story.id) { mutableStateOf(false) }
                    
                    MarkerComposable(
                        keys = arrayOf<Any>(
                            story.id, 
                            story.title, 
                            story.latitude, 
                            story.longitude, 
                            story.photoUrls.firstOrNull() ?: "", 
                            isSelected,
                            isLoaded
                        ),
                        state = markerState,
                        anchor = Offset(0.5f, 0.5f),
                        title = story.title,
                        zIndex = if (isSelected) 3f else 1f,
                        onClick = {
                            editingStory = story
                            showAddStorySheet = storyLatLng
                            true
                        }
                    ) {
                        StoryMarker(
                            story = story, 
                            isSelected = isSelected,
                            onImageLoaded = { isLoaded = it }
                        )
                    }
                }
            }

            markerData?.let { data ->
                if (data.showPolyline) {
                    // Straight Dashed Line
                    Polyline(
                        points = listOf(data.myEffectiveLocation, data.partnerEffectiveLocation),
                        color = MaterialTheme.colorScheme.primary,
                        width = 10f,
                        pattern = listOf(Dash(30f), Gap(20f))
                    )

                    // Distance label - follows the midpoint
                    val labelPosition = LatLng(
                        (data.myEffectiveLocation.latitude + data.partnerEffectiveLocation.latitude) / 2,
                        (data.myEffectiveLocation.longitude + data.partnerEffectiveLocation.longitude) / 2
                    )

                    MarkerComposable(
                        keys = arrayOf<Any>(distance ?: 0.0, distanceInfo ?: 0, labelPosition),
                        state = rememberUpdatedMarkerState(position = labelPosition),
                        anchor = Offset(0.5f, 0.5f),
                        zIndex = 1f
                    ) {
                        val blurRadius = 8.dp
                        val shadowColor = if (isDarkTheme) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f)
                        val bubbleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(
                                modifier = Modifier.matchParentSize()
                            ) {
                                val blurRadiusPx = blurRadius.toPx()
                                drawIntoCanvas { canvas ->
                                    val paint = android.graphics.Paint().apply {
                                        color = bubbleColor.toArgb()
                                        isAntiAlias = true
                                        setShadowLayer(blurRadiusPx, 0f, 0f, shadowColor.toArgb())
                                    }
                                    // Draw rect inset by blurRadius to ensure the shadow isn't clipped
                                    val rect = android.graphics.RectF(
                                        blurRadiusPx, 
                                        blurRadiusPx, 
                                        size.width - blurRadiusPx, 
                                        size.height - blurRadiusPx
                                    )
                                    val cornerRadius = 16.dp.toPx()
                                    canvas.nativeCanvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .padding(blurRadius)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                val distanceText = if ((distance ?: 0.0) < 1000) "${distance?.toInt() ?: 0}m" else "${((distance ?: 0.0) / 1000).format(1)}km"

                                Text(
                                    text = distanceText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }



        if (showAddPlaceSheet != null) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showAddPlaceSheet = null
                    editingPlace = null
                },
                sheetState = bottomSheetState
            ) {
                AddPlaceSheetContent(
                    location = showAddPlaceSheet!!,
                    initialPlace = editingPlace,
                    onAddPlace = { place: Place ->
                        onAddPlace(place)
                        showAddPlaceSheet = null
                        editingPlace = null
                    }
                )
            }
        }

        if (showAddStorySheet != null) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showAddStorySheet = null
                    editingStory = null
                },
                sheetState = bottomSheetState
            ) {
                AddStorySheetContent(
                    location = showAddStorySheet!!,
                    initialStory = editingStory,
                    onAddStory = { story, bytes ->
                        onAddStory(story, bytes)
                        showAddStorySheet = null
                        editingStory = null
                    },
                    onUpdateStory = { story, bytes ->
                        onUpdateStory(story, bytes)
                        showAddStorySheet = null
                        editingStory = null
                    },
                    onCreatePlace = { loc, prefillName, prefillAddr ->
                        showAddStorySheet = null
                        editingStory = null
                        editingPlace = Place(
                            name = prefillName,
                            address = prefillAddr,
                            latitude = loc.latitude,
                            longitude = loc.longitude
                        )
                        showAddPlaceSheet = loc
                    }
                )
            }
        }


        // Off-screen markers
        markerData?.let { data ->
            val isPartnerOffScreen = !data.isPartnerVisible && data.partnerEdgePoint != null && currentPartnerLocation != null
            val isMeOffScreen = !data.isMeVisible && data.myEdgePoint != null && currentMyLocation != null

            AnimatedVisibility(
                visible = isPartnerOffScreen,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                if (currentPartnerLocation != null && data.partnerEdgePoint != null) {
                    OffScreenMarker(
                        edgePoint = data.partnerEdgePoint,
                        angle = data.partnerAngle,
                        user = partner,
                        showArrow = true,
                        onTap = {
                            scope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLng(currentPartnerLocation))
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = isMeOffScreen,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                if (currentMyLocation != null && data.myEdgePoint != null) {
                    OffScreenMarker(
                        edgePoint = data.myEdgePoint,
                        angle = data.myAngle,
                        user = currentUser,
                        showArrow = true,
                        onTap = {
                            scope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLng(currentMyLocation))
                            }
                        }
                    )
                }
            }
        }

        // Partner Info Card Overlay
        val partnerScreenPos = markerData?.partnerScreenPos
        
        // Show info if manually clicked
        val showCard = isPartnerInfoVisible && partnerScreenPos != null && showAddPlaceSheet == null
        
        if (showCard && partner != null) {
            val markerRadiusPx = with(density) { 24.dp.toPx() }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { isPartnerInfoVisible = false })
                    }
            ) {
                var cardSize by remember { mutableStateOf(IntSize.Zero) }
                
                Box(
                    modifier = Modifier
                        .onSizeChanged { cardSize = it }
                        .offset {
                            // Calculate the target position for the card's bottom-center anchor (the pointer tip)
                            val targetX = partnerScreenPos.x.toInt()
                            val targetY = (partnerScreenPos.y - markerRadiusPx - 4).toInt()
                            
                            val halfWidth = cardSize.width / 2
                            val margin = 16.dp.toPx().toInt()
                            
                            // Clamp the target position so the card remains fully on screen
                            val clampedX = targetX.coerceIn(
                                halfWidth + margin, 
                                mapSize.width - halfWidth - margin
                            )
                            val clampedY = targetY.coerceIn(
                                cardSize.height + margin, 
                                mapSize.height - margin
                            )
                            
                            // Position the card so its bottom-center (pointer tip) is at (clampedX, clampedY)
                            IntOffset(clampedX - halfWidth, clampedY - cardSize.height)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* Consume tap to prevent map dismissal */ }
                        )
                ) {
                    PartnerStatusCard(
                        user = partner,
                        status = rememberPartnerStatus(partner, currentTime),
                        currentTime = currentTime,
                        distanceInfo = distanceInfo,
                        isPartnerComingToMe = isPartnerComingToMe,
                        isDistanceLoading = isDistanceLoading,
                        distanceError = distanceError
                    )
                }
            }
        }

        // New Header Layout: Weather(Left), Dashboard(Center), Settings(Right)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 14.dp)
        ) {
            if (settings.weatherWidgetEnabled) {
                WeatherWidget(
                    modifier = Modifier.align(Alignment.CenterStart),
                    weatherInfo = weatherInfo,
                    isLoading = isWeatherLoading
                )
            }

            if (settings.dashboardEnabled) {
                PartnerDashboard(
                    modifier = Modifier.align(Alignment.Center),
                    anniversaryDate = anniversaryDate,
                    currentTime = currentTime,
                    onEditAnniversary = onEditAnniversary
                )
            }

            // Map Settings Button (Top Right, standalone circular button)
            Surface(
                onClick = { onOpenSettings() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                shape = CircleShape,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }


        // Floating action buttons container
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End
        ) {

            // Follow Mode Button
            AnimatedVisibility(
                visible = !isFollowModeEnabled && partnerLocation != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            partnerLocation?.let {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, followZoom), 1000)
                            }
                            isFollowModeEnabled = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Follow Partner")
                }
            }

            // Recenter/Fit Button
            SmallFloatingActionButton(
                onClick = { fitMarkers() },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit Markers")
            }
        }

        if (showDeletePlaceDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeletePlaceDialog = null },
                title = { Text("Delete Place") },
                text = { Text("Are you sure you want to delete \"${showDeletePlaceDialog?.name}\"? You will no longer receive notifications for this location.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeletePlaceDialog?.let { onDeletePlace(it.id) }
                            showDeletePlaceDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeletePlaceDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteStoryDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteStoryDialog = null },
                title = { Text("Delete Story") },
                text = { Text("Are you sure you want to delete \"${showDeleteStoryDialog?.title}\"? This memory will be removed for both you and your partner.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteStoryDialog?.let { onDeleteStory(it) }
                            showDeleteStoryDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteStoryDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AddPlaceSheetContent(
    location: LatLng,
    initialPlace: Place? = null,
    onAddPlace: (Place) -> Unit
) {
    var name by remember { mutableStateOf(initialPlace?.name ?: "") }
    var address by remember { mutableStateOf(initialPlace?.address ?: "Fetching address...") }
    val radius = initialPlace?.radius?.toFloat() ?: 200f
    var notifyOnEntry by remember { mutableStateOf(initialPlace?.notifyOnEntry ?: true) }
    var notifyOnExit by remember { mutableStateOf(initialPlace?.notifyOnExit ?: true) }
    var selectedColor by remember { 
        mutableStateOf(initialPlace?.color?.let { Color(it.toInt()) } ?: Color(0xFF2196F3)) 
    }
    var selectedIconName by remember { mutableStateOf(initialPlace?.iconName ?: "Location") }

    val colors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFF44336), // Red
        Color(0xFFFFC107), // Amber
        Color(0xFF9C27B0), // Purple
        Color(0xFF795548)  // Brown
    )

    val icons = listOf(
        "Location" to Icons.Default.LocationOn,
        "Home" to Icons.Default.Home,
        "Work" to Icons.Default.Work,
        "School" to Icons.Default.School,
        "Shopping" to Icons.Default.ShoppingCart,
        "Restaurant" to Icons.Default.Restaurant,
        "Gym" to Icons.Default.FitnessCenter,
        "Hospital" to Icons.Default.LocalHospital,
        "Park" to Icons.Default.Park
    )

    LaunchedEffect(location, initialPlace) {
        if (initialPlace != null) return@LaunchedEffect
        
        delay(500) // Debounce rapid location changes during interaction
        
        val result = reverseGeocode(location.latitude, location.longitude)
        if (result != null) {
            address = result
            if (name.isEmpty()) {
                // Heuristic: if the result contains a comma, the part before it might be a good name
                name = result.split(",").firstOrNull() ?: result
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (initialPlace == null) "Add Place Marker" else "Edit Place Marker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Get notified when your partner enters or leaves this area.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Place Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NotificationChip(
                label = "Entry",
                selected = notifyOnEntry,
                onClick = { notifyOnEntry = !notifyOnEntry },
                modifier = Modifier.weight(1f)
            )
            NotificationChip(
                label = "Exit",
                selected = notifyOnExit,
                onClick = { notifyOnExit = !notifyOnExit },
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Style", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colors.forEach { color ->
                    val isSelected = selectedColor == color
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .padding(if (isSelected) 4.dp else 0.dp)
                                .background(color, CircleShape)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                icons.forEach { (iconName, icon) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selectedIconName == iconName) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { selectedIconName = iconName },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = iconName,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedIconName == iconName) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                onAddPlace(
                    Place(
                        id = initialPlace?.id ?: "",
                        name = name.ifBlank { address },
                        address = address,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radius = radius.toDouble(),
                        notifyOnEntry = notifyOnEntry,
                        notifyOnExit = notifyOnExit,
                        color = selectedColor.toArgb().toLong() and 0xFFFFFFFFL,
                        iconName = selectedIconName
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() || address != "Fetching address...",
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (initialPlace == null) "Save Place" else "Update Place")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun NotificationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun OffScreenMarker(
    edgePoint: Offset, // Expected to be already avoided
    angle: Float,
    user: User?,
    showArrow: Boolean = true,
    onTap: () -> Unit
) {
    val indicatorSize = 56.dp
    val density = LocalDensity.current
    val indicatorSizePx = with(density) { indicatorSize.toPx() }
    val half = indicatorSizePx / 2f

    val finalX = edgePoint.x
    val finalY = edgePoint.y

    // Breathing effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val isDarkTheme = LocalIsDarkTheme.current
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val shadowColor = if (isDarkTheme) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.4f)
    val bubbleColorArgb = bubbleColor.toArgb()
    val shadowColorArgb = shadowColor.toArgb()

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (finalX - half).toInt(),
                    (finalY - half).toInt()
                )
            }
            .size(indicatorSize)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        // The "Bubble" Shape (Theme-aware Background with Pointer)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bubbleRadius = (indicatorSizePx - 8.dp.toPx()) / 2
            val pointerWidth = 12.dp.toPx()
            val pointerHeight = 12.dp.toPx()
            val blurRadius = 8.dp.toPx()

            val combinedPath = android.graphics.Path().apply {
                addCircle(center.x, center.y, bubbleRadius, android.graphics.Path.Direction.CW)
                if (showArrow) {
                    val pointerPath = android.graphics.Path().apply {
                        moveTo(center.x + bubbleRadius - 2.dp.toPx(), center.y - pointerWidth / 2)
                        lineTo(center.x + bubbleRadius + pointerHeight, center.y)
                        lineTo(center.x + bubbleRadius - 2.dp.toPx(), center.y + pointerWidth / 2)
                        close()
                    }
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(angle, center.x, center.y)
                    pointerPath.transform(matrix)
                    addPath(pointerPath)
                }
            }

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = bubbleColorArgb
                    isAntiAlias = true
                    setShadowLayer(blurRadius, 0f, 0f, shadowColorArgb)
                }
                canvas.nativeCanvas.drawPath(combinedPath, paint)
            }
        }

        // The Profile Picture Circle with Theme-aware Ring
        Box(
            modifier = Modifier
                .size(indicatorSize - 16.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(
                user = user,
                modifier = Modifier.fillMaxSize(),
                showInitials = user?.avatarUrl == null
            )
        }
    }
}

data class MarkerVisibilityData(
    val isMeVisible: Boolean,
    val isPartnerVisible: Boolean,
    val myEffectiveLocation: LatLng,
    val partnerEffectiveLocation: LatLng,
    val myEdgePoint: Offset?,
    val partnerEdgePoint: Offset?,
    val myAngle: Float,
    val partnerAngle: Float,
    val showPolyline: Boolean,
    val partnerScreenPos: Offset?
)

@Composable
fun ConnectionStatusBadge(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        ConnectionStatus.LIVE -> "Live" to Color(0xFF4CAF50)
        ConnectionStatus.WEAK -> "Weak GPS" to Color(0xFFFF9800)
        ConnectionStatus.OFFLINE -> "No Signal" to Color(0xFFF44336)
    }

    Surface(
        color = color,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp),
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun UserMarker(
    user: User?,
    isMe: Boolean,
    status: ConnectionStatus
) {
    val speed = user?.speed ?: 0f
    val isMoving = speed > 0.3f && status != ConnectionStatus.OFFLINE

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(76.dp),
        contentAlignment = Alignment.TopCenter
    ) {

        // Ripple
        if (isMoving) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = if (isMe)
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }

        // Avatar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 8.dp)
                .size(48.dp)
                .background(
                    if (isMe)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.primary,
                    CircleShape
                )
                .padding(2.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(
                user = user,
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                showInitials = user?.avatarUrl == null
            )
        }

        // Connection Badge
        if (!isMe && user != null) {
            ConnectionStatusBadge(
                status = status,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 48.dp)
            )
        }
    }
}

@Composable
fun CombinedUserMarker(
    currentUser: User?,
    partner: User?
) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(110.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background Glow/Circle
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Heart icons above
            Row(
                modifier = Modifier.offset(y = 4.dp),
                horizontalArrangement = Arrangement.spacedBy((-4).dp)
            ) {
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF4081))
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp).offset(y = (-4).dp), tint = Color(0xFFFF4081))
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF4081))
            }

            // Avatars Bubble
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(2.dp, Color(0xFFFF4081).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clip(CircleShape)
                    ) {
                        UserAvatar(
                            user = currentUser,
                            modifier = Modifier.fillMaxSize(),
                            showInitials = currentUser?.avatarUrl == null
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clip(CircleShape)
                    ) {
                        UserAvatar(
                            user = partner,
                            modifier = Modifier.fillMaxSize(),
                            showInitials = partner?.avatarUrl == null
                        )
                    }
                }
            }

            // Small triangle pointing down
            val surfaceColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(8.dp)
                    .offset(y = (-1).dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2, size.height)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = surfaceColor
                    )
                }
            }
            
            // Location Name (Optional, similar to reference)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.offset(y = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(10.dp), tint = Color(0xFFFF4081))
                    Text(
                        text = "Together",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StoryMarker(
    story: Story, 
    isSelected: Boolean = false,
    onImageLoaded: (Boolean) -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "markerScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 12.dp else 4.dp,
        label = "markerElevation"
    )

    var hasAppeared by remember { mutableStateOf(true) }
    val appearanceScale by animateFloatAsState(
        targetValue = if (hasAppeared) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "appearanceScale"
    )

    LaunchedEffect(story.id) {
        hasAppeared = true
    }

    // Image loading state for MarkerComposable re-rendering
    val imageUrl = story.photoUrls.firstOrNull()
    val context = LocalPlatformContext.current
    
    val painter = if (!imageUrl.isNullOrEmpty()) {
        val request = remember(imageUrl, context) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .disableHardwareBitmaps()
                .crossfade(false)
                .build()
        }
        rememberAsyncImagePainter(
            model = request,
            contentScale = ContentScale.Crop
        )
    } else null

    val imageState = painter?.state?.collectAsState()?.value
    val isLoaded = imageState is AsyncImagePainter.State.Success
    
    LaunchedEffect(isLoaded) {
        onImageLoaded(isLoaded)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                val finalScale = scale * appearanceScale
                scaleX = finalScale
                scaleY = finalScale
                shadowElevation = elevation.toPx()
                shape = CircleShape
                clip = false
            }
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(
                width = if (isSelected) 3.dp else 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFFF4081),
                shape = CircleShape
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            // Re-composition trigger when image is loaded
            // This ensures that MarkerComposable takes a new snapshot
            if (!isLoaded) {
                // Background/Placeholder while loading
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF4081),
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Date badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFFF4081), CircleShape)
                .border(1.dp, Color.White, CircleShape)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            val dateStr = remember(story.date) {
                val instant = Instant.fromEpochMilliseconds(story.date)
                val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${dateTime.day}/${dateTime.month.number}"
            }
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStorySheetContent(
    location: LatLng,
    initialStory: Story? = null,
    onAddStory: (Story, List<ByteArray>) -> Unit,
    onUpdateStory: (Story, List<ByteArray>) -> Unit,
    onCreatePlace: (LatLng, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initialStory?.title ?: "") }
    var description by remember { mutableStateOf(initialStory?.description ?: "") }
    var dateMillis by remember { mutableLongStateOf(initialStory?.date ?: Clock.System.now().toEpochMilliseconds()) }
    var category by remember { mutableStateOf(initialStory?.category ?: "Memory") }
    var address by remember { mutableStateOf(initialStory?.address ?: "Fetching address...") }
    val photoUrls = remember { mutableStateListOf<String>().apply { initialStory?.photoUrls?.let { addAll(it) } } }
    val newPhotos = remember { mutableStateListOf<Pair<String, ByteArray>>() }
    
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(location, initialStory) {
        if (initialStory != null) return@LaunchedEffect
        val result = reverseGeocode(location.latitude, location.longitude)
        if (result != null) {
            address = result
        }
    }

    val pickerLauncher = rememberFilePickerLauncher(
        type = PickerType.Image,
        onResult = { file: PlatformFile? ->
            file?.let { platformFile ->
                scope.launch {
                    val bytes = platformFile.readBytes()
                    // Use a unique ID instead of potentially null path
                    val tempId = "temp_${Clock.System.now().toEpochMilliseconds()}"
                    newPhotos.add(tempId to bytes)
                    photoUrls.add(tempId)
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (initialStory == null) "New Story" else "Edit Story",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            val dateStr = remember(dateMillis) {
                val instant = Instant.fromEpochMilliseconds(dateMillis)
                val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${dateTime.day} ${dateTime.month.name}, ${dateTime.year}"
            }
            Text(text = dateStr, style = MaterialTheme.typography.bodyLarge)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Photos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            
            if (photoUrls.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { photoUrls.size })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 8.dp
                    ) { page ->
                        val url = photoUrls[page]
                        Box(modifier = Modifier.fillMaxSize()) {
                            val model = remember(url) {
                                if (url.startsWith("temp_")) {
                                    newPhotos.find { it.first == url }?.second
                                } else {
                                    url
                                }
                            }
                            coil3.compose.AsyncImage(
                                model = model,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { 
                                    photoUrls.remove(url)
                                    newPhotos.removeAll { it.first == url }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    
                    if (photoUrls.size > 1) {
                        Row(
                            Modifier
                                .height(32.dp)
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(photoUrls.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(6.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { pickerLauncher.launch() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddAPhoto, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Photo")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val story = Story(
                        id = initialStory?.id ?: "",
                        title = title,
                        description = description,
                        date = dateMillis,
                        category = category,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = address,
                        photoUrls = photoUrls.filter { !it.startsWith("temp_") && !it.startsWith("/") }
                    )
                    if (initialStory == null) {
                        onAddStory(story, newPhotos.map { it.second })
                    } else {
                        onUpdateStory(story, newPhotos.map { it.second })
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Story")
            }

            if (initialStory == null) {
                OutlinedButton(
                    onClick = { onCreatePlace(location, title, address) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Make it a Place")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}


@Composable
fun PartnerStatusCard(
    user: User,
    status: ConnectionStatus,
    currentTime: Long,
    distanceInfo: DistanceInfo? = null,
    isPartnerComingToMe: Boolean = false,
    isDistanceLoading: Boolean = false,
    distanceError: String? = null
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = surfaceColor.copy(alpha = 0.95f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val speed = user.speed ?: 0f
                    if (speed > 0.3f) {
                        val speedKmh = (speed * 3.6f).toInt()
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$speedKmh km/h",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                if (status == ConnectionStatus.OFFLINE) {
                    Text(
                        text = "Last updated ${formatLastUpdated(user.lastLocationUpdate, currentTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                val speed = user.speed ?: 0f
                val isMoving = speed > 0.3f

                if (isMoving) {
                    if (distanceInfo != null) {
                        val distanceM = distanceInfo.distanceMeters.toDouble()
                        val distanceText = if (distanceM < 1000) {
                            "${distanceM.toInt()}m"
                        } else {
                            "${(distanceM / 1000).format(1)}km"
                        }
                        val distanceStatus = if (isPartnerComingToMe) {
                            "Coming to you • $distanceText away"
                        } else {
                            "$distanceText away"
                        }
                        Text(
                            text = distanceStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isDistanceLoading) {
                        Text(
                            text = "Calculating distance...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    } else if (distanceError != null) {
                        Text(
                            text = "Distance Unavailable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Battery
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val level = user.batteryLevel
                        val isCharging = user.isCharging == true
                        val batteryRes = when {
                            isCharging -> when {
                                level == null -> R.drawable.ic_battery_status
                                level <= 0.20f -> R.drawable.ic_battery_charging_20
                                level <= 0.50f -> R.drawable.ic_battery_charging_50
                                level <= 0.80f -> R.drawable.ic_battery_charging_80
                                else -> R.drawable.ic_battery_charging
                            }
                            else -> when {
                                level == null -> R.drawable.ic_battery_status
                                level <= 0.20f -> R.drawable.ic_battery_20
                                level <= 0.50f -> R.drawable.ic_battery_50
                                level <= 0.80f -> R.drawable.ic_battery_80
                                else -> R.drawable.ic_battery_100
                            }
                        }
                        val batteryColor = when {
                            level == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            level <= 0.20f -> Color.Red
                            level <= 0.50f -> Color(0xFFFFA500)
                            else -> Color(0xFF4CAF50)
                        }

                        Icon(
                            painter = painterResource(id = batteryRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Unspecified
                        )
                        Text(
                            text = (level?.let { "${(it * 100).toInt()}%" } ?: "--%"),
                            style = MaterialTheme.typography.labelSmall,
                            color = batteryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Signal
                    val signalRes = R.drawable.ic_signal_status
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            painter = painterResource(id = signalRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (user.connectionType) {
                                "Cellular" -> "Cell"
                                null -> "Off"
                                else -> user.connectionType
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        // Small triangle pointing down
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(8.dp)
                .offset(y = (-1).dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                }
                drawPath(
                    path = path,
                    color = surfaceColor.copy(alpha = 0.95f)
                )
                // Diagonal borderlines to match the card's border
                val strokePath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2, size.height)
                    lineTo(size.width, 0f)
                }
                drawPath(
                    path = strokePath,
                    color = outlineColor,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun PartnerDashboard(
    modifier: Modifier = Modifier,
    anniversaryDate: Long?,
    currentTime: Long,
    onEditAnniversary: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onEditAnniversary() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (anniversaryDate != null) {
                val days = (currentTime - anniversaryDate) / (1000 * 60 * 60 * 24)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Favorite,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Together for $days days",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Using common utilities from LocationUtils.kt

@Composable
fun WeatherWidget(
    modifier: Modifier = Modifier,
    weatherInfo: WeatherInfo?,
    isLoading: Boolean
) {
    AnimatedVisibility(
        visible = weatherInfo != null || isLoading,
        enter = fadeIn() + slideInHorizontally { -it },
        exit = fadeOut() + slideOutHorizontally { -it },
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isLoading && weatherInfo == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (weatherInfo != null) {
                    Text(
                        text = weatherInfo.iconCode,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${weatherInfo.temperature.toInt()}°C",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}