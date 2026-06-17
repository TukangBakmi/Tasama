package com.example.tasama

import android.app.Application
import com.example.tasama.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class TasamaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            androidContext(this@TasamaApp)
        }
    }
}
