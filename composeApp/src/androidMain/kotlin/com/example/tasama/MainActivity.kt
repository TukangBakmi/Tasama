package com.example.tasama

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.tasama.domain.model.AppSettings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.tasama.auth.GoogleSignInHelper
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.model.User
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.presentation.main.AuthState
import com.example.tasama.presentation.main.MainViewModel
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {
    
    private val authRepository: AuthRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val partnerViewModel: com.example.tasama.presentation.partner.PartnerViewModel by viewModel()
    private val mainViewModel: MainViewModel by viewModel()
    private val loginViewModel: com.example.tasama.presentation.login.LoginViewModel by viewModel()

    private var initialChannelId by mutableStateOf<String?>(null)
    private var navigateToTab by mutableStateOf<String?>(null)

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> mainViewModel.setForeground(true)
            Lifecycle.Event.ON_STOP -> mainViewModel.setForeground(false)
            else -> {}
        }
    }

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
        val splashScreen = installSplashScreen()
        android.util.Log.d("TasamaSplash", "MainActivity.onCreate - Started")
        
        var appSettings by mutableStateOf<AppSettings?>(null)
        
        // Block splash screen until settings are loaded
        splashScreen.setKeepOnScreenCondition { appSettings == null }

        lifecycleScope.launch {
            appSettings = settingsRepository.settings.first()
            appSettings?.let { setNightMode(it.theme) }
        }

        // Ensure authState flow is active by subscribing to it
        lifecycleScope.launch {
            mainViewModel.authState.collect { state ->
                android.util.Log.d("TasamaSplash", "MainActivity - observed AuthState: $state")
            }
        }
        val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        android.util.Log.d("TasamaTheme", "MainActivity.onCreate - UI Mode: $uiMode (Night: ${android.content.res.Configuration.UI_MODE_NIGHT_YES}, Light: ${android.content.res.Configuration.UI_MODE_NIGHT_NO})")

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            android.util.Log.d("TasamaFCM", "Current token: $token")
        }

        askPermissions()
        startBatteryMonitoring()
        com.example.tasama.util.initGeocoding(this)

        handleIntent(intent)

        val googleSignInHelper = GoogleSignInHelper(this)

        setContent {
            val scope = rememberCoroutineScope()
            val settings by mainViewModel.settings.collectAsState()

            // Update System Night Mode reactively on Android
            LaunchedEffect(settings.theme) {
                setNightMode(settings.theme)
            }

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
                initialTheme = appSettings?.theme,
                initialChannelId = initialChannelId,
                navigateToTab = navigateToTab,
                onChannelNavigated = { initialChannelId = null },
                onTabNavigated = { navigateToTab = null },
                onGoogleSignInClick = {
                    println("[GOOGLE] MainActivity onGoogleSignInClick triggered. VM: ${loginViewModel.hashCode()}")
                    if (loginViewModel.uiState.value.isGoogleLoading) {
                        println("[GOOGLE] Already loading, ignoring click")
                        return@App
                    }

                    scope.launch {
                        println("[GOOGLE] Setting loading = true")
                        loginViewModel.setGoogleLoading(true)
                        println("[GOOGLE] current uiState.isGoogleLoading = ${loginViewModel.uiState.value.isGoogleLoading}")
                        
                        println("[GOOGLE] Launching googleSignInHelper.signIn()")
                        try {
                            val idToken = googleSignInHelper.signIn()
                            if (idToken != null) {
                                println("[GOOGLE] Got ID Token, signing into Firebase...")
                                try {
                                    authRepository.signInWithGoogle(idToken)
                                    println("[GOOGLE] Firebase Sign-In Successful")
                                    loginViewModel.setGoogleLoading(false)
                                } catch (e: Exception) {
                                    println("[GOOGLE] Firebase Sign-In Failed: ${e.message}")
                                    loginViewModel.setLoginError("Google Sign-In failed. Please try again.")
                                }
                            } else {
                                println("[GOOGLE] sign-in cancelled or failed (idToken is null)")
                                loginViewModel.setGoogleLoading(false)
                            }
                        } catch (e: Exception) {
                            println("[GOOGLE] Error during sign in process: ${e.message}")
                            val errorMessage = if (e.message?.contains("internet connection", ignoreCase = true) == true) {
                                e.message
                            } else {
                                "Google Sign-In failed. Please try again."
                            }
                            loginViewModel.setLoginError(errorMessage)
                        }
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("channelId")?.let {
            initialChannelId = it
        }

        val navigateTo = intent.getStringExtra("navigate_to")
        val type = intent.getStringExtra("type")

        if (navigateTo != null) {
            navigateToTab = navigateTo
        } else if (type != null) {
            // Map FCM type to tab destination if navigate_to is missing (e.g. background notifications)
            navigateToTab = when {
                type.startsWith("SAVINGS_") -> "savings"
                type.startsWith("CHAT_") || intent.hasExtra("channelId") -> "chat"
                type.startsWith("PARTNER_") || type.startsWith("LOCATION_") || type == "GEOFENCE" -> "partner"
                else -> null
            }
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

    private fun setNightMode(theme: AppTheme) {
        val mode = when (theme) {
            AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val applicationMode = when (theme) {
                AppTheme.LIGHT -> UiModeManager.MODE_NIGHT_NO
                AppTheme.DARK -> UiModeManager.MODE_NIGHT_YES
                AppTheme.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
            try {
                // Using reflection to set application night mode to avoid potential compilation issues
                // with property access if the environment is not synced correctly.
                val method = uiModeManager.javaClass.getMethod("setApplicationNightMode", Int::class.javaPrimitiveType)
                method.invoke(uiModeManager, applicationMode)
            } catch (e: Exception) {
                android.util.Log.e("TasamaTheme", "Failed to set application night mode", e)
            }
        }
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
