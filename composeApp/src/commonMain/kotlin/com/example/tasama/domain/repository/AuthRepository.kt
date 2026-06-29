package com.example.tasama.domain.repository

import com.example.tasama.domain.model.User
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
    suspend fun getUserShortId(uid: String): String?
    suspend fun getUserIdFromShortId(shortId: String): String?
    suspend fun updateFcmToken(uid: String, token: String?)
    suspend fun isGuest(): Boolean
    suspend fun getUser(uid: String): User?
    fun getUserFlow(uid: String): Flow<User?>
    suspend fun uploadProfilePicture(uid: String, bytes: ByteArray): String
    suspend fun updateProfilePicture(uid: String, url: String)
    suspend fun updateDisplayName(uid: String, name: String)
    suspend fun updateLocation(uid: String, lat: Double, lon: Double)
    suspend fun updateLastActive(uid: String, timestamp: Long? = null)
    suspend fun sendPartnerRequest(uid: String, partnerShortId: String): Result<Unit>
    suspend fun acceptPartnerRequest(uid: String, anniversaryDate: Long): Result<Unit>
    suspend fun declinePartnerRequest(uid: String): Result<Unit>
    suspend fun cancelPartnerRequest(uid: String): Result<Unit>
    suspend fun unlinkPartner(uid: String): Result<Unit>
}
