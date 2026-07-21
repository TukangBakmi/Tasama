package com.example.tasama.data.repository

import com.example.tasama.domain.model.Story
import com.example.tasama.domain.repository.StoryRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.storage.Data
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseStoryRepository : StoryRepository {
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage

    override fun getStories(userId: String): Flow<List<Story>> {
        return firestore.collection("users").document(userId).collection("stories").snapshots.map { snapshot ->
            snapshot.documents.map { doc ->
                val story: Story = doc.data()
                story.copy(id = doc.id)
            }
        }
    }

    override suspend fun addStory(userId: String, story: Story) {
        val collection = firestore.collection("users").document(userId).collection("stories")
        
        val id = if (story.id.isNotBlank()) {
            story.id
        } else {
            (1..20).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
        }

        val newStory = story.copy(id = id, createdBy = userId)
        collection.document(id).set(newStory)
    }

    override suspend fun updateStory(userId: String, story: Story) {
        if (story.id.isBlank()) return
        firestore.collection("users").document(userId).collection("stories").document(story.id).set(story)
    }

    override suspend fun deleteStory(userId: String, story: Story) {
        if (story.id.isBlank()) return

        // Delete photo files from storage
        story.photoUrls.forEach { url ->
            try {
                deleteStoryPhoto(url)
            } catch (e: Exception) {
                // Log or handle individual deletion failure
            }
        }
        
        firestore.collection("users").document(userId).collection("stories").document(story.id).delete()
    }

    override suspend fun deleteStoryPhoto(url: String) {
        try {
            val ref = storage.getReferenceFromUrl(url)
            ref.delete()
        } catch (e: Exception) {
            // Might fail if it's not a firebase storage URL
        }
    }

    override suspend fun uploadStoryPhoto(userId: String, bytes: ByteArray): String {
        val fileName = "${(1..10).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")}.jpg"
        val ref = storage.reference.child("stories/$userId/$fileName")
        val data = createStorageData(bytes)
        ref.putData(data)
        return ref.getDownloadUrl()
    }
}
