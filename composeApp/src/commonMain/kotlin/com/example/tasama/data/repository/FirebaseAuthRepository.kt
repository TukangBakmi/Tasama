package com.example.tasama.data.repository

import com.example.tasama.domain.model.User
import com.example.tasama.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
            val userData = User(
                id = user.uid,
                email = email,
                name = name
            )
            firestore.collection("users").document(user.uid).set(userData)
        }
    }

    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
    }

    override suspend fun signInWithGoogle(idToken: String) {
        // Note: idToken is obtained from the platform-specific Google Sign-In flow
        val credential = dev.gitlive.firebase.auth.GoogleAuthProvider.credential(idToken, null)
        auth.signInWithCredential(credential)
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
    }

    override suspend fun getUserName(uid: String): String? {
        return try {
            firestore.collection("users").document(uid).get().data<User>().name
        } catch (e: Exception) {
            null
        }
    }
}
