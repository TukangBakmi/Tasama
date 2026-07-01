package com.example.tasama.data.repository

import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

class FirebaseAuthRepository : AuthRepository {
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore

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

    override suspend fun signUp(email: String, password: String, name: String) {
        val result = auth.createUserWithEmailAndPassword(email, password)
        val user = result.user
        if (user != null) {
            val shortId = generateShortId()
            val userData = User(
                id = user.uid,
                shortId = shortId,
                email = email,
                name = name
            )
            firestore.collection("users").document(user.uid).set(userData)
        }
    }

    private fun generateShortId(): String {
        return (1..12).map { (0..9).random() }.joinToString("")
    }

    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
    }

    override suspend fun signInWithGoogle(idToken: String) {
        val credential = dev.gitlive.firebase.auth.GoogleAuthProvider.credential(idToken, null)
        val result = auth.signInWithCredential(credential)
        val user = result.user
        if (user != null) {
            val doc = firestore.collection("users").document(user.uid).get()
            if (!doc.exists) {
                val userData = User(
                    id = user.uid,
                    shortId = generateShortId(),
                    email = user.email ?: "",
                    name = user.displayName ?: "User"
                )
                firestore.collection("users").document(user.uid).set(userData)
            }
        }
    }

    override suspend fun signOut() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            updateFcmToken(uid, null)
        }
        auth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
    }

    override suspend fun getUserName(uid: String): String? {
        return try {
            firestore.collection("users").document(uid).get().data<User>().name
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getUserShortId(uid: String): String? {
        return try {
            val user = firestore.collection("users").document(uid).get().data<User>()
            user.shortId.ifEmpty {
                val newShortId = generateShortId()
                firestore.collection("users")
                    .document(uid)
                    .updateFields {
                        "shortId" to newShortId
                    }
                newShortId
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getUserIdFromShortId(shortId: String): String? {
        return try {
            val query = firestore.collection("users")
                .where { "shortId" equalTo shortId }
                .get()
            query.documents.firstOrNull()?.data<User>()?.id
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun updateFcmToken(uid: String, token: String?) {
        try {
            firestore.collection("users")
                .document(uid)
                .updateFields {
                    "fcmToken" to token
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun isGuest(): Boolean {
        return auth.currentUser?.isAnonymous ?: false
    }

    override suspend fun getUser(uid: String): User? {
        return try {
            firestore.collection("users").document(uid).get().data<User>()
        } catch (_: Exception) {
            null
        }
    }

    override fun getUserFlow(uid: String): Flow<User?> {
        return firestore.collection("users").document(uid).snapshots.map { snapshot ->
            try {
                snapshot.data<User>()
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun uploadProfilePicture(uid: String, bytes: ByteArray): String {
        try {
            // Using the default storage bucket from google-services.json
            val storage = Firebase.storage
            val ref = storage.reference.child("profile_pictures/${uid}.jpg")
            val data = createStorageData(bytes)
            
            println("Starting upload to: ${ref.path}")
            
            // GitLive Firebase: putData returns a Flow<UploadState>.
            // We must collect the flow to await the upload's completion.
            ref.putData(data)
            
            println("Upload completed successfully. Fetching download URL...")
            return ref.getDownloadUrl()
        } catch (e: Exception) {
            println("Firebase Storage Error: ${e.message}")
            if (e.message?.contains("404") == true) {
                println("HINT: A 404 error usually means the Storage bucket is not initialized.")
                println("Go to Firebase Console -> Storage and click 'Get Started' to initialize it.")
            }
            throw e
        }
    }

    override suspend fun updateProfilePicture(uid: String, url: String?) {
        try {
            firestore.collection("users")
                .document(uid)
                .updateFields {
                    "avatarUrl" to url
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun updateDisplayName(uid: String, name: String) {
        try {
            firestore.collection("users")
                .document(uid)
                .updateFields {
                    "name" to name
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun updateLocation(uid: String, lat: Double, lon: Double, speed: Float?) {
        try {
            firestore.collection("users").document(uid).updateFields {
                "latitude" to lat
                "longitude" to lon
                "speed" to speed
                "lastLocationUpdate" to Clock.System.now().toEpochMilliseconds()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun updateBatteryLevel(uid: String, level: Float, isCharging: Boolean) {
        try {
            firestore.collection("users").document(uid).updateFields {
                "batteryLevel" to level
                "isCharging" to isCharging
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun updateLastActive(uid: String, timestamp: Long?) {
        try {
            firestore.collection("users").document(uid).updateFields {
                "lastActive" to (timestamp ?: Clock.System.now().toEpochMilliseconds())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun sendPartnerRequest(uid: String, partnerShortId: String): Result<Unit> {
        return try {
            val partnerUid = getUserIdFromShortId(partnerShortId)
                ?: return Result.failure(Exception("Partner not found"))

            if (partnerUid == uid) {
                return Result.failure(Exception("You cannot link with yourself"))
            }

            // Check if partner is already linked or has a pending request
            val partner = getUser(partnerUid) ?: return Result.failure(Exception("Partner not found"))
            if (partner.partnerId != null) return Result.failure(Exception("This user already has a partner"))
            if (partner.partnerRequestFrom != null) return Result.failure(Exception("This user already has a pending request"))

            // Update sender
            firestore.collection("users").document(uid).updateFields {
                "partnerRequestTo" to partnerUid
            }
            // Update recipient
            firestore.collection("users").document(partnerUid).updateFields {
                "partnerRequestFrom" to uid
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptPartnerRequest(uid: String, anniversaryDate: Long): Result<Unit> {
        return try {
            val user = getUser(uid) ?: return Result.failure(Exception("User not found"))
            val partnerUid = user.partnerRequestFrom ?: return Result.failure(Exception("No pending request"))

            // Link both users
            firestore.collection("users").document(uid).updateFields {
                "partnerId" to partnerUid
                "anniversaryDate" to anniversaryDate
                "partnerRequestFrom" to null
            }
            firestore.collection("users").document(partnerUid).updateFields {
                "partnerId" to uid
                "anniversaryDate" to anniversaryDate
                "partnerRequestTo" to null
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun declinePartnerRequest(uid: String): Result<Unit> {
        return try {
            val user = getUser(uid) ?: return Result.failure(Exception("User not found"))
            val partnerUid = user.partnerRequestFrom ?: return Result.failure(Exception("No pending request"))

            firestore.collection("users").document(uid).updateFields { "partnerRequestFrom" to null }
            firestore.collection("users").document(partnerUid).updateFields { "partnerRequestTo" to null }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelPartnerRequest(uid: String): Result<Unit> {
        return try {
            val user = getUser(uid) ?: return Result.failure(Exception("User not found"))
            val partnerUid = user.partnerRequestTo ?: return Result.failure(Exception("No pending request"))

            firestore.collection("users").document(uid).updateFields { "partnerRequestTo" to null }
            firestore.collection("users").document(partnerUid).updateFields { "partnerRequestFrom" to null }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlinkPartner(uid: String): Result<Unit> {
        return try {
            val user = getUser(uid) ?: return Result.failure(Exception("User not found"))
            val partnerId = user.partnerId

            if (partnerId != null) {
                firestore.collection("users").document(uid).updateFields { 
                    "partnerId" to null 
                    "anniversaryDate" to null
                }
                firestore.collection("users").document(partnerId).updateFields { 
                    "partnerId" to null 
                    "anniversaryDate" to null
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateAnniversaryDate(uid: String, date: Long): Result<Unit> {
        return try {
            val user = getUser(uid) ?: return Result.failure(Exception("User not found"))
            val partnerId = user.partnerId

            firestore.collection("users").document(uid).updateFields {
                "anniversaryDate" to date
            }
            if (partnerId != null) {
                firestore.collection("users").document(partnerId).updateFields {
                    "anniversaryDate" to date
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
