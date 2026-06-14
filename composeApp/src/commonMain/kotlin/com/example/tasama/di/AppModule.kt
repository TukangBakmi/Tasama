package com.example.tasama.di

import com.example.tasama.data.repository.FakeTransactionRepository
import com.example.tasama.domain.repository.TransactionRepository
import com.example.tasama.presentation.dashboard.DashboardViewModel
import com.example.tasama.presentation.transaction.TransactionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    single<TransactionRepository> {
        FakeTransactionRepository()
    }

    viewModel { DashboardViewModel(get()) }
    viewModel { TransactionViewModel(get()) }
}