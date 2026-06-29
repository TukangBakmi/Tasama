package com.example.tasama.presentation.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.tasama.domain.model.User
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlin.math.*

@Composable
actual fun MapContent(
    modifier: Modifier,
    currentUser: User?,
    partner: User?
) {
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

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            contentPadding = WindowInsets(0).asPaddingValues(),
            properties = MapProperties(
                isMyLocationEnabled = false,
                mapType = MapType.NORMAL
            )
        ) {
            myLocation?.let {
                val markerState = rememberMarkerState(position = it)
                MarkerComposable(
                    keys = arrayOf(currentUser?.avatarUrl ?: "", currentUser?.name ?: "", it),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f)
                ) {
                    UserMarker(user = currentUser, isMe = true)
                }
            }

            partnerLocation?.let {
                val markerState = rememberMarkerState(position = it)
                MarkerComposable(
                    keys = arrayOf(partner?.avatarUrl ?: "", partner?.name ?: "", it),
                    state = markerState,
                    anchor = Offset(0.5f, 0.5f)
                ) {
                    UserMarker(user = partner, isMe = false)
                }
            }

            if (myLocation != null && partnerLocation != null) {
                Polyline(
                    points = listOf(myLocation, partnerLocation),
                    color = MaterialTheme.colorScheme.primary,
                    width = 10f,
                    pattern = listOf(Dash(30f), Gap(20f))
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (user?.avatarUrl != null) {
                SubcomposeAsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            } else {
                Text(
                    text = user?.name?.take(1)?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
