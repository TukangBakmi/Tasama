package com.example.tasama.presentation.partner

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.tasama.domain.model.User
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.yield

@Composable
actual fun MapContent(
    modifier: Modifier,
    partner: User?
) {
    val partnerLocation = remember(partner?.latitude, partner?.longitude) {
        if (partner?.latitude != null && partner.longitude != null) {
            LatLng(partner.latitude, partner.longitude)
        } else {
            // Default to a visible location if no partner data yet
            LatLng(-6.2000, 106.8166) 
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(partnerLocation, 12f)
    }

    val markerState = rememberMarkerState(position = partnerLocation)
    val uiSettings = remember { 
        MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = true
        ) 
    }

    LaunchedEffect(partnerLocation) {
        markerState.position = partnerLocation
        
        if (partnerLocation.latitude != 0.0 || partnerLocation.longitude != 0.0) {
            val currentTarget = cameraPositionState.position.target
            if (currentTarget.latitude != partnerLocation.latitude || 
                currentTarget.longitude != partnerLocation.longitude) {
                
                yield() // Give the UI a frame to attach before animating
                try {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLng(partnerLocation)
                    )
                } catch (_: Exception) {
                    // Silently handle animation interruptions
                }
            }
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = uiSettings,
        contentPadding = WindowInsets(0).asPaddingValues(),
        properties = MapProperties(
            isMyLocationEnabled = true,
            mapType = MapType.NORMAL
        )
    ) {
        if (partnerLocation.latitude != 0.0 || partnerLocation.longitude != 0.0) {
            Marker(
                state = markerState,
                title = partner?.name ?: "Partner",
                snippet = "Last updated: ${partner?.lastLocationUpdate ?: "Unknown"}"
            )
        }
    }
}
