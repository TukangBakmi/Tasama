package com.example.tasama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import com.example.tasama.auth.GoogleSignInHelper
import com.example.tasama.domain.repository.AuthRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val authRepository: AuthRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val googleSignInHelper = GoogleSignInHelper(this)

        setContent {
            val scope = rememberCoroutineScope()
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
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}