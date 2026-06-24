package com.example.tasama.di

import com.example.tasama.data.repository.FirebaseAIChatRepository
import com.example.tasama.data.repository.FirebaseAuthRepository
import com.example.tasama.data.repository.FirebaseChatRepository
import com.example.tasama.data.repository.FirebaseSavingsRepository
import com.example.tasama.data.repository.FirebaseTransactionRepository
import com.example.tasama.domain.repository.AIChatRepository
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.SavingsRepository
import com.example.tasama.domain.repository.TransactionRepository
import com.example.tasama.presentation.ai.AIViewModel
import com.example.tasama.presentation.chat.ChatListViewModel
import com.example.tasama.presentation.chat.ChatViewModel
import com.example.tasama.presentation.dashboard.DashboardViewModel
import com.example.tasama.presentation.login.LoginViewModel
import com.example.tasama.presentation.main.MainViewModel
import com.example.tasama.presentation.partner.PartnerViewModel
import com.example.tasama.presentation.profile.ProfileViewModel
import com.example.tasama.presentation.savings.SavingsViewModel
import com.example.tasama.presentation.transaction.TransactionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

import com.example.tasama.data.repository.DataStoreSettingsRepository
import com.example.tasama.domain.repository.SettingsRepository

val appModule = module {

    single<SettingsRepository> { DataStoreSettingsRepository(get()) }

    single<TransactionRepository> {
        FirebaseTransactionRepository(get())
    }

    single<SavingsRepository> {
        FirebaseSavingsRepository(get())
    }

    single<AuthRepository> {
        FirebaseAuthRepository()
    }

    single<ChatRepository> {
        FirebaseChatRepository(get())
    }

    single<AIChatRepository> {
        FirebaseAIChatRepository(get())
    }

    viewModel { DashboardViewModel(get(), get()) }
    viewModel { TransactionViewModel(get(), get()) }
    viewModel { AIViewModel(get(), get(), get(), get()) }
    viewModel { SavingsViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get()) }
    viewModel { ChatListViewModel(get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }
    viewModel { PartnerViewModel(get()) }
    viewModel { MainViewModel(get(), get()) }
    viewModel { LoginViewModel(get()) }
}