package com.example.tasama.presentation.partner

import android.graphics.Point
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Park
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
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.EtaInfo
import com.example.tasama.domain.repository.TravelMode
import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.theme.LocalIsDarkTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.*
import android.location.Geocoder
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import com.example.tasama.R
import kotlin.time.Clock

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
    anniversaryDate: Long?,
    etaInfo: EtaInfo?,
    weatherInfo: com.example.tasama.domain.model.WeatherInfo?,
    isWeatherLoading: Boolean,
    travelMode: TravelMode,
    isPartnerComingToMe: Boolean,
    isEtaLoading: Boolean,
    etaError: String?,
    onEditAnniversary: () -> Unit,
    onAddPlace: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onSetTravelMode: (TravelMode) -> Unit,
    onUnlink: () -> Unit
) {
    val density = LocalDensity.current
    val indicatorSizePx = with(density) { 56.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val radiusPx = indicatorSizePx / 2

    var showDeletePlaceDialog by remember { mutableStateOf<Place?>(null) }
    var isPartnerInfoVisible by remember { mutableStateOf(false) }
    var showAddPlaceSheet by remember { mutableStateOf<LatLng?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isRouteEnabled by rememberSaveable { mutableStateOf(false) }
    var isFollowModeEnabled by rememberSaveable { mutableStateOf(true) }
    
    var hasInitialFit by remember { mutableStateOf(false) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val followZoom = 16.5f

    val isDarkTheme = LocalIsDarkTheme.current
    val context = LocalContext.current
    val mapProperties = remember(isDarkTheme) {
        MapProperties(
            mapStyleOptions = if (isDarkTheme) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
            } else null
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

    // Navigation Route Logic
    val routePoints = remember(etaInfo?.encodedPolyline) {
        etaInfo?.encodedPolyline?.let { decodePolyline(it) } ?: emptyList()
    }

    // Connect the route points to the animated avatar positions for a seamless look
    val connectedRoutePoints = remember(routePoints, currentMyLocation, currentPartnerLocation) {
        if (routePoints.isEmpty() || currentMyLocation == null || currentPartnerLocation == null) {
            routePoints
        } else {
            val list = routePoints.toMutableList()
            // The Directions API request is always Partner -> Me
            list[0] = currentPartnerLocation
            list[list.lastIndex] = currentMyLocation
            list
        }
    }

    val routeAlpha = animateFloatAsState(
        targetValue = if (isRouteEnabled && routePoints.isNotEmpty()) 1f else 0f,
        animationSpec = tween(600),
        label = "routeAlpha"
    )

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

    val distance by remember(currentMyLocation, currentPartnerLocation) {
        derivedStateOf {
            if (currentMyLocation != null && currentPartnerLocation != null && 
                currentMyLocation.latitude != 0.0 && currentPartnerLocation.latitude != 0.0) {
                calculateDistance(currentMyLocation, currentPartnerLocation)
            } else null
        }
    }

    val fitPaddingPx = with(density) { 64.dp.toPx().toInt() }
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

    // Auto-fit when route is enabled
    LaunchedEffect(isRouteEnabled) {
        if (isRouteEnabled) {
            fitMarkers()
        }
    }

    // Auto-fit when travel mode changes and route updates
    LaunchedEffect(travelMode) {
        if (isRouteEnabled) {
            fitMarkers()
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

    // Derived states for real-time intersection and visibility
    val markerData by remember(currentMyLocation, currentPartnerLocation, mapSize, cameraPositionState, density) {
        derivedStateOf {
            // Read state to trigger recomposition during camera movement
            cameraPositionState.position
            cameraPositionState.isMoving
            val projection = cameraPositionState.projection ?: return@derivedStateOf null

            if (mapSize == IntSize.Zero || (currentMyLocation == null && currentPartnerLocation == null)) {
                return@derivedStateOf null
            }

            val width = mapSize.width.toFloat()
            val height = mapSize.height.toFloat()

            val pMe = if (currentMyLocation != null) projection.toScreenLocation(currentMyLocation).let { Offset(it.x.toFloat(), it.y.toFloat()) } else Offset.Zero
            val pPartner = if (currentPartnerLocation != null) projection.toScreenLocation(currentPartnerLocation).let { Offset(it.x.toFloat(), it.y.toFloat()) } else Offset.Zero

            // Buffer to determine visibility and avoid edge flickering
            val buffer = 5f

            // Check visibility using screen coordinates AND visible region (crucial for tilted maps)
            val isMeVisible = currentMyLocation?.let {
                pMe.x in buffer..width - buffer &&
                        pMe.y in buffer..height - buffer &&
                        projection.visibleRegion.latLngBounds.contains(it)
            } ?: true

            val isPartnerVisible = currentPartnerLocation?.let {
                pPartner.x in buffer..width - buffer &&
                        pPartner.y in buffer..height - buffer &&
                        projection.visibleRegion.latLngBounds.contains(it)
            } ?: true

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
                        myEdge = clippedMe
                        // Calculate marker center (clamped to stay fully on screen)
                        val finalX = clippedMe.x.coerceIn(radiusPx + marginPx, width - radiusPx - marginPx)
                        val finalY = clippedMe.y.coerceIn(radiusPx + marginPx, height - radiusPx - marginPx)
                        polyStart = projection.fromScreenLocation(Point(finalX.toInt(), finalY.toInt()))
                    }
                    if (!isPartnerVisible) {
                        partnerEdge = clippedPartner
                        // Calculate marker center (clamped to stay fully on screen)
                        val finalX = clippedPartner.x.coerceIn(radiusPx + marginPx, width - radiusPx - marginPx)
                        val finalY = clippedPartner.y.coerceIn(radiusPx + marginPx, height - radiusPx - marginPx)
                        polyEnd = projection.fromScreenLocation(Point(finalX.toInt(), finalY.toInt()))
                    }
                } else {
                    // Segment doesn't cross the screen. Place indicators using rays from center.
                    val center = Offset(width / 2, height / 2)
                    if (!isMeVisible) {
                        myEdge = findRayIntersection(center, pMe, width, height)
                        val finalX = myEdge.x.coerceIn(radiusPx + marginPx, width - radiusPx - marginPx)
                        val finalY = myEdge.y.coerceIn(radiusPx + marginPx, height - radiusPx - marginPx)
                        polyStart = projection.fromScreenLocation(Point(finalX.toInt(), finalY.toInt()))
                    }
                    if (!isPartnerVisible) {
                        partnerEdge = findRayIntersection(center, pPartner, width, height)
                        val finalX = partnerEdge.x.coerceIn(radiusPx + marginPx, width - radiusPx - marginPx)
                        val finalY = partnerEdge.y.coerceIn(radiusPx + marginPx, height - radiusPx - marginPx)
                        polyEnd = projection.fromScreenLocation(Point(finalX.toInt(), finalY.toInt()))
                    }
                }
            } else if (!showPolyline) {
                // Handle single marker off-screen indicators
                val center = Offset(width / 2, height / 2)
                if (currentMyLocation != null && !isMeVisible) {
                    myEdge = findRayIntersection(center, pMe, width, height)
                }
                if (currentPartnerLocation != null && !isPartnerVisible) {
                    partnerEdge = findRayIntersection(center, pPartner, width, height)
                }
            }

            MarkerVisibilityData(
                isMeVisible = isMeVisible,
                isPartnerVisible = isPartnerVisible,
                myEffectiveLocation = polyStart ?: LatLng(0.0, 0.0),
                partnerEffectiveLocation = polyEnd ?: LatLng(0.0, 0.0),
                myEdgePoint = myEdge,
                partnerEdgePoint = partnerEdge,
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
            onMapClick = { isPartnerInfoVisible = false },
            contentPadding = WindowInsets(0).asPaddingValues(),
            properties = mapProperties,
            onMapLongClick = { latLng ->
                val existingPlace = places.find { place ->
                    calculateDistance(latLng, LatLng(place.latitude, place.longitude)) <= place.radius
                }
                if (existingPlace != null) {
                    showDeletePlaceDialog = existingPlace
                } else {
                    showAddPlaceSheet = latLng
                }
            }
        ) {
            currentMyLocation?.let { location ->
                val markerState = rememberUpdatedMarkerState(position = location)
                val status = rememberPartnerStatus(currentUser)
                MarkerComposable(
                    keys = arrayOf<Any>(
                        currentUser?.avatarUrl ?: "",
                        currentUser?.name ?: "",
                        currentUser?.batteryLevel ?: 0f,
                        currentUser?.isCharging ?: false,
                        currentUser?.connectionType ?: "",
                        status
                    ),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f),
                    visible = markerData?.isMeVisible ?: true
                ) {
                    UserMarker(user = currentUser, isMe = true, status = status)
                }
            }

            currentPartnerLocation?.let { location ->
                val status = rememberPartnerStatus(partner)
                val markerState = rememberUpdatedMarkerState(position = location)
                MarkerComposable(
                    keys = arrayOf<Any>(
                        partner?.avatarUrl ?: "",
                        partner?.name ?: "",
                        partner?.batteryLevel ?: 0f,
                        partner?.isCharging ?: false,
                        partner?.connectionType ?: "",
                        partner?.speed ?: 0f,
                        status
                    ),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f),
                    visible = markerData?.isPartnerVisible ?: true,
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
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f),
                    title = place.name,
                    onInfoWindowLongClick = {
                        showDeletePlaceDialog = place
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(2.dp, placeColor, CircleShape)
                            .padding(8.dp)
                            .pointerInput(place.id) {
                                detectTapGestures(
                                    onTap = {
                                        markerState.showInfoWindow()
                                    },
                                    onLongPress = {
                                        showDeletePlaceDialog = place
                                    }
                                )
                            },
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

            markerData?.let { data ->
                if (data.showPolyline) {
                    // 1. Dynamic Route (Google Directions) - Only visible when partner info is shown
                    if (routeAlpha.value > 0f && connectedRoutePoints.isNotEmpty()) {
                        val isWalking = travelMode == TravelMode.WALKING
                        
                        // Route Border/Shadow for depth
                        Polyline(
                            points = connectedRoutePoints,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f * routeAlpha.value),
                            width = 16f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            pattern = if (isWalking) listOf(Dash(20f), Gap(20f)) else null
                        )
                        // Main Route Line
                        Polyline(
                            points = connectedRoutePoints,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = routeAlpha.value),
                            width = 10f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            pattern = if (isWalking) listOf(Dash(20f), Gap(20f)) else null
                        )
                    }

                    // 2. Straight Dashed Line - Fades out when the real route fades in
                    val dashedAlpha = 1f - routeAlpha.value
                    if (dashedAlpha > 0f) {
                        Polyline(
                            points = listOf(data.myEffectiveLocation, data.partnerEffectiveLocation),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = dashedAlpha),
                            width = 10f,
                            pattern = listOf(Dash(30f), Gap(20f))
                        )
                    }

                    // Distance label - follows the route if visible, otherwise midpoint
                    val labelPosition = if (routeAlpha.value > 0.5f && connectedRoutePoints.isNotEmpty()) {
                        getPolylineMidpoint(connectedRoutePoints) ?: LatLng(
                            (data.myEffectiveLocation.latitude + data.partnerEffectiveLocation.latitude) / 2,
                            (data.myEffectiveLocation.longitude + data.partnerEffectiveLocation.longitude) / 2
                        )
                    } else {
                        LatLng(
                            (data.myEffectiveLocation.latitude + data.partnerEffectiveLocation.latitude) / 2,
                            (data.myEffectiveLocation.longitude + data.partnerEffectiveLocation.longitude) / 2
                        )
                    }

                    MarkerComposable(
                        keys = arrayOf(distance ?: 0.0, etaInfo ?: 0, labelPosition, routeAlpha.value),
                        state = rememberUpdatedMarkerState(position = labelPosition),
                        anchor = Offset(0.5f, 0.5f),
                        zIndex = 1f
                    ) {
                        val blurRadius = 8.dp
                        val shadowColor = if (isDarkTheme) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.2f)
                        val bubbleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(
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
                                val labelText = if (routeAlpha.value > 0.5f && etaInfo != null) {
                                    "${etaInfo.durationText} ($distanceText)"
                                } else {
                                    distanceText
                                }

                                Text(
                                    text = labelText,
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
                onDismissRequest = { showAddPlaceSheet = null },
                sheetState = bottomSheetState
            ) {
                AddPlaceSheetContent(
                    location = showAddPlaceSheet!!,
                    onAddPlace = { place: Place ->
                        onAddPlace(place)
                        showAddPlaceSheet = null
                    },
                    onCancel = { showAddPlaceSheet = null }
                )
            }
        }

        // Off-screen markers
        markerData?.let { data ->
            if (!data.isPartnerVisible && data.partnerEdgePoint != null && partnerLocation != null) {
                OffScreenMarker(
                    targetLocation = partnerLocation,
                    edgePoint = data.partnerEdgePoint,
                    user = partner,
                    cameraPositionState = cameraPositionState,
                    mapSize = mapSize,
                    showArrow = true,
                    onTap = {
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLng(partnerLocation))
                        }
                    }
                )
            }
            if (!data.isMeVisible && data.myEdgePoint != null && myLocation != null) {
                OffScreenMarker(
                    targetLocation = myLocation,
                    edgePoint = data.myEdgePoint,
                    user = currentUser,
                    cameraPositionState = cameraPositionState,
                    mapSize = mapSize,
                    showArrow = true,
                    onTap = {
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLng(myLocation))
                        }
                    }
                )
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
                        etaInfo = etaInfo,
                        isPartnerComingToMe = isPartnerComingToMe,
                        isEtaLoading = isEtaLoading,
                        etaError = etaError
                    )
                }
            }
        }

        PartnerDashboard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
            anniversaryDate = anniversaryDate,
            onEditAnniversary = onEditAnniversary
        )

        WeatherWidget(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 14.dp, end = 16.dp),
            weatherInfo = weatherInfo,
            isLoading = isWeatherLoading
        )

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

            // Route Toggle Button
            SmallFloatingActionButton(
                onClick = { isRouteEnabled = !isRouteEnabled },
                containerColor = if (isRouteEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                contentColor = if (isRouteEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Directions, contentDescription = "Toggle Route")
            }

            // Travel Mode Selector (Only when route is enabled)
            AnimatedVisibility(
                visible = isRouteEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TravelModeButton(
                            selected = travelMode == TravelMode.DRIVING,
                            onClick = { onSetTravelMode(TravelMode.DRIVING) },
                            icon = Icons.Default.DirectionsCar
                        )
                        TravelModeButton(
                            selected = travelMode == TravelMode.MOTORCYCLE,
                            onClick = { onSetTravelMode(TravelMode.MOTORCYCLE) },
                            icon = Icons.Default.TwoWheeler
                        )
                        TravelModeButton(
                            selected = travelMode == TravelMode.WALKING,
                            onClick = { onSetTravelMode(TravelMode.WALKING) },
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk
                        )
                    }
                }
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
    }
}

