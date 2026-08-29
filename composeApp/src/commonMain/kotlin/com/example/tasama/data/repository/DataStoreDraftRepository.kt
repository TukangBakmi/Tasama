package com.example.tasama.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.tasama.domain.repository.DraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreDraftRepository(
    private val dataStore: DataStore<Preferences>
) : DraftRepository {

    override fun getDraft(channelId: String): Flow<String?> {
        val key = stringPreferencesKey("draft_$channelId")
        return dataStore.data.map { preferences ->
            preferences[key]
        }
    }

    override suspend fun saveDraft(channelId: String, draft: String) {
        val key = stringPreferencesKey("draft_$channelId")
        dataStore.edit { preferences ->
            if (draft.isBlank()) {
                preferences.remove(key)
            } else {
                preferences[key] = draft
            }
        }
    }

    override suspend fun clearDraft(channelId: String) {
        val key = stringPreferencesKey("draft_$channelId")
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    override fun getAllDrafts(): Flow<Map<String, String>> {
        return dataStore.data.map { preferences ->
            preferences.asMap()
                .filterKeys { it.name.startsWith("draft_") }
                .mapKeys { it.key.name.removePrefix("draft_") }
                .mapValues { it.value.toString() }
        }
    }
}
