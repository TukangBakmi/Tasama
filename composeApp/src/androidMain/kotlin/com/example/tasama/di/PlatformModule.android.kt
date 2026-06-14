package com.example.tasama.di

import com.example.tasama.data.service.AndroidExportService
import com.example.tasama.domain.service.ExportService
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<ExportService> { AndroidExportService() }
}
