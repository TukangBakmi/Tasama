package com.example.tasama.presentation.partner

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import com.example.tasama.util.reverseGeocode
import com.example.tasama.util.calculateDistance
import com.example.tasama.util.getPointAtDistance
import com.example.tasama.util.Location
import com.example.tasama.util.format
import com.example.tasama.util.clipSegmentToRect
import com.example.tasama.util.findRayIntersection
import com.example.tasama.util.applyUIAvoidance
import com.example.tasama.util.disableHardwareBitmaps
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.math.*
import com.example.tasama.R
import com.example.tasama.presentation.components.AppTransientFeedbackOverlay
import kotlinx.datetime.*
import kotlin.time.Clock

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
    partnerLiveLocation: com.example.tasama.domain.model.LiveLocation?,
    places: List<Place>,
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
    onUnlink: () -> Unit,
    onCopyId: (String) -> Unit,
    settings: AppSettings,
    onOpenSettings: () -> Unit
) {
    val density = LocalDensity.current

    var isPartnerInfoVisible by remember { mutableStateOf(false) }
    var showAddPlaceSheet by remember { mutableStateOf<LatLng?>(null) }
    var radiusEditCenter by remember { mutableStateOf<LatLng?>(null) }
    var editingPlace by remember { mutableStateOf<Place?>(null) }
    var tempRadius by remember { mutableFloatStateOf(200f) }
    var tempColor by remember { mutableStateOf(Color(0xFF2196F3)) }
    var tempIconName by remember { mutableStateOf("Location") }
    var isPlacementModeEnabled by rememberSaveable { mutableStateOf(false) }
    var isFollowModeEnabled by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(showAddPlaceSheet, radiusEditCenter, editingPlace) {
        if (showAddPlaceSheet != null || radiusEditCenter != null) {
            tempRadius = editingPlace?.radius?.toFloat() ?: 200f
            tempColor = editingPlace?.color?.let { Color(it.toInt()) } ?: Color(0xFF2196F3)
            tempIconName = editingPlace?.iconName ?: "Location"
        }
    }
    
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

    val partnerLocation = remember(partner?.latitude, partner?.longitude, partnerLiveLocation?.latitude, partnerLiveLocation?.longitude) {
        val lat = partnerLiveLocation?.latitude ?: partner?.latitude
        val lon = partnerLiveLocation?.longitude ?: partner?.longitude
        if (lat != null && lon != null) {
            LatLng(lat, lon)
        } else null
    }

    val animatedMyLocation by animateLatLngAsState(
        targetValue = myLocation ?: LatLng(0.0, 0.0),
        animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
    )
    val animatedPartnerLocation by animateLatLngAsState(
        targetValue = partnerLocation ?: LatLng(0.0, 0.0),
        animationSpec = tween(durationMillis = if (partnerLiveLocation != null) 500 else 1500, easing = LinearEasing)
    )

    val currentMyLocation = if (myLocation != null) animatedMyLocation else null
    val currentPartnerLocation = if (partnerLocation != null) animatedPartnerLocation else null

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(partnerLocation ?: LatLng(-6.2000, 106.8166), 12f)
    }

    // Optimization: Debounce camera position updates to avoid expensive calculations during every micro-movement
    var debouncedCameraPosition by remember { mutableStateOf(cameraPositionState.position) }
    @OptIn(FlowPreview::class)
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position }
            .debounce(150)
            .collect { debouncedCameraPosition = it }
    }

    val uiSettings by remember(isPlacementModeEnabled, radiusEditCenter) {
        derivedStateOf {
            MapUiSettings(
                compassEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                zoomControlsEnabled = false,
                scrollGesturesEnabled = !isPlacementModeEnabled && radiusEditCenter == null
            )
        }
    }

    val distance by remember(currentMyLocation, currentPartnerLocation) {
        derivedStateOf {
            if (currentMyLocation != null && currentPartnerLocation != null &&
                currentMyLocation.latitude != 0.0 && currentPartnerLocation.latitude != 0.0) {
                calculateDistance(Location(currentMyLocation.latitude, currentMyLocation.longitude), Location(currentPartnerLocation.latitude, currentPartnerLocation.longitude))
            } else null
        }
    }

    val isTogether by remember(distance) {
        derivedStateOf { (distance ?: Double.MAX_VALUE) < 150.0 }
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
                        val dist = calculateDistance(
                            Location(currentMyLocation.latitude, currentMyLocation.longitude),
                            Location(currentPartnerLocation.latitude, currentPartnerLocation.longitude)
                        )
                        if (dist < 1200.0) {
                            val center = LatLng(
                                (currentMyLocation.latitude + currentPartnerLocation.latitude) / 2.0,
                                (currentMyLocation.longitude + currentPartnerLocation.longitude) / 2.0
                            )
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.builder()
                                    .target(center)
                                    .zoom(15.2f) // Capped zoom so it doesn't over-zoom when close together
                                    .bearing(0f)
                                    .tilt(0f)
                                    .build()
                            )
                        } else {
                            val bounds = LatLngBounds.Builder()
                                .include(currentMyLocation)
                                .include(currentPartnerLocation)
                                .build()
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

    BackHandler(enabled = isPlacementModeEnabled) {
        isPlacementModeEnabled = false
    }

    // Derived states for real-time intersection and visibility (Optimization: Responsive to Debounced Camera Movement)
    val markerData by remember(currentMyLocation, currentPartnerLocation, isTogether, mapSize, density, debouncedCameraPosition) {
        derivedStateOf {
            // Using debouncedCameraPosition to trigger re-calculation only when movement slows down or stops
            val position = debouncedCameraPosition
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
            val showPolyline = currentMyLocation != null && currentPartnerLocation != null && !isTogether

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
            if (isPlacementModeEnabled) return@GoogleMap
            
            if (radiusEditCenter != null) {
                // If user clicks anywhere while editing radius, cancel edit mode
                radiusEditCenter = null
                editingPlace = null
                return@GoogleMap
            }
            
            println("DEBUG: Map clicked at $latLng")
            isPartnerInfoVisible = false
            
            val clickedPlace = places.find { place ->
                val distanceValue = calculateDistance(Location(latLng.latitude, latLng.longitude), Location(place.latitude, place.longitude))
                distanceValue <= maxOf(place.radius, 50.0) 
            }
            if (clickedPlace != null) {
                println("DEBUG: Map click detected place: ${clickedPlace.name}")
                editingPlace = clickedPlace
                radiusEditCenter = LatLng(clickedPlace.latitude, clickedPlace.longitude)
                showAddPlaceSheet = null
            } else {
                editingPlace = null
                radiusEditCenter = null
                showAddPlaceSheet = null
            }
        },
            contentPadding = WindowInsets(0).asPaddingValues(),
            properties = mapProperties,
            onMapLongClick = { latLng ->
                if (isPlacementModeEnabled) {
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng))
                    }
                    return@GoogleMap
                }
                val existingPlace = places.find { place ->
                    calculateDistance(Location(latLng.latitude, latLng.longitude), Location(place.latitude, place.longitude)) <= place.radius
                }
                if (existingPlace != null) {
                    editingPlace = existingPlace
                    showAddPlaceSheet = LatLng(existingPlace.latitude, existingPlace.longitude)
                    radiusEditCenter = null
                } else {
                    editingPlace = null
                    showAddPlaceSheet = latLng
                    radiusEditCenter = null
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
                    keys = arrayOf<Any>(
                        currentUser?.id ?: "me", 
                        partner?.id ?: "partner",
                        currentUser?.avatarUrl ?: "",
                        partner?.avatarUrl ?: ""
                    ),
                    state = rememberUpdatedMarkerState(position = midpoint),
                    anchor = Offset(0.5f, 0.5f),
                    visible = (markerData?.isMeVisible ?: true || markerData?.isPartnerVisible ?: true),
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
                        if (isPlacementModeEnabled) return@MarkerComposable true
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
                    val markerState = rememberUpdatedMarkerState(position = LatLng(place.latitude, place.longitude))
                    
                    // Simple Marker for Places
                    MarkerComposable(
                        keys = arrayOf<Any>(place.id, place.name, place.color ?: 0L, place.iconName ?: ""),
                        state = markerState,
                        anchor = Offset(0.5f, 0.5f),
                        zIndex = 0f
                    ) {
                        Surface(
                            color = Color(place.color ?: 0xFF2196F3).copy(alpha = 0.9f),
                            shape = CircleShape,
                            border = BorderStroke(2.dp, Color.White),
                            tonalElevation = 2.dp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (place.iconName) {
                                        "Home" -> Icons.Default.Home
                                        "Work" -> Icons.Default.Work
                                        "School" -> Icons.Default.School
                                        "Shopping" -> Icons.Default.ShoppingCart
                                        "Restaurant" -> Icons.Default.Restaurant
                                        "Gym" -> Icons.Default.FitnessCenter
                                        "Hospital" -> Icons.Default.LocalHospital
                                        "Park" -> Icons.Default.Park
                                        else -> Icons.Default.LocationOn
                                    },
                                    contentDescription = place.name,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Circle for Place radius
                    Circle(
                        center = LatLng(place.latitude, place.longitude),
                        radius = place.radius,
                        fillColor = Color(place.color ?: 0xFF2196F3).copy(alpha = 0.15f),
                        strokeColor = Color(place.color ?: 0xFF2196F3).copy(alpha = 0.3f),
                        strokeWidth = 2f
                    )
                }
            }

            // Interactive Radius Editor Preview
            if (showAddPlaceSheet != null || radiusEditCenter != null) {
                val center = radiusEditCenter ?: showAddPlaceSheet!!
                
    Circle(
        center = center,
        radius = tempRadius.toDouble(),
        fillColor = tempColor.copy(alpha = 0.15f),
        strokeColor = tempColor.copy(alpha = 0.5f),
        strokeWidth = 2f
    )

                MarkerComposable(
                    state = rememberUpdatedMarkerState(
                        LatLng(
                            getPointAtDistance(center.latitude, center.longitude, tempRadius.toDouble(), 90.0).latitude,
                            getPointAtDistance(center.latitude, center.longitude, tempRadius.toDouble(), 90.0).longitude
                        )
                    ),
                    anchor = Offset(0.5f, 0.5f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = tempColor,
                        modifier = Modifier.size(16.dp),
                        border = BorderStroke(2.dp, Color.White),
                        shadowElevation = 2.dp
                    ) {}
                }

                // Radius label marker
                val labelLoc = getPointAtDistance(center.latitude, center.longitude, tempRadius.toDouble(), 75.0)
                val labelPos = LatLng(labelLoc.latitude, labelLoc.longitude)
                MarkerComposable(
                    state = rememberUpdatedMarkerState(labelPos),
                    anchor = Offset(0.5f, 0.5f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, tempColor.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "${tempRadius.toInt()} m\n(Drag anywhere to resize)",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tempColor
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



        CustomBottomSheet(
            visible = showAddPlaceSheet != null,
            onDismissRequest = { 
                showAddPlaceSheet = null
                editingPlace = null
            },
            peekHeight = 100.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AddPlaceSheetContent(
                    location = showAddPlaceSheet ?: LatLng(0.0, 0.0),
                    initialPlace = editingPlace,
                    radiusValue = tempRadius,
                    onRadiusChange = { tempRadius = it },
                    selectedColor = tempColor,
                    onColorChange = { tempColor = it },
                    selectedIconName = tempIconName,
                    onIconChange = { tempIconName = it },
                    onAddPlace = { place: Place ->
                        onAddPlace(place)
                        showAddPlaceSheet = null
                        editingPlace = null
                    },
                    onDeletePlace = {
                        editingPlace?.let { onDeletePlace(it.id) }
                        showAddPlaceSheet = null
                        editingPlace = null
                    }
                )
                AppTransientFeedbackOverlay()
            }
        }

        // Radius Edit Overlay (Full Screen Touch Interceptor)
        if (radiusEditCenter != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val projection = cameraPositionState.projection
                            if (projection != null) {
                                val currentScreenPoint = Point(change.position.x.toInt(), change.position.y.toInt())
                                val dragLatLng = projection.fromScreenLocation(currentScreenPoint)
                                val dist = calculateDistance(
                                    Location(radiusEditCenter!!.latitude, radiusEditCenter!!.longitude),
                                    Location(dragLatLng.latitude, dragLatLng.longitude)
                                )
                                tempRadius = dist.toFloat().coerceIn(50f, 2000f)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                radiusEditCenter = null
                                editingPlace = null
                            }
                        )
                    }
            )
        }

        // Save Radius Button Overlay
        AnimatedVisibility(
            visible = radiusEditCenter != null && showAddPlaceSheet == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        radiusEditCenter = null
                        editingPlace = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        editingPlace?.let { place ->
                            onAddPlace(place.copy(radius = tempRadius.toDouble()))
                        }
                        radiusEditCenter = null
                        editingPlace = null
                    }
                ) {
                    Text("Save Radius")
                }
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
        val showCard = isPartnerInfoVisible && partnerScreenPos != null && showAddPlaceSheet == null && !isPlacementModeEnabled
        
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
        AnimatedVisibility(
            visible = !isPlacementModeEnabled,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
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
        }


        // Placement Mode UI
        if (isPlacementModeEnabled) {
            // Center Pin / Crosshair
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Pin icon
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .offset(y = (-22).dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                // Small dot at the exact center for precision
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }

            // Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Move map to set location",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Or long press on the map to place a marker",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { isPlacementModeEnabled = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val center = cameraPositionState.position.target
                            showAddPlaceSheet = center
                            isPlacementModeEnabled = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("Confirm Location")
                    }
                }
            }
        }


        // Floating action buttons container
        AnimatedVisibility(
            visible = !isPlacementModeEnabled,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {

                // Compass / Mata Angin Button (Resets bearing to North, rotates with map orientation)
                val currentBearing = cameraPositionState.position.bearing
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val currentPos = cameraPositionState.position
                            val newPos = CameraPosition.builder(currentPos)
                                .bearing(0f)
                                .tilt(0f)
                                .build()
                            cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(newPos), 500)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    CompassIcon(bearing = currentBearing)
                }

                // Recenter/Fit User & Partner Button
                SmallFloatingActionButton(
                    onClick = { fitMarkers() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Fit User and Partner"
                    )
                }

                // Add Place Button
                SmallFloatingActionButton(
                    onClick = { isPlacementModeEnabled = true },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = "Add Place")
                }
            }
        }
    }
}

