package com.example.tasama.data.repository

import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthRepository(
    private val chatRepository: Lazy<ChatRepository>,
    private val savingsRepository: Lazy<SavingsRepository>,
    private val transactionRepository: Lazy<TransactionRepository>,
    private val presenceRepository: Lazy<PresenceRepository>,
    private val aiChatRepository: Lazy<AIChatRepository>,
    private val placeRepository: Lazy<PlaceRepository>,
    private val geofenceMonitor: Lazy<com.example.tasama.domain.service.GeofenceMonitor>
) : AuthRepository {
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var _sessionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    override val sessionScope: CoroutineScope get() = _sessionScope

    private val _userId = MutableStateFlow<String?>(null)
    private val _isLoggingOut = MutableStateFlow(false)
    private val _isInitialized = MutableStateFlow(false)
    override val isLoggingOut = _isLoggingOut.asStateFlow()

    override val userId: Flow<String?> = _isInitialized
        .filter { it }
        .flatMapLatest {
            combine(_userId, _isLoggingOut) { uid, loggingOut ->
                if (loggingOut) null else uid
            }
        }.distinctUntilChanged()

    init {
        repositoryScope.launch {
            auth.authStateChanged.collect { user ->
                println("DEBUG: [AUTH] AuthState changed: ${user?.uid}")
                _userId.value = user?.uid
                if (!_isInitialized.value) {
                    _isInitialized.value = true
                }
            }
        }
    }

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
        println("[GOOGLE] FirebaseAuthRepository.signInWithGoogle called")
        try {
            val credential = dev.gitlive.firebase.auth.GoogleAuthProvider.credential(idToken, null)
            val result = auth.signInWithCredential(credential)
            val user = result.user
            println("[GOOGLE] Firebase signInWithCredential successful. User UID: ${user?.uid}")
            if (user != null) {
                val doc = firestore.collection("users").document(user.uid).get()
                if (!doc.exists) {
                    println("[GOOGLE] Creating new user document in Firestore")
                    val userData = User(
                        id = user.uid,
                        shortId = generateShortId(),
                        email = user.email ?: "",
                        name = user.displayName ?: "User"
                    )
                    firestore.collection("users").document(user.uid).set(userData)
                }
            }
        } catch (e: Exception) {
            println("[GOOGLE] Firebase signInWithCredential failed: ${e.message}")
            throw e
        }
    }

    override suspend fun cleanupRepositories() {
        presenceRepository.value.cleanup()
        chatRepository.value.cleanup()
        savingsRepository.value.cleanup()
        transactionRepository.value.cleanup()
        aiChatRepository.value.cleanup()
        placeRepository.value.cleanup()
        geofenceMonitor.value.cleanup()
    }

    override suspend fun signOut() {
        println("DEBUG: [SESSION] Logout started")
        _isLoggingOut.value = true
        val uid = auth.currentUser?.uid
        if (uid != null) {
            try {
                println("DEBUG: [SESSION] Updating FCM token to null for $uid")
                updateFcmToken(uid, null)
            } catch (e: Exception) {
                println("DEBUG: [SESSION] Failed to update FCM token: ${e.message}")
            }
        }

        // 1. Cancel all user-scoped collectors by cancelling the session scope
        println("DEBUG: [SESSION] Cancelling Firestore listeners (via sessionScope)")
        _sessionScope.cancel()
        
        // 2. Coordinate cleanup of all repositories (RTDB, Presence, etc.)
        println("DEBUG: [SESSION] Calling cleanupRepositories()")
        cleanupRepositories()
        
        // 3. Create a new session scope for the next user
        _sessionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        
        // 4. Manually emit null to signal any remaining observers to stop
        println("DEBUG: [SESSION] Emitting null to userId flow")
        _userId.value = null

        // 5. Delay to ensure all listeners are removed and flows are cancelled
        println("DEBUG: [SESSION] Delaying to allow async cleanup and listener removal")
        kotlinx.coroutines.delay(500)
        println("DEBUG: [SESSION] Firestore listeners cancelled")

        // 6. Actual Firebase signOut
        try {
            println("DEBUG: [SESSION] Signing out Firebase Auth")
            auth.signOut()
            println("DEBUG: [SESSION] Sign out completed")
        } catch (e: Exception) {
            println("DEBUG: [SESSION] Firebase auth.signOut() failed: ${e.message}")
        } finally {
            _isLoggingOut.value = false
        }
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

    override suspend fun getUserIdByName(name: String): String? {
        return try {
            val query = firestore.collection("users")
                .where { "name" equalTo name }
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
        }.catch { e ->
            if (e.message?.contains("permission", ignoreCase = true) == true) {
                println("DEBUG: [AUTH] getUserFlow: Permission denied for $uid (expected during logout)")
            } else {
                println("ERROR: [AUTH] getUserFlow error for $uid: ${e.message}")
            }
            emit(null)
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

    override suspend fun updateLocation(uid: String, lat: Double, lon: Double, speed: Float?, accuracy: Float?) {
        try {
            val timestamp = Clock.System.now().toEpochMilliseconds()
            firestore.collection("users").document(uid).updateFields {
                "latitude" to lat
                "longitude" to lon
                "speed" to speed
                "accuracy" to accuracy
                "lastLocationUpdate" to timestamp
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

    override suspend fun updateConnectionType(uid: String, type: String) {
        try {
            firestore.collection("users").document(uid).updateFields {
                "connectionType" to type
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun addContact(uid: String, contactUid: String): Result<Unit> {
        return try {
            if (uid == contactUid) return Result.failure(Exception("You cannot add yourself as a contact"))
            
            val userRef = firestore.collection("users").document(uid)
            val user = userRef.get().data<User>()
            
            if (!user.contactIds.contains(contactUid)) {
                val newContacts = user.contactIds + contactUid
                userRef.updateFields {
                    "contactIds" to newContacts
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeContact(uid: String, contactUid: String): Result<Unit> {
        return try {
            val userRef = firestore.collection("users").document(uid)
            val user = userRef.get().data<User>()
            
            if (user.contactIds.contains(contactUid)) {
                val newContacts = user.contactIds.filter { it != contactUid }
                userRef.updateFields {
                    "contactIds" to newContacts
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPartnerRequest(uid: String, partnerShortId: String): Result<Unit> {
        return try {
            val sender = getUser(uid) ?: return Result.failure(Exception("User not found"))
            if (sender.partnerId != null) return Result.failure(Exception("You already have a partner"))
            if (sender.partnerRequestTo != null) return Result.failure(Exception("You already have a pending outgoing request"))
            if (sender.partnerRequestFrom != null) return Result.failure(Exception("You have a pending incoming request. Please accept or decline it first."))

            val partnerUid = getUserIdFromShortId(partnerShortId)
                ?: return Result.failure(Exception("Partner not found"))

            if (partnerUid == uid) {
                return Result.failure(Exception("You cannot link with yourself"))
            }

            // Check if partner is already linked or has a pending request
            val partner = getUser(partnerUid) ?: return Result.failure(Exception("Partner not found"))
            if (partner.partnerId != null) return Result.failure(Exception("This user already has a partner"))
            if (partner.partnerRequestFrom != null || partner.partnerRequestTo != null) {
                return Result.failure(Exception("This user already has a pending request"))
            }

            // Update sender
            firestore.collection("users").document(uid).updateFields {
                "partnerRequestTo" to partnerUid
            }
            // Update recipient
            firestore.collection("users").document(partnerUid).updateFields {
                "partnerRequestFrom" to uid
            }

            // Send notification
            sendNotification(
                targetUid = partnerUid,
                title = "Partner Request",
                body = "${sender.name} wants to link with you!",
                type = "PARTNER_REQUEST",
                senderName = sender.name,
                senderPhoto = sender.avatarUrl
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptPartnerRequest(uid: String, anniversaryDate: Long): Result<Unit> {
        return try {
            val user = getUser(uid) ?: return Result.failure(Exception("User not found"))
            if (user.partnerId != null) return Result.failure(Exception("You already have a partner"))
            
            val partnerUid = user.partnerRequestFrom ?: return Result.failure(Exception("No pending request"))
            val partner = getUser(partnerUid) ?: return Result.failure(Exception("Partner not found"))

            if (partner.partnerId != null) {
                // Partner linked with someone else in the meantime
                firestore.collection("users").document(uid).updateFields { "partnerRequestFrom" to null }
                return Result.failure(Exception("This user is no longer available"))
            }

            // Link both users
            val userContactIds = if (!user.contactIds.contains(partnerUid)) {
                user.contactIds + partnerUid
            } else {
                user.contactIds
            }

            val partnerContactIds = if (!partner.contactIds.contains(uid)) {
                partner.contactIds + uid
            } else {
                partner.contactIds
            }

            firestore.collection("users").document(uid).updateFields {
                "partnerId" to partnerUid
                "anniversaryDate" to anniversaryDate
                "partnerRequestFrom" to null
                "contactIds" to userContactIds
            }
            firestore.collection("users").document(partnerUid).updateFields {
                "partnerId" to uid
                "anniversaryDate" to anniversaryDate
                "partnerRequestTo" to null
                "contactIds" to partnerContactIds
            }

            // Send notification
            sendNotification(
                targetUid = partnerUid,
                title = "Partner Request Accepted",
                body = "${user.name} accepted your partner request!",
                type = "PARTNER_ACCEPT",
                senderName = user.name,
                senderPhoto = user.avatarUrl
            )

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

            // Send notification
            sendNotification(
                targetUid = partnerUid,
                title = "Partner Request Declined",
                body = "${user.name} declined your partner request.",
                type = "PARTNER_DECLINE",
                senderName = user.name,
                senderPhoto = user.avatarUrl
            )

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

            // Send notification
            sendNotification(
                targetUid = partnerUid,
                title = "Partner Request Cancelled",
                body = "${user.name} cancelled the partner request.",
                type = "PARTNER_CANCEL",
                senderName = user.name,
                senderPhoto = user.avatarUrl
            )

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

                // Send notification
                sendNotification(
                    targetUid = partnerId,
                    title = "Partner Unlinked",
                    body = "${user.name} has unlinked from you.",
                    type = "PARTNER_UNLINK",
                    senderName = user.name,
                    senderPhoto = user.avatarUrl
                )
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

    override suspend fun sendNotification(
        targetUid: String,
        title: String,
        body: String,
        type: String,
        senderName: String?,
        senderPhoto: String?,
        senderAvatar: String?
    ): Result<Unit> {
        return try {
            val notificationData = mutableMapOf<String, Any?>(
                "targetUid" to targetUid,
                "title" to title,
                "body" to body,
                "timestamp" to Clock.System.now().toEpochMilliseconds(),
                "read" to false,
                "type" to type
            )
            senderName?.let { notificationData["sender_name"] = it }
            senderPhoto?.let { notificationData["sender_photo"] = it }
            senderAvatar?.let { notificationData["sender_avatar"] = it }

            firestore.collection("notifications").add(notificationData)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
