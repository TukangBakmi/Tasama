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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.Place
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.EtaInfo
import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.theme.LocalIsDarkTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.*
import com.example.tasama.R
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?,
    places: List<Place>,
    anniversaryDate: Long?,
    etaInfo: EtaInfo?,
    isPartnerComingToMe: Boolean,
    isEtaLoading: Boolean,
    etaError: String?,
    onEditAnniversary: () -> Unit,
    onAddPlace: (String, Double, Double, Double) -> Unit,
    onDeletePlace: (String) -> Unit,
    onUnlink: () -> Unit
) {
    val density = LocalDensity.current
    val indicatorSizePx = with(density) { 56.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val radiusPx = indicatorSizePx / 2

    var showAddPlaceDialog by remember { mutableStateOf<LatLng?>(null) }
    var showDeletePlaceDialog by remember { mutableStateOf<Place?>(null) }
    var isPartnerInfoVisible by remember { mutableStateOf(false) }
    
    // Auto-show info if partner is moving
    val partnerIsMoving = (partner?.speed ?: 0f) > 0.3f
    
    LaunchedEffect(partnerIsMoving) {
        if (partnerIsMoving) {
            isPartnerInfoVisible = true
        }
    }

    var hasInitialFit by remember { mutableStateOf(false) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var placeName by remember { mutableStateOf("") }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val defaultRadius = 150.0 // 150m is a good default for geofencing

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

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(partnerLocation ?: LatLng(-6.2000, 106.8166), 12f)
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false
        )
    }

    val distance = remember(myLocation, partnerLocation) {
        if (myLocation != null && partnerLocation != null) {
            calculateDistance(myLocation, partnerLocation)
        } else null
    }

    val fitPaddingPx = with(density) { 64.dp.toPx().toInt() }
    val fitMarkers = {
        if (isMapLoaded && mapSize != IntSize.Zero) {
            val hasMyLoc = myLocation != null && myLocation.latitude != 0.0
            val hasPartnerLoc = partnerLocation != null && partnerLocation.latitude != 0.0

            scope.launch {
                val update = when {
                    hasMyLoc && hasPartnerLoc -> {
                        if (myLocation == partnerLocation) {
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.builder().target(myLocation).zoom(15f).bearing(0f).tilt(0f).build()
                            )
                        } else {
                            val bounds = LatLngBounds.Builder().include(myLocation).include(
                                partnerLocation
                            ).build()
                            CameraUpdateFactory.newLatLngBounds(bounds, fitPaddingPx)
                        }
                    }
                    hasPartnerLoc -> {
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.builder().target(partnerLocation).zoom(15f).bearing(0f).tilt(0f).build()
                        )
                    }
                    hasMyLoc -> {
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.builder().target(myLocation).zoom(15f).bearing(0f).tilt(0f).build()
                        )
                    }
                    else -> null
                }
                update?.let { cameraPositionState.animate(it) }
            }
        }
    }

    LaunchedEffect(isMapLoaded, mapSize, myLocation, partnerLocation) {
        if (isMapLoaded && mapSize != IntSize.Zero && !hasInitialFit && (myLocation != null || partnerLocation != null)) {
            fitMarkers()
            hasInitialFit = true
        }
    }

    // Derived states for real-time intersection and visibility
    val markerData by remember(myLocation, partnerLocation, mapSize, cameraPositionState, density) {
        derivedStateOf {
            // Read state to trigger recomposition during camera movement
            cameraPositionState.position
            cameraPositionState.isMoving
            val projection = cameraPositionState.projection ?: return@derivedStateOf null

            if (mapSize == IntSize.Zero || (myLocation == null && partnerLocation == null)) {
                return@derivedStateOf null
            }

            val width = mapSize.width.toFloat()
            val height = mapSize.height.toFloat()

            val pMe = if (myLocation != null) projection.toScreenLocation(myLocation).let { Offset(it.x.toFloat(), it.y.toFloat()) } else Offset.Zero
            val pPartner = if (partnerLocation != null) projection.toScreenLocation(partnerLocation).let { Offset(it.x.toFloat(), it.y.toFloat()) } else Offset.Zero

            // Buffer to determine visibility and avoid edge flickering
            val buffer = 5f

            // Check visibility using screen coordinates AND visible region (crucial for tilted maps)
            val isMeVisible = myLocation?.let {
                pMe.x in buffer..width - buffer &&
                        pMe.y in buffer..height - buffer &&
                        projection.visibleRegion.latLngBounds.contains(it)
            } ?: true

            val isPartnerVisible = partnerLocation?.let {
                pPartner.x in buffer..width - buffer &&
                        pPartner.y in buffer..height - buffer &&
                        projection.visibleRegion.latLngBounds.contains(it)
            } ?: true

            var myEdge: Offset? = null
            var partnerEdge: Offset? = null
            var polyStart = myLocation
            var polyEnd = partnerLocation
            val showPolyline = myLocation != null && partnerLocation != null

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
                if (myLocation != null && !isMeVisible) {
                    myEdge = findRayIntersection(center, pMe, width, height)
                }
                if (partnerLocation != null && !isPartnerVisible) {
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

    val showFitButton by remember(myLocation, partnerLocation, mapSize) {
        derivedStateOf {
            val position = cameraPositionState.position
            // Condition 1: Map is rotated or tilted
            val isRotated = abs(position.bearing) > 0.05f || abs(position.tilt) > 0.05f
            
            // Condition 2: Either person exists and is currently off-screen (one or both)
            val data = markerData
            val isAnyOffScreen = if (data != null) {
                val meOff = (myLocation != null && myLocation.latitude != 0.0) && !data.isMeVisible
                val partnerOff = (partnerLocation != null && partnerLocation.latitude != 0.0) && !data.isPartnerVisible
                meOff || partnerOff
            } else {
                false
            }
            
            isMapLoaded && (isRotated || isAnyOffScreen)
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
                    showAddPlaceDialog = latLng
                }
            }
        ) {
            myLocation?.let {
                val markerState = rememberUpdatedMarkerState(position = it)
                MarkerComposable(
                    keys = arrayOf<Any>(
                        currentUser?.avatarUrl ?: "",
                        currentUser?.name ?: "",
                        it.latitude,
                        it.longitude,
                        currentUser?.batteryLevel ?: 0f,
                        currentUser?.isCharging ?: false,
                        currentUser?.connectionType ?: ""
                    ),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f),
                    visible = markerData?.isMeVisible ?: true
                ) {
                    UserMarker(user = currentUser, isMe = true)
                }
            }

            partnerLocation?.let { location ->
                val markerState = rememberUpdatedMarkerState(position = location)
                MarkerComposable(
                    keys = arrayOf<Any>(
                        partner?.avatarUrl ?: "",
                        partner?.name ?: "",
                        location.latitude,
                        location.longitude,
                        partner?.batteryLevel ?: 0f,
                        partner?.isCharging ?: false,
                        partner?.connectionType ?: ""
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
                    UserMarker(user = partner, isMe = false)
                }
            }

            places.forEach { place ->
                val placeLatLng = LatLng(place.latitude, place.longitude)
                Circle(
                    center = placeLatLng,
                    radius = place.radius,
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    strokeColor = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2f
                )
                val markerState = rememberUpdatedMarkerState(position = placeLatLng)
                MarkerComposable(
                    state = markerState,
                    title = place.name,
                    onInfoWindowLongClick = {
                        showDeletePlaceDialog = place
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
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
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF007BFF), // Azure color
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            markerData?.let { data ->
                if (data.showPolyline) {
                    Polyline(
                        points = listOf(data.myEffectiveLocation, data.partnerEffectiveLocation),
                        color = MaterialTheme.colorScheme.primary,
                        width = 10f,
                        pattern = listOf(Dash(30f), Gap(20f))
                    )

                    // Distance label in the center of the line
                    val midLatLng = LatLng(
                        (data.myEffectiveLocation.latitude + data.partnerEffectiveLocation.latitude) / 2,
                        (data.myEffectiveLocation.longitude + data.partnerEffectiveLocation.longitude) / 2
                    )
                    MarkerComposable(
                        state = rememberUpdatedMarkerState(position = midLatLng),
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
                                Text(
                                    text = if (distance!! < 1000) "${distance.toInt()}m" else "${(distance / 1000).format(1)}km",
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



        if (showAddPlaceDialog != null) {
            AlertDialog(
                onDismissRequest = { showAddPlaceDialog = null },
                title = { Text("Add Place") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Save this location to receive notifications when you or your partner arrive or leave.")
                        OutlinedTextField(
                            value = placeName,
                            onValueChange = { placeName = it },
                            label = { Text("Place Name (e.g. Home, Office)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            text = "Radius set to ${defaultRadius.toInt()}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = placeName.isNotBlank(),
                        onClick = {
                            showAddPlaceDialog?.let {
                                onAddPlace(placeName, it.latitude, it.longitude, defaultRadius)
                            }
                            showAddPlaceDialog = null
                            placeName = ""
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddPlaceDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
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
        
        // Show info if manually clicked OR if triggered by movement
        val showCard = isPartnerInfoVisible && partnerScreenPos != null
        
        if (showCard && partner != null && partnerScreenPos != null) {
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

        // Recenter/Fit Button
        AnimatedVisibility(
            visible = showFitButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
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
fun UserMarker(user: User?, isMe: Boolean) {
    val speed = user?.speed ?: 0f
    val isMoving = speed > 0.3f
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (isMoving) {
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                        .background(
                            if (isMe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (isMe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp),
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
        }
        
        if (isMoving && !isMe) {
            val speedKmh = (speed * 3.6f).toInt()
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "$speedKmh km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
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
                val now = Clock.System.now().toEpochMilliseconds()
                val isOnline = user.lastActive?.let { 
                    now - it < 60_000 
                } ?: false

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E), CircleShape)
                            .border(1.dp, surfaceColor.copy(alpha = 0.5f), CircleShape)
                    )
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val speed = user.speed ?: 0f
                val isMoving = speed > 0.3f

                if (isMoving) {
                    if (etaInfo != null) {
                        val etaStatus = if (isPartnerComingToMe) {
                            "Coming to you • ETA ${etaInfo.durationText}"
                        } else {
                            "${etaInfo.distanceText} • ${etaInfo.durationText}"
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
                            isCharging -> R.drawable.ic_battery_charging
                            level == null -> R.drawable.ic_battery_status
                            level <= 0.20f -> R.drawable.ic_battery_20
                            level <= 0.50f -> R.drawable.ic_battery_50
                            level <= 0.80f -> R.drawable.ic_battery_80
                            else -> R.drawable.ic_battery_100
                        }
                        val batteryColor = when {
                            isCharging -> Color(0xFF4CAF50)
                            level == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            level <= 0.20f -> Color.Red
                            level <= 0.50f -> Color(0xFFFFA500)
                            else -> Color(0xFF4CAF50)
                        }

                        Icon(
                            painter = painterResource(id = batteryRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = batteryColor
                        )
                        val chargingSign = if (isCharging) " ⚡" else ""
                        Text(
                            text = (level?.let { "${(it * 100).toInt()}%$chargingSign" } ?: "--%"),
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
                // Diagonal border lines to match the card's border
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
    val r = 6371e3 // Earth's radius in meters
    val lat1 = p1.latitude * PI / 180
    val lat2 = p2.latitude * PI / 180
    val dLat = (p2.latitude - p1.latitude) * PI / 180
    val dLon = (p2.longitude - p1.longitude) * PI / 180

    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)