@Composable
fun AddPlaceSheetContent(
    location: LatLng,
    onAddPlace: (Place) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("Fetching address...") }
    val radius = 200f // Fixed to 200m as requested
    var notifyOnEntry by remember { mutableStateOf(true) }
    var notifyOnExit by remember { mutableStateOf(true) }
    var selectedColor by remember { mutableStateOf(Color(0xFF2196F3)) } // Default Blue
    var selectedIconName by remember { mutableStateOf("Location") }

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

    LaunchedEffect(location) {
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val thoroughfare = addr.thoroughfare ?: ""
                    val subThoroughfare = addr.subThoroughfare ?: ""
                    val locality = addr.locality ?: ""
                    
                    val formattedAddress = if (thoroughfare.isNotEmpty()) {
                        if (subThoroughfare.isNotEmpty()) "$thoroughfare $subThoroughfare, $locality"
                        else "$thoroughfare, $locality"
                    } else locality
                    
                    withContext(Dispatchers.Main) {
                        address = formattedAddress.ifBlank { "Dropped Pin" }
                        if (name.isEmpty()) name = thoroughfare.ifEmpty { locality }.ifEmpty { "Dropped Pin" }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        address = "Unknown Location"
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    address = "Error fetching address"
                }
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
                    text = "Add Place Marker",
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
                        id = "",
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
            Text("Save Place")
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
fun TravelModeButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun OffScreenMarker(
    targetLocation: LatLng,
    edgePoint: Offset,
    user: User?,
    cameraPositionState: CameraPositionState,
    mapSize: IntSize,
    showArrow: Boolean = true,
    onTap: () -> Unit
) {
    val indicatorSize = 56.dp
    val density = LocalDensity.current
    val indicatorSizePx = with(density) { indicatorSize.toPx() }

    val width = mapSize.width.toFloat()
    val height = mapSize.height.toFloat()

    // Position clamped to edges to stay fully visible
    val half = indicatorSizePx / 2f
    val marginPx = with(density) { 8.dp.toPx() }

    val finalX = edgePoint.x.coerceIn(half + marginPx, width - half - marginPx)
    val finalY = edgePoint.y.coerceIn(half + marginPx, height - half - marginPx)

    val projection = cameraPositionState.projection
    val angle = remember(projection, targetLocation, finalX, finalY) {
        val targetScreenPos = projection?.toScreenLocation(targetLocation)
        if (targetScreenPos != null) {
            val dx = targetScreenPos.x - finalX
            val dy = targetScreenPos.y - finalY
            (atan2(dy.toDouble(), dx.toDouble()) * 180 / PI).toFloat()
        } else 0f
    }

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
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        // The "Bubble" Shape (Theme-aware Background with Pointer)
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
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
    val showPolyline: Boolean,
    val partnerScreenPos: Offset?
)

enum class ConnectionStatus {
    LIVE, WEAK, OFFLINE
}

@Composable
fun rememberPartnerStatus(user: User?): ConnectionStatus {
    if (user == null) return ConnectionStatus.OFFLINE
    
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    
    LaunchedEffect(user.lastLocationUpdate) {
        while (true) {
            now = Clock.System.now().toEpochMilliseconds()
            delay(5000)
        }
    }
    
    return remember(user.lastLocationUpdate, user.accuracy, now) {
        val lastUpdate = user.lastLocationUpdate ?: 0L
        val delayMs = now - lastUpdate
        val accuracy = user.accuracy ?: 0f
        
        when {
            lastUpdate == 0L || delayMs > 30_000 -> ConnectionStatus.OFFLINE
            delayMs > 10_000 || accuracy > 50f -> ConnectionStatus.WEAK
            else -> ConnectionStatus.LIVE
        }
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
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
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

fun formatLastUpdated(lastUpdate: Long?): String {
    if (lastUpdate == null) return "never"
    val now = Clock.System.now().toEpochMilliseconds()
    val diffSec = (now - lastUpdate) / 1000
    return when {
        diffSec < 60 -> "just now"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        else -> "${diffSec / 86400}d ago"
    }
}

/**
 * Clips a line segment to the screen rectangle using Liang-Barsky algorithm.
 * Returns the clipped segment endpoints as a pair of Offsets.
 */
fun clipSegmentToRect(p1: Offset, p2: Offset, width: Float, height: Float): Pair<Offset, Offset>? {
    var t0 = 0f
    var t1 = 1f
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y

    fun clip(p: Float, q: Float): Boolean {
        if (p == 0f) return q >= 0
        val t = q / p
        if (p < 0) {
            if (t > t1) return false
            if (t > t0) t0 = t
        } else if (p > 0) {
            if (t < t0) return false
            if (t < t1) t1 = t
        }
        return true
    }

    if (clip(-dx, p1.x) && clip(dx, width - p1.x) &&
        clip(-dy, p1.y) && clip(dy, height - p1.y)) {
        return Pair(
            Offset(p1.x + t0 * dx, p1.y + t0 * dy),
            Offset(p1.x + t1 * dx, p1.y + t1 * dy)
        )
    }
    return null
}

/**
 * Finds the intersection of a ray starting from 'start' toward 'end' with screen edges.
 */
fun findRayIntersection(start: Offset, end: Offset, width: Float, height: Float): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y

    val tX = if (dx > 0) (width - start.x) / dx else if (dx < 0) -start.x / dx else Float.MAX_VALUE
    val tY = if (dy > 0) (height - start.y) / dy else if (dy < 0) -start.y / dy else Float.MAX_VALUE

    val t = min(tX, tY)
    return Offset(start.x + t * dx, start.y + t * dy)
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
fun PartnerStatusCard(
    user: User, 
    etaInfo: EtaInfo? = null, 
    isPartnerComingToMe: Boolean = false,
    isEtaLoading: Boolean = false,
    etaError: String? = null
) {
    val status = rememberPartnerStatus(user)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = surfaceColor.copy(alpha = 0.95f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        text = "Last updated ${formatLastUpdated(user.lastLocationUpdate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                val speed = user.speed ?: 0f
                val isMoving = speed > 0.3f

                if (isMoving) {
                    if (etaInfo != null) {
                        val etaStatus = if (isPartnerComingToMe) {
                            "Coming to you • ETA ${etaInfo.durationText}"
                        } else {
                            "${etaInfo.durationText} away"
                        }
                        Text(
                            text = etaStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isEtaLoading) {
                        Text(
                            text = "Calculating ETA...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    } else if (etaError != null) {
                        Text(
                            text = "ETA Unavailable",
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
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
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
    onEditAnniversary: () -> Unit
) {
    Surface(
        modifier = modifier.statusBarsPadding(),
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
                val days = (Clock.System.now().toEpochMilliseconds() - anniversaryDate) / (1000 * 60 * 60 * 24)
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

fun calculateDistance(p1: LatLng, p2: LatLng): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(
        p1.latitude, p1.longitude,
        p2.latitude, p2.longitude,
        results
    )
    return results[0].toDouble()
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

/**
 * Decodes an encoded polyline string into a list of LatLng points.
 */
fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
        poly.add(p)
    }

    return poly
}

/**
 * Calculates the midpoint along a polyline path.
 */
fun getPolylineMidpoint(points: List<LatLng>): LatLng? {
    if (points.isEmpty()) return null
    if (points.size == 1) return points[0]

    var totalDistance = 0.0
    for (i in 0 until points.size - 1) {
        totalDistance += calculateDistance(points[i], points[i+1])
    }

    val midDistance = totalDistance / 2.0
    var currentDistance = 0.0
    for (i in 0 until points.size - 1) {
        val segmentDist = calculateDistance(points[i], points[i+1])
        if (currentDistance + segmentDist >= midDistance) {
            val ratio = if (segmentDist > 0) (midDistance - currentDistance) / segmentDist else 0.0
            val lat = points[i].latitude + (points[i+1].latitude - points[i].latitude) * ratio
            val lng = points[i].longitude + (points[i+1].longitude - points[i].longitude) * ratio
            return LatLng(lat, lng)
        }
        currentDistance += segmentDist
    }
    return points.last()
}

@Composable
fun WeatherWidget(
    modifier: Modifier = Modifier,
    weatherInfo: com.example.tasama.domain.model.WeatherInfo?,
    isLoading: Boolean
) {
    AnimatedVisibility(
        visible = weatherInfo != null || isLoading,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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