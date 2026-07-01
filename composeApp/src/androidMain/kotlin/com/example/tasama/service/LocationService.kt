package com.example.tasama.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import kotlinx.coroutines.flow.collectLatest
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
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
            val uid = authRepository.getCurrentUserId() ?: return@launch
            authRepository.getUserFlow(uid).collectLatest { user ->
                val partnerId = user?.partnerId
                if (partnerId != null) {
                    authRepository.getUserFlow(partnerId).collectLatest { partner ->
                        if (partner != null) {
                            lastPartnerData = partner
                            updateNotification(partner)
                        }
                    }
                }
            }
        }
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
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "Offline"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "Offline"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Offline"
        }
    }

    private fun updateNotification(partner: User) {
        if (partner.avatarUrl != lastPartnerData?.avatarUrl) {
            partnerAvatarBitmap = null
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (partnerAvatarBitmap == null && partner.avatarUrl != null) {
            Glide.with(this)
                .asBitmap()
                .load(partner.avatarUrl)
                .circleCrop()
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        partnerAvatarBitmap = resource
                        notificationManager.notify(NOTIFICATION_ID, createNotification(partner))
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
        
        notificationManager.notify(NOTIFICATION_ID, createNotification(partner))
    }

    private fun createNotification(partner: User?): android.app.Notification {
        val collapsedView = RemoteViews(packageName, R.layout.notification_custom_small)
        val expandedView = RemoteViews(packageName, R.layout.notification_custom_expanded)

        if (partner != null) {
            val activityStatus = when {
                (partner.speed ?: 0f) > 10f -> " is Driving"
                (partner.speed ?: 0f) > 1.5f -> " is Moving"
                else -> "'s activity"
            }
            
            val titleText = "${partner.name}$activityStatus"
            collapsedView.setTextViewText(R.id.notification_title, titleText)
            
            expandedView.setTextViewText(R.id.notification_partner_name, "${partner.name}'s")

            val batteryPercent = partner.batteryLevel?.let { "${(it * 100).toInt()}%" } ?: "--%"
            collapsedView.setTextViewText(R.id.notification_battery, batteryPercent)
            expandedView.setTextViewText(R.id.notification_battery, batteryPercent)

            val network = partner.connectionType ?: "Unknown"
            collapsedView.setTextViewText(R.id.notification_network, network)
            expandedView.setTextViewText(R.id.notification_network, network)

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

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun getAddress(lat: Double?, lon: Double?): String {
        if (lat == null || lon == null) return "Location unknown"
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addressLine = addresses[0].getAddressLine(0)
                if (addressLine.isNullOrEmpty()) "Unknown location" else addressLine
            } else {
                "Unknown location"
            }
        } catch (e: Exception) {
            "Locating..."
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
        } catch (unlikely: SecurityException) {
            stopSelf()
        }
    }

    private fun stopLocationService() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
