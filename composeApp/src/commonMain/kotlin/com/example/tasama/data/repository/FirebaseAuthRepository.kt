package com.example.tasama.data.repository

import com.example.tasama.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseAuthRepository : AuthRepository {
    private val auth = Firebase.auth

    override val userId: Flow<String?> = auth.authStateChanged.map { it?.uid }

    override suspend fun signInAnonymously() {
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
}
