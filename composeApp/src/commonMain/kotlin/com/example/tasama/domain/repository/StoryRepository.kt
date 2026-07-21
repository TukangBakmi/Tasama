package com.example.tasama.domain.repository

import com.example.tasama.domain.model.Story
import kotlinx.coroutines.flow.Flow

interface StoryRepository {
    fun getStories(userId: String): Flow<List<Story>>
    suspend fun addStory(userId: String, story: Story)
    suspend fun deleteStory(userId: String, story: Story)
    suspend fun deleteStoryPhoto(url: String)
    suspend fun updateStory(userId: String, story: Story)
    suspend fun uploadStoryPhoto(userId: String, bytes: ByteArray): String
}
