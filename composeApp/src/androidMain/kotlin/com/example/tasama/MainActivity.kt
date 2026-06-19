package com.example.tasama

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.tasama.auth.GoogleSignInHelper
import com.example.tasama.domain.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val authRepository: AuthRepository by inject()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            // Permission denied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        askNotificationPermission()

        val googleSignInHelper = GoogleSignInHelper(this)

        setContent {
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                authRepository.userId.collect { uid ->
                    if (uid != null) {
                        try {
                            val token = FirebaseMessaging.getInstance().token.await()
                            authRepository.updateFcmToken(uid, token)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            App(
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

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
