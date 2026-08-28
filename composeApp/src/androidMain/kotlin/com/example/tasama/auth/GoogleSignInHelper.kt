package com.example.tasama.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSignInHelper(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val serverClientId = "761665195026-0lqjs7j2b7p7j8mjcvb6ukckl0p10d0u.apps.googleusercontent.com"

    suspend fun signIn(): String? = withContext(Dispatchers.IO) {
        android.util.Log.i("GoogleSignIn", "signIn() called. Server Client ID: $serverClientId")
        try {
            // Attempt 1: Filter by authorized accounts (faster, allows auto-select)
            android.util.Log.i("GoogleSignIn", "Attempt 1: Calling getCredential with filterByAuthorizedAccounts = true")
            val idToken = tryGetCredential(filterByAuthorizedAccounts = true)

            if (idToken != null) {
                android.util.Log.i("GoogleSignIn", "Attempt 1 successful")
                return@withContext idToken
            }

            // Attempt 2: Fallback - Don't filter, let user choose/add account
            android.util.Log.i("GoogleSignIn", "Attempt 1 returned no credentials, trying Attempt 2: filterByAuthorizedAccounts = false")
            val fallbackToken = tryGetCredential(filterByAuthorizedAccounts = false)
            
            if (fallbackToken == null) {
                android.util.Log.w("GoogleSignIn", "Attempt 2 also returned no credentials")
            }
            return@withContext fallbackToken

        } catch (e: GetCredentialCancellationException) {
            android.util.Log.i("GoogleSignIn", "User cancelled Google Sign-In")
            null
        } catch (e: Exception) {
            android.util.Log.e("GoogleSignIn", "Sign in failed with exception: ${e.javaClass.simpleName} - ${e.message}", e)
            if (e.message?.contains("internet connection", ignoreCase = true) == true || e is java.net.UnknownHostException) {
                throw Exception("Unable to connect. Please check your internet connection.")
            }
            throw e
        }
    }

    private suspend fun tryGetCredential(filterByAuthorizedAccounts: Boolean): String? {
        android.util.Log.d("GoogleSignIn", "Configuring GetGoogleIdOption: filter=$filterByAuthorizedAccounts, autoSelect=true")
        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            android.util.Log.i("GoogleSignIn", "Executing getCredential(filter=$filterByAuthorizedAccounts) on CredentialManager instance: $credentialManager")
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            android.util.Log.i("GoogleSignIn", "getCredential(filter=$filterByAuthorizedAccounts) returned successfully")
            handleSignIn(result)
        } catch (e: NoCredentialException) {
            android.util.Log.w("GoogleSignIn", "NoCredentialException (filter=$filterByAuthorizedAccounts): ${e.message}")
            null
        } catch (e: GetCredentialException) {
            // Handle the specific "No credentials available" error message which sometimes isn't typed as NoCredentialException
            if (e.message?.contains("No credentials available", ignoreCase = true) == true) {
                android.util.Log.w("GoogleSignIn", "GetCredentialException: No credentials available (filter=$filterByAuthorizedAccounts)")
                null
            } else {
                throw e
            }
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
