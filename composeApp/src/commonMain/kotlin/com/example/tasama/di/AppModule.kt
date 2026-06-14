package com.example.tasama.di

import com.example.tasama.data.remote.GeminiService
import com.example.tasama.data.repository.FakeChatRepository
import com.example.tasama.data.repository.FakeSavingsRepository
import com.example.tasama.data.repository.FakeTransactionRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.domain.repository.TransactionRepository
import com.example.tasama.presentation.ai.AIViewModel
import com.example.tasama.presentation.chat.ChatViewModel
import com.example.tasama.presentation.dashboard.DashboardViewModel
import com.example.tasama.presentation.profile.ProfileViewModel
import com.example.tasama.presentation.savings.SavingsViewModel
import com.example.tasama.presentation.transaction.TransactionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    single<TransactionRepository> {
        FakeTransactionRepository()
    }

    single<SavingsRepository> {
        FakeSavingsRepository()
    }

    single<ChatRepository> {
        FakeChatRepository()
    }

    single { GeminiService() }

    viewModel { DashboardViewModel(get()) }
    viewModel { TransactionViewModel(get()) }
    viewModel { AIViewModel(get(), get()) }
    viewModel { SavingsViewModel(get()) }
    viewModel { ChatViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
}