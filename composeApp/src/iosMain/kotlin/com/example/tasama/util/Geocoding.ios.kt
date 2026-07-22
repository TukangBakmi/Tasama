package com.example.tasama.util

import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = suspendCoroutine { continuation ->
    val geocoder = CLGeocoder()
    val location = CLLocation(latitude, longitude)
    
    geocoder.reverseGeocodeLocation(location) { placemarks, error ->
        if (error != null || placemarks.isNullOrEmpty()) {
            continuation.resume(null)
            return@reverseGeocodeLocation
        }
        
        val placemark = placemarks.first() as CLPlacemark
        val name = placemark.name
        val locality = placemark.locality
        val thoroughfare = placemark.thoroughfare
        
        val formatted = when {
            thoroughfare != null && locality != null -> "$thoroughfare, $locality"
            name != null -> name
            locality != null -> locality
            else -> "Dropped Pin"
        }
        
        continuation.resume(formatted)
    }
}
