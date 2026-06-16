package com.example.tasama.di

import com.example.tasama.BuildConfig
import com.example.tasama.data.remote.GroqService
import com.example.tasama.data.service.AndroidExportService
import com.example.tasama.data.service.AndroidFileService
import com.example.tasama.domain.service.ExportService
import com.example.tasama.domain.service.FileService
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<ExportService> { AndroidExportService() }
    single<FileService> { AndroidFileService(androidContext()) }
    single { GroqService(apiKey = BuildConfig.GROQ_API_KEY) }
}
