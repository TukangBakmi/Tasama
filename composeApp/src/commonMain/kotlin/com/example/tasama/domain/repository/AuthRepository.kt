package com.example.tasama.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val userId: Flow<String?>
    suspend fun signInAnonymously()
    fun getCurrentUserId(): String?
}
