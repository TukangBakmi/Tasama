package com.example.tasama.di

import com.example.tasama.data.remote.FcmService
import com.example.tasama.data.remote.GroqService
import com.example.tasama.data.service.JvmExportService
import com.example.tasama.data.service.JvmFileService
import com.example.tasama.domain.service.ExportService
import com.example.tasama.domain.service.FileService
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<ExportService> { JvmExportService() }
    single<FileService> { JvmFileService() }
    single { GroqService(apiKey = System.getenv("GROQ_API_KEY") ?: "") }
    single { 
        FcmService(
            projectId = System.getenv("FCM_PROJECT_ID") ?: "",
            accessToken = System.getenv("FCM_ACCESS_TOKEN") ?: ""
        ) 
    }
}
