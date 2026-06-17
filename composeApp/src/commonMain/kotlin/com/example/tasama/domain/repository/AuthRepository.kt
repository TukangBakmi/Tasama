package com.example.tasama.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val userId: Flow<String?>
    suspend fun signInAnonymously()
    suspend fun signUp(email: String, password: String, name: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signInWithGoogle(idToken: String)
    suspend fun signOut()
    suspend fun sendPasswordResetEmail(email: String)
    fun getCurrentUserId(): String?
    suspend fun getUserName(uid: String): String?
}