@Composable
fun AddPlaceSheetContent(
    location: LatLng,
    initialPlace: Place? = null,
    radiusValue: Float,
    onRadiusChange: (Float) -> Unit,
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    selectedIconName: String,
    onIconChange: (String) -> Unit,
    onAddPlace: (Place) -> Unit,
    onDeletePlace: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialPlace?.name ?: "") }
    var address by remember { mutableStateOf(initialPlace?.address ?: "Fetching address...") }
    var notifyOnEntry by remember { mutableStateOf(initialPlace?.notifyOnEntry ?: true) }
    var notifyOnExit by remember { mutableStateOf(initialPlace?.notifyOnExit ?: true) }

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
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), thickness = 0.5.dp)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            ) { onColorChange(color) },
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
                            .clickable { onIconChange(iconName) },
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

        Spacer(modifier = Modifier.height(2.dp))

        Button(
            onClick = {
                onAddPlace(
                    Place(
                        id = initialPlace?.id ?: "",
                        name = name.ifBlank { address },
                        address = address,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radius = radiusValue.toDouble(),
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

        if (initialPlace != null && onDeletePlace != null) {
            OutlinedButton(
                onClick = onDeletePlace,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Place")
            }
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
fun CompassIcon(bearing: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                rotationZ = -bearing
            }
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val needleWidth = 5.dp.toPx()
        val needleLength = 10.dp.toPx()

        // 1. North Left (Dark Red)
        val northLeft = Path().apply {
            moveTo(centerX, centerY - needleLength)
            lineTo(centerX - needleWidth, centerY)
            lineTo(centerX, centerY)
            close()
        }
        drawPath(northLeft, Color(0xFFC62828))

        // 2. North Right (Bright Red)
        val northRight = Path().apply {
            moveTo(centerX, centerY - needleLength)
            lineTo(centerX + needleWidth, centerY)
            lineTo(centerX, centerY)
            close()
        }
        drawPath(northRight, Color(0xFFEF5350))

        // 3. South Left (Dark Silver)
        val southLeft = Path().apply {
            moveTo(centerX, centerY + needleLength)
            lineTo(centerX - needleWidth, centerY)
            lineTo(centerX, centerY)
            close()
        }
        drawPath(southLeft, Color(0xFF90A4AE))

        // 4. South Right (Light Silver/White)
        val southRight = Path().apply {
            moveTo(centerX, centerY + needleLength)
            lineTo(centerX + needleWidth, centerY)
            lineTo(centerX, centerY)
            close()
        }
        drawPath(southRight, Color(0xFFECEFF1))

        // 5. Center Pivot Dot
        drawCircle(
            color = Color(0xFF263238),
            radius = 2.5.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

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
fun CustomBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    peekHeight: Dp = 100.dp,
    content: @Composable () -> Unit
) {
    if (!visible) return

    val density = LocalDensity.current
    val peekHeightPx = with(density) { peekHeight.toPx() }
    val scope = rememberCoroutineScope()

    var contentHeightPx by remember { mutableFloatStateOf(0f) }
    val offsetY = remember { Animatable(1200f) }

    LaunchedEffect(visible, contentHeightPx) {
        if (visible && contentHeightPx > 0f) {
            if (offsetY.value == 1200f) {
                offsetY.snapTo(contentHeightPx)
            }
            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
        }
    }

    val collapsedOffset = maxOf(0f, contentHeightPx - peekHeightPx)
    val hiddenOffset = contentHeightPx
    val thresholdPx = with(density) { 70.dp.toPx() }

    fun settleSheet(velocity: Float = 0f) {
        val current = offsetY.value
        scope.launch {
            when {
                // High downward velocity OR dragged past collapsed threshold -> Dismiss
                velocity > 1000f || current > collapsedOffset + thresholdPx -> {
                    offsetY.animateTo(hiddenOffset, tween(200))
                    onDismissRequest()
                }
                // High upward velocity OR near top -> Expand (0f)
                velocity < -1000f || current < collapsedOffset * 0.5f -> {
                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                }
                // Otherwise -> Settle at Collapsed (100.dp)
                else -> {
                    offsetY.animateTo(collapsedOffset, spring(stiffness = Spring.StiffnessLow))
                }
            }
        }
    }

    fun dismiss() {
        scope.launch {
            offsetY.animateTo(hiddenOffset, tween(200))
            onDismissRequest()
        }
    }

    val nestedScrollConnection = remember(collapsedOffset, hiddenOffset) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val current = offsetY.value

                // Dragging UP while sheet is not fully expanded
                if (delta < 0 && current > 0f) {
                    val newOffset = (current + delta).coerceAtLeast(0f)
                    val consumed = current - newOffset
                    scope.launch { offsetY.snapTo(newOffset) }
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                val current = offsetY.value

                // Dragging DOWN when inner content is at top
                if (delta > 0 && current < hiddenOffset) {
                    val newOffset = (current + delta).coerceAtMost(hiddenOffset)
                    val consumedY = newOffset - current
                    scope.launch { offsetY.snapTo(newOffset) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                settleSheet(available.y)
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                settleSheet(available.y)
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim
        val scrimAlpha = if (hiddenOffset > 0f) {
            (1f - (offsetY.value / hiddenOffset).coerceIn(0f, 1f)) * 0.5f
        } else 0.5f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .pointerInput(Unit) {
                    detectTapGestures { dismiss() }
                }
        )

        // Sheet Container with NestedScroll and PointerInput
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { contentHeightPx = it.height.toFloat() }
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .nestedScroll(nestedScrollConnection)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { settleSheet(0f) },
                        onDragCancel = { settleSheet(0f) },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (offsetY.value + dragAmount).coerceIn(0f, hiddenOffset)
                            scope.launch {
                                offsetY.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                content()
            }
        }
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