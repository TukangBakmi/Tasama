package com.example.tasama.util

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// We need a way to get the context in commonMain or pass it down. 
// However, since this is an actual implementation, we can use a static provider or similar if needed,
// but for now let's assume we have a way to access it or provide a more specific signature if required.
// In this project, we can use a common internal context provider or pass it to the actual function if we change the signature.
// Alternatively, many KMP projects use a top-level property to hold the application context.

private var appContext: Context? = null

fun initGeocoding(context: Context) {
    appContext = context.applicationContext
}

actual suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
    val context = appContext ?: return@withContext null
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]
            val thoroughfare = addr.thoroughfare ?: ""
            val subThoroughfare = addr.subThoroughfare ?: ""
            val locality = addr.locality ?: ""
            
            val formattedAddress = if (thoroughfare.isNotEmpty()) {
                if (subThoroughfare.isNotEmpty()) "$thoroughfare $subThoroughfare, $locality"
                else "$thoroughfare, $locality"
            } else locality
            
            formattedAddress.ifBlank { "Dropped Pin" }
        } else {
            "Unknown Location"
        }
    } catch (e: Exception) {
        null
    }
}
