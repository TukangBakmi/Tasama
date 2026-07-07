package com.example.tasama.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.tasama.MainActivity
import com.example.tasama.R
import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.PlaceRepository
import com.example.tasama.domain.service.GeofenceMonitor
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.android.ext.android.inject
import java.util.*

class LocationService : Service() {

    private val authRepository: AuthRepository by inject()
    private val placeRepository: PlaceRepository by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var geofenceMonitor: GeofenceMonitor? = null
    private var partnerObservationJob: Job? = null
    private var lastPartnerData: User? = null
    private var partnerAvatarBitmap: Bitmap? = null
    private var lastAddress: String? = null
    private var lastAddressLocation: Pair<Double, Double>? = null
    private var lastNotificationUpdateTime = 0L
    private var isAvatarLoading = false
    private var isServiceStarted = false

    private val contentIntent by lazy {
        val intent = Intent(this, MainActivity::class.java)
        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID = "location_updates"
        const val NOTIFICATION_ID = 12345
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        geofenceMonitor = GeofenceMonitor(authRepository, placeRepository, serviceScope)
        geofenceMonitor?.startMonitoring()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val uid = authRepository.getCurrentUserId() ?: return
                for (location in locationResult.locations) {
                    val speed = if (location.hasSpeed()) location.speed else null
                    serviceScope.launch {
                        authRepository.updateLocation(uid, location.latitude, location.longitude, speed)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLocationService()
            ACTION_STOP -> stopLocationService()
        }
        return START_STICKY
    }

    private fun startLocationService() {
        if (isServiceStarted) return
        isServiceStarted = true

        val notificationManager = getSystemService(NotificationManager::class.java)
        
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Sharing",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        // Initial notification
        val notification = createNotification(null)
        startForeground(NOTIFICATION_ID, notification)
        
        requestLocationUpdates()
        observePartner()
        monitorLocalStatus()
    }

    private fun observePartner() {
        partnerObservationJob?.cancel()
        partnerObservationJob = serviceScope.launch {
            val uid = authRepository.getCurrentUserId() ?: run {
                stopLocationService()
                return@launch
            }

            // Observe ONLY partnerId changes to avoid restarting the partner flow unnecessarily
            authRepository.getUserFlow(uid)
                .map { it?.partnerId }
                .distinctUntilChanged()
                .collectLatest { partnerId ->
                    if (partnerId != null) {
                        authRepository.getUserFlow(partnerId).collect { partner ->
                            if (partner != null) {
                                if (shouldUpdateNotification(partner)) {
                                    updateNotification(partner)
                                    lastPartnerData = partner
                                    lastNotificationUpdateTime = System.currentTimeMillis()
                                }
                            }
                        }
                    } else {
                        // Optional: show a "No partner linked" notification state
                    }
                }
        }
    }

    private fun shouldUpdateNotification(newPartner: User): Boolean {
        val now = System.currentTimeMillis()
        val oldPartner = lastPartnerData ?: return true

        // 1. Force update for critical profile changes
        if (oldPartner.name != newPartner.name ||
            oldPartner.avatarUrl != newPartner.avatarUrl) return true

        // 2. Force update for battery changes (even 1%) or charging status
        val oldBattery = ((oldPartner.batteryLevel ?: 0f) * 100).toInt()
        val newBattery = ((newPartner.batteryLevel ?: 0f) * 100).toInt()
        if (oldBattery != newBattery) return true
        if (oldPartner.isCharging != newPartner.isCharging) return true

        // 3. Force update for connection type changes
        if (oldPartner.connectionType != newPartner.connectionType) return true

        // 4. Force update for status transitions (Stationary <-> Moving)
        val oldIsMoving = (oldPartner.speed ?: 0f) > 0.6f
        val newIsMoving = (newPartner.speed ?: 0f) > 0.6f
        if (oldIsMoving != newIsMoving) return true

        // 5. Time throttle: Only throttle minor jitter (Location/Speed) to once every 15s
        if (now - lastNotificationUpdateTime < 15000) return false

        // 6. Update if speed changes significantly (5+ km/h)
        val oldSpeedKmh = ((oldPartner.speed ?: 0f) * 3.6f).toInt()
        val newSpeedKmh = ((newPartner.speed ?: 0f) * 3.6f).toInt()
        if (Math.abs(oldSpeedKmh - newSpeedKmh) >= 5) return true

        // 7. Update if moved significantly (40+ meters)
        val distance = calculateDistance(
            oldPartner.latitude, oldPartner.longitude,
            newPartner.latitude, newPartner.longitude
        )
        if (distance > 40) return true

        return false
    }

    private fun calculateDistance(lat1: Double?, lon1: Double?, lat2: Double?, lon2: Double?): Float {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return Float.MAX_VALUE
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun monitorLocalStatus() {
        serviceScope.launch {
            val uid = authRepository.getCurrentUserId() ?: return@launch
            while (isActive) {
                val connectionType = getConnectionType()
                authRepository.updateConnectionType(uid, connectionType)
                delay(60000) // Update every minute
            }
        }
    }

    private fun getConnectionType(): String {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "Offline"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "Offline"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val info = wifiManager.connectionInfo
                val ssid = info.ssid?.trim('"')
                if (ssid != null && ssid != "<unknown ssid>" && ssid.isNotEmpty()) {
                    ssid
                } else {
                    "Wi-Fi"
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Offline"
        }
    }

    private fun updateNotification(partner: User) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (partner.avatarUrl != lastPartnerData?.avatarUrl) {
            partnerAvatarBitmap = null
            isAvatarLoading = false
        }
        
        // Always trigger an immediate notification update with current data
        // Throttling is handled by the caller (shouldUpdateNotification)
        notificationManager.notify(NOTIFICATION_ID, createNotification(partner))

        // Trigger avatar load if we don't have it yet
        if (partnerAvatarBitmap == null && partner.avatarUrl != null && !isAvatarLoading) {
            isAvatarLoading = true
            Glide.with(this)
                .asBitmap()
                .load(partner.avatarUrl)
                .circleCrop()
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        partnerAvatarBitmap = resource
                        isAvatarLoading = false
                        notificationManager.notify(NOTIFICATION_ID, createNotification(partner))
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        isAvatarLoading = false
                    }
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        isAvatarLoading = false
                    }
                })
        }
    }

    private fun createNotification(partner: User?): android.app.Notification {
        val collapsedView = RemoteViews(packageName, R.layout.notification_custom_small)
        val expandedView = RemoteViews(packageName, R.layout.notification_custom_expanded)

        if (partner != null) {
            val titleText = "${partner.name}'s activity"
            collapsedView.setTextViewText(R.id.notification_title, titleText)
            
            expandedView.setTextViewText(R.id.notification_partner_name, "${partner.name}'s")
            expandedView.setTextViewText(R.id.notification_activity_status, "activity")

            val chargingSign = if (partner.isCharging == true) " ⚡" else ""
            val batteryPercent = partner.batteryLevel?.let { "${(it * 100).toInt()}%$chargingSign" } ?: "--%"
            collapsedView.setTextViewText(R.id.notification_battery, batteryPercent)
            expandedView.setTextViewText(R.id.notification_battery, batteryPercent)

            // Set dynamic battery icon
            val batteryRes = when {
                partner.isCharging == true -> R.drawable.ic_battery_charging
                partner.batteryLevel == null -> R.drawable.ic_battery_status
                partner.batteryLevel <= 0.20f -> R.drawable.ic_battery_20
                partner.batteryLevel <= 0.50f -> R.drawable.ic_battery_50
                partner.batteryLevel <= 0.80f -> R.drawable.ic_battery_80
                else -> R.drawable.ic_battery_100
            }
            collapsedView.setImageViewResource(R.id.notification_battery_icon, batteryRes)
            expandedView.setImageViewResource(R.id.notification_battery_icon, batteryRes)

            // Color handling for notification icons (must use setInt or similar for RemoteViews if needed, 
            // but usually standard drawables are better. Let's stick to the charging icon swap for now).

            val networkDisplay = when (partner.connectionType) {
                "Cellular" -> "Cell"
                null -> "Unknown"
                else -> partner.connectionType
            }
            collapsedView.setTextViewText(R.id.notification_network, networkDisplay)
            expandedView.setTextViewText(R.id.notification_network, networkDisplay)

            // Speed display
            val speedMs = partner.speed ?: 0f
            if (speedMs > 0.5f) {
                val speedKmh = (speedMs * 3.6f).toInt()
                val speedText = "$speedKmh km/h"
                
                collapsedView.setViewVisibility(R.id.notification_speed_icon, android.view.View.VISIBLE)
                collapsedView.setViewVisibility(R.id.notification_speed, android.view.View.VISIBLE)
                collapsedView.setTextViewText(R.id.notification_speed, speedText)
                
                expandedView.setViewVisibility(R.id.notification_speed_icon, android.view.View.VISIBLE)
                expandedView.setViewVisibility(R.id.notification_speed, android.view.View.VISIBLE)
                expandedView.setTextViewText(R.id.notification_speed, speedText)
            } else {
                collapsedView.setViewVisibility(R.id.notification_speed_icon, android.view.View.GONE)
                collapsedView.setViewVisibility(R.id.notification_speed, android.view.View.GONE)
                
                expandedView.setViewVisibility(R.id.notification_speed_icon, android.view.View.GONE)
                expandedView.setViewVisibility(R.id.notification_speed, android.view.View.GONE)
            }

            // Set static signal icon
            val signalRes = R.drawable.ic_signal_status
            collapsedView.setImageViewResource(R.id.notification_signal_icon, signalRes)
            expandedView.setImageViewResource(R.id.notification_signal_icon, signalRes)

            val address = getAddress(partner.latitude, partner.longitude)
            expandedView.setTextViewText(R.id.notification_address, address)

            // Use cached bitmap if available
            partnerAvatarBitmap?.let {
                collapsedView.setImageViewBitmap(R.id.notification_avatar, it)
                expandedView.setImageViewBitmap(R.id.notification_avatar, it)
            } ?: run {
                // Fallback or placeholder if needed, though Glide should update it
                collapsedView.setImageViewResource(R.id.notification_avatar, R.drawable.avatar1)
                expandedView.setImageViewResource(R.id.notification_avatar, R.drawable.avatar1)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setWhen(0)
            .build()
    }

    private fun getAddress(lat: Double?, lon: Double?): String {
        if (lat == null || lon == null) return "Location unknown"
        
        // Cache check: only re-fetch if moved more than 30 meters
        lastAddressLocation?.let { (lastLat, lastLon) ->
            if (calculateDistance(lat, lon, lastLat, lastLon) < 30 && lastAddress != null) {
                return lastAddress!!
            }
        }

        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val result = if (!addresses.isNullOrEmpty()) {
                val addressLine = addresses[0].getAddressLine(0)
                if (addressLine.isNullOrEmpty()) "Unknown location" else addressLine
            } else {
                "Unknown location"
            }
            lastAddress = result
            lastAddressLocation = lat to lon
            result
        } catch (_: Exception) {
            lastAddress ?: "Locating..."
        }
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun stopLocationService() {
        isServiceStarted = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
