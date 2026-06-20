package com.example.tasama.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

expect fun createDataStore(context: Any? = null): DataStore<Preferences>

const val DATASTORE_FILE_NAME = "tasama_settings.preferences_pb"
