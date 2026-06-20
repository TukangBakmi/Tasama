package com.example.tasama.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

actual fun createDataStore(context: Any?): DataStore<Preferences> {
    require(context is Context) { "Android context is required" }
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = { context.filesDir.resolve(DATASTORE_FILE_NAME).absolutePath.toPath() }
    )
}
