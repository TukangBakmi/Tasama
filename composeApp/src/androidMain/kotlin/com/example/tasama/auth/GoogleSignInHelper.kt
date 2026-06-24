package com.example.tasama.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSignInHelper(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): String? = withContext(Dispatchers.IO) {
        android.util.Log.i("GoogleSignIn", "signIn() called")
        try {
            val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("761665195026-0lqjs7j2b7p7j8mjcvb6ukckl0p10d0u.apps.googleusercontent.com")
                .setAutoSelectEnabled(false) // Set to false for testing to ensure the picker shows
                .build()

            val request: GetCredentialRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            android.util.Log.i("GoogleSignIn", "Calling getCredential...")
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            android.util.Log.i("GoogleSignIn", "getCredential returned successfully")
            
            handleSignIn(result)
        } catch (e: Exception) {
            android.util.Log.e("GoogleSignIn", "Sign in failed with exception: ${e.javaClass.simpleName} - ${e.message}", e)
            null
        }
    }

    private fun handleSignIn(result: GetCredentialResponse): String? {
        val credential = result.credential
        android.util.Log.i("GoogleSignIn", "Credential Type: ${credential.type}")
        
        return when (credential) {
            is GoogleIdTokenCredential -> {
                android.util.Log.i("GoogleSignIn", "Is GoogleIdTokenCredential")
                credential.idToken
            }
            else -> {
                android.util.Log.i("GoogleSignIn", "Attempting to parse CustomCredential: ${credential.type}")
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    android.util.Log.i("GoogleSignIn", "Successfully parsed GoogleIdTokenCredential from CustomCredential")
                    googleIdTokenCredential.idToken
                } catch (e: Exception) {
                    android.util.Log.e("GoogleSignIn", "Failed to parse credential: ${e.message}")
                    val keys = credential.data.keySet()
                    android.util.Log.e("GoogleSignIn", "Bundle keys: ${keys.joinToString(", ")}")
                    null
                }
            }
        }
    }

}
