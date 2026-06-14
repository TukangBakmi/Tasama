package com.example.tasama.di

import com.example.tasama.data.service.JvmExportService
import com.example.tasama.data.service.JvmFileService
import com.example.tasama.domain.service.ExportService
import com.example.tasama.domain.service.FileService
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<ExportService> { JvmExportService() }
    single<FileService> { JvmFileService() }
}
