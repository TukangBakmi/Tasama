package com.example.tasama.di

import com.example.tasama.BuildConfig
import com.example.tasama.data.remote.GroqService
import com.example.tasama.data.service.AndroidExportService
import com.example.tasama.data.service.AndroidFileService
import com.example.tasama.data.local.createDataStore
import com.example.tasama.domain.service.ExportService
import com.example.tasama.domain.service.FileService
import com.example.tasama.data.repository.GoogleDirectionsRepository
import com.example.tasama.domain.repository.DirectionsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

actual val platformModule: Module = module {
    single<DataStore<Preferences>> { createDataStore(androidContext()) }
    single<ExportService> { AndroidExportService() }
    single<FileService> { AndroidFileService(androidContext()) }
    single { GroqService(apiKey = BuildConfig.GROQ_API_KEY) }
    single<DirectionsRepository> { GoogleDirectionsRepository(apiKey = BuildConfig.GOOGLE_MAPS_API_KEY) }
}
