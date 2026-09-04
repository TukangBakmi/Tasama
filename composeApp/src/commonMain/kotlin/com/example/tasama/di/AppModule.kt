package com.example.tasama.di

import com.example.tasama.data.repository.FirebaseAIChatRepository
import com.example.tasama.data.repository.FirebaseAuthRepository
import com.example.tasama.data.repository.FirebaseChatRepository
import com.example.tasama.data.repository.FirebaseLiveLocationRepository
import com.example.tasama.data.repository.FirebasePresenceRepository
import com.example.tasama.data.repository.FirebaseSavingsRepository
import com.example.tasama.data.repository.FirebaseTransactionRepository
import com.example.tasama.domain.repository.AIChatRepository
import com.example.tasama.domain.repository.AuthRepository
import com.example.tasama.domain.repository.ChatRepository
import com.example.tasama.domain.repository.LiveLocationRepository
import com.example.tasama.domain.repository.PresenceRepository
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
import com.example.tasama.data.repository.FirebasePlaceRepository
import com.example.tasama.data.repository.WeatherRepositoryImpl
import com.example.tasama.domain.repository.PlaceRepository
import com.example.tasama.domain.repository.WeatherRepository
import com.example.tasama.domain.service.GeofenceMonitor
import kotlinx.coroutines.MainScope
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

import com.example.tasama.data.repository.DataStoreDraftRepository
import com.example.tasama.data.repository.DataStoreSettingsRepository
import com.example.tasama.domain.repository.DraftRepository
import com.example.tasama.domain.repository.SettingsRepository

val appModule = module {

    single<SettingsRepository> { DataStoreSettingsRepository(get()) }
    single<DraftRepository> { DataStoreDraftRepository(get()) }

    single<TransactionRepository> {
        FirebaseTransactionRepository(get())
    }

    single<SavingsRepository> {
        FirebaseSavingsRepository(get())
    }

    single<PresenceRepository> {
        FirebasePresenceRepository(get(), get())
    }

    single<LiveLocationRepository> {
        FirebaseLiveLocationRepository()
    }

    single<AuthRepository> {
        FirebaseAuthRepository(
            lazy { get() },
            lazy { get() },
            lazy { get() },
            lazy { get() },
            lazy { get() },
            lazy { get() },
            lazy { get() }
        )
    }

    single<PlaceRepository> {
        FirebasePlaceRepository(get())
    }

    single<WeatherRepository> {
        WeatherRepositoryImpl()
    }

    single<ChatRepository> {
        FirebaseChatRepository(get())
    }

    single<AIChatRepository> {
        FirebaseAIChatRepository(get())
    }

    single { GeofenceMonitor(lazy { get<AuthRepository>() }, get(), MainScope()) }

    viewModel { DashboardViewModel(get(), get()) }
    viewModel { TransactionViewModel(get(), get()) }
    viewModel { AIViewModel(get(), get(), get(), get(), get()) }
    viewModel { SavingsViewModel(get(), get(), get()) }
    viewModel { ChatViewModel(get(), get(), get(), get(), get()) }
    viewModel { ChatListViewModel(get(), get(), get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }
    viewModel { PartnerViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MainViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { LoginViewModel(get()) }
}
