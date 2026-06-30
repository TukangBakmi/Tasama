package com.example.tasama.presentation.partner

import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.tasama.domain.model.User
import com.example.tasama.presentation.components.UserAvatar
import com.example.tasama.presentation.theme.LocalIsDarkTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.*
import coil3.compose.LocalPlatformContext
import com.example.tasama.R

private const val OFFSCREEN_VISIBLE_RATIO = 0.4f

@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?
) {
    val density = LocalDensity.current
    val indicatorSizePx = with(density) { 56.dp.toPx() }
    val paddingPx = indicatorSizePx * OFFSCREEN_VISIBLE_RATIO
    val radiusPx = indicatorSizePx / 2

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

    LaunchedEffect(partnerLocation) {
        partnerLocation?.let {
            if (it.latitude != 0.0 || it.longitude != 0.0) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLng(it))
            }
        }
    }

    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val isDarkTheme = LocalIsDarkTheme.current

    val mapProperties = remember(isDarkTheme) {
        MapProperties(
            isMyLocationEnabled = false,
            mapType = MapType.NORMAL,
            mapStyleOptions = if (isDarkTheme) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
            } else null
        )
    }

    // Derived states for real-time intersection and visibility
    val markerData by remember(myLocation, partnerLocation, mapSize, cameraPositionState, density) {
        derivedStateOf {
            // Read state to trigger recomposition during camera movement
            cameraPositionState.position
            cameraPositionState.isMoving
            val projection = cameraPositionState.projection ?: return@derivedStateOf null

            if (myLocation == null || partnerLocation == null || mapSize == IntSize.Zero) {
                return@derivedStateOf null
            }

            val width = mapSize.width.toFloat()
            val height = mapSize.height.toFloat()

            val pMe = projection.toScreenLocation(myLocation).let { Offset(it.x.toFloat(), it.y.toFloat()) }
            val pPartner = projection.toScreenLocation(partnerLocation).let { Offset(it.x.toFloat(), it.y.toFloat()) }

            // Buffer to determine visibility and avoid edge flickering
            val buffer = 5f

            // Check visibility using screen coordinates AND visible region (crucial for tilted maps)
            val isMeVisible = pMe.x in buffer..width - buffer &&
                    pMe.y in buffer..height - buffer &&
                    projection.visibleRegion.latLngBounds.contains(myLocation)

            val isPartnerVisible = pPartner.x in buffer..width - buffer &&
                    pPartner.y in buffer..height - buffer &&
                    projection.visibleRegion.latLngBounds.contains(partnerLocation)

            // Calculate intersection points for the polyline and off-screen markers
            var myEdge: Offset? = null
            var partnerEdge: Offset? = null
            var polyStart = myLocation
            var polyEnd = partnerLocation
            var showPolyline = true

            if (!isMeVisible || !isPartnerVisible) {
                val intersections = clipSegmentToRect(pMe, pPartner, width, height)

                if (intersections != null) {
                    val (clippedMe, clippedPartner) = intersections

                    if (!isMeVisible) {
                        myEdge = clippedMe
                        // Calculate marker center (clamped) and adjust polyline start to circle edge
                        val finalX = clippedMe.x.coerceIn(paddingPx, width - paddingPx)
                        val finalY = clippedMe.y.coerceIn(paddingPx, height - paddingPx)
                        val center = Offset(finalX, finalY)
                        val dx = pPartner.x - center.x
                        val dy = pPartner.y - center.y
                        val len = sqrt(dx * dx + dy * dy)
                        val adjustedPoint = if (len > 0) {
                            Offset(center.x + (dx / len) * radiusPx, center.y + (dy / len) * radiusPx)
                        } else center
                        polyStart = projection.fromScreenLocation(Point(adjustedPoint.x.toInt(), adjustedPoint.y.toInt()))
                    }
                    if (!isPartnerVisible) {
                        partnerEdge = clippedPartner
                        // Calculate marker center (clamped) and adjust polyline end to circle edge
                        val finalX = clippedPartner.x.coerceIn(paddingPx, width - paddingPx)
                        val finalY = clippedPartner.y.coerceIn(paddingPx, height - paddingPx)
                        val center = Offset(finalX, finalY)
                        val dx = pMe.x - center.x
                        val dy = pMe.y - center.y
                        val len = sqrt(dx * dx + dy * dy)
                        val adjustedPoint = if (len > 0) {
                            Offset(center.x + (dx / len) * radiusPx, center.y + (dy / len) * radiusPx)
                        } else center
                        polyEnd = projection.fromScreenLocation(
                            Point(adjustedPoint.x.toInt(), adjustedPoint.y.toInt())
                        )
                    }
                } else {
                    // Segment doesn't cross the screen. Place indicators using rays from center.
                    val center = Offset(width / 2, height / 2)
                    if (!isMeVisible) myEdge = findRayIntersection(center, pMe, width, height)
                    if (!isPartnerVisible) partnerEdge = findRayIntersection(center, pPartner, width, height)
                    showPolyline = false
                }
            }

            MarkerVisibilityData(
                isMeVisible = isMeVisible,
                isPartnerVisible = isPartnerVisible,
                myEffectiveLocation = polyStart,
                partnerEffectiveLocation = polyEnd,
                myEdgePoint = myEdge,
                partnerEdgePoint = partnerEdge,
                showPolyline = showPolyline
            )
        }
    }

    Box(modifier = modifier.onSizeChanged { mapSize = it }) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            contentPadding = WindowInsets(0).asPaddingValues(),
            properties = mapProperties
        ) {
            myLocation?.let {
                val markerState = rememberUpdatedMarkerState(position = it)
                MarkerComposable(
                    keys = arrayOf<Any>(currentUser?.avatarUrl ?: "", currentUser?.name ?: "", it.latitude, it.longitude),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f),
                    visible = markerData?.isMeVisible ?: true
                ) {
                    UserMarker(user = currentUser, isMe = true)
                }
            }

            partnerLocation?.let {
                val markerState = rememberUpdatedMarkerState(position = it)
                MarkerComposable(
                    keys = arrayOf<Any>(partner?.avatarUrl ?: "", partner?.name ?: "", it.latitude, it.longitude),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f),
                    visible = markerData?.isPartnerVisible ?: true
                ) {
                    UserMarker(user = partner, isMe = false)
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
                }
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
                    isMe = false,
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
                    isMe = true,
                    onTap = {
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLng(myLocation))
                        }
                    }
                )
            }
        }

        if (distance != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                shape = CircleShape,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = if (distance < 1000) "${distance.toInt()}m away" else "${(distance / 1000).format(1)}km away",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
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
    isMe: Boolean,
    onTap: () -> Unit
) {
    val indicatorSize = 56.dp
    val indicatorSizePx = with(LocalDensity.current) { indicatorSize.toPx() }
    val padding = indicatorSizePx * OFFSCREEN_VISIBLE_RATIO

    val width = mapSize.width.toFloat()
    val height = mapSize.height.toFloat()

    // Clamp to ensure it stays in visible bounds with padding
    val finalX = edgePoint.x.coerceIn(padding, width - padding)
    val finalY = edgePoint.y.coerceIn(padding, height - padding)

    // Calculate angle for the arrow pointing to the actual location
    val cameraTarget = cameraPositionState.position.target
    val bearing = calculateBearing(cameraTarget, targetLocation)
    val relativeBearing = (bearing - cameraPositionState.position.bearing + 360) % 360

    Surface(
        modifier = Modifier
            .offset {
                IntOffset(
                    (finalX - indicatorSizePx / 2).toInt(),
                    (finalY - indicatorSizePx / 2).toInt()
                )
            }
            .size(indicatorSize),
        shape = CircleShape,
        color = if (isMe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
        onClick = onTap
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .background(Color.White, CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(
                user = user,
                modifier = Modifier.fillMaxSize(),
                showInitials = user?.avatarUrl == null
            )

            // Direction arrow pointing to the actual location
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (-6).dp)
                    .rotate(relativeBearing),
                tint = if (isMe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
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
    val showPolyline: Boolean
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

fun calculateBearing(start: LatLng, end: LatLng): Float {
    val startLat = Math.toRadians(start.latitude)
    val startLng = Math.toRadians(start.longitude)
    val endLat = Math.toRadians(end.latitude)
    val endLng = Math.toRadians(end.longitude)

    val dLng = endLng - startLng
    val y = sin(dLng) * cos(endLat)
    val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}

@Composable
fun UserMarker(user: User?, isMe: Boolean) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(if (isMe) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, CircleShape)
            .padding(2.dp)
            .background(Color.White, CircleShape)
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
