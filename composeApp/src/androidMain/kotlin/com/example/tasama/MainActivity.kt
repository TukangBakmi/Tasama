package com.example.tasama

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.tasama.auth.GoogleSignInHelper
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.model.User
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val authRepository: AuthRepository by inject()
    private val partnerViewModel: com.example.tasama.presentation.partner.PartnerViewModel by inject()

    private var initialChannelId by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            // Permission granted, our LaunchedEffect will handle starting the service if a partner exists
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            android.util.Log.d("TasamaFCM", "Current token: $token")
        }

        askPermissions()
        startBatteryMonitoring()

        initialChannelId = intent.getStringExtra("channelId")

        val googleSignInHelper = GoogleSignInHelper(this)

        setContent {
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                authRepository.userId.collectLatest { uid: String? ->
                    if (uid != null) {
                        try {
                            val token = FirebaseMessaging.getInstance().token.await()
                            android.util.Log.d("FCM", "Token retrieved: $token")
                            authRepository.updateFcmToken(uid, token)
                        } catch (e: Exception) {
                            android.util.Log.e("FCM", "Failed to get/update token", e)
                        }

                        // Monitor partner status to start/stop LocationService
                        authRepository.getUserFlow(uid).collectLatest { user: User? ->
                            if (user?.partnerId != null) {
                                if (ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    startLocationUpdates()
                                }
                            } else {
                                stopLocationService()
                            }
                        }
                    } else {
                        stopLocationService()
                    }
                }
            }

            App(
                initialChannelId = initialChannelId,
                onChannelNavigated = { initialChannelId = null },
                onGoogleSignInClick = {
                    scope.launch {
                        android.util.Log.d("GoogleSignIn", "Sign-in button clicked")
                        val idToken = googleSignInHelper.signIn()
                        if (idToken != null) {
                            android.util.Log.d("GoogleSignIn", "Got ID Token, signing into Firebase...")
                            try {
                                authRepository.signInWithGoogle(idToken)
                                android.util.Log.d("GoogleSignIn", "Firebase Sign-In Successful")
                            } catch (e: Exception) {
                                android.util.Log.e("GoogleSignIn", "Firebase Sign-In Failed", e)
                            }
                        } else {
                            android.util.Log.e("GoogleSignIn", "Failed to get ID Token (idToken is null)")
                        }
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("channelId")?.let {
            initialChannelId = it
        }
    }

    private fun askPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    private fun startLocationUpdates() {
        val intent = Intent(this, com.example.tasama.service.LocationService::class.java).apply {
            action = com.example.tasama.service.LocationService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLocationService() {
        val intent = Intent(this, com.example.tasama.service.LocationService::class.java).apply {
            action = com.example.tasama.service.LocationService.ACTION_STOP
        }
        startService(intent)
    }

    private fun startBatteryMonitoring() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(null, ifilter)
        }

        batteryStatus?.let { updateBatteryInfo(it) }

        val batteryReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                updateBatteryInfo(intent)
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun updateBatteryInfo(intent: Intent) {
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = level / scale.toFloat()

        val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        partnerViewModel.updateBatteryLevel(batteryPct, isCharging)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
