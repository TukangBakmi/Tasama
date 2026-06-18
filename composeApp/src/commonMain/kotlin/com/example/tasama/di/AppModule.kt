package com.example.tasama.di

import com.example.tasama.data.remote.GroqService
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
import com.example.tasama.presentation.profile.ProfileViewModel
import com.example.tasama.presentation.savings.SavingsViewModel
import com.example.tasama.presentation.transaction.TransactionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

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

    viewModel { DashboardViewModel(get()) }
    viewModel { TransactionViewModel(get()) }
    viewModel { AIViewModel(get(), get(), get()) }
    viewModel { SavingsViewModel(get()) }
    viewModel { ChatViewModel(get()) }
    viewModel { ChatListViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get()) }
    viewModel { MainViewModel(get()) }
    viewModel { LoginViewModel(get()) }
}