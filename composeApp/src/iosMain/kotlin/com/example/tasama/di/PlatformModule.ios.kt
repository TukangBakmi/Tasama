package com.example.tasama.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.tasama.data.local.createDataStore
import com.example.tasama.domain.service.ExportService
import com.example.tasama.domain.service.NoOpExportService
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<DataStore<Preferences>> { createDataStore() }
    single<ExportService> { NoOpExportService() }
}
