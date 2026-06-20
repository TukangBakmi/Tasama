package com.example.tasama

import android.app.Application
import com.example.tasama.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class TasamaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Google Maps with the latest renderer
        com.google.android.gms.maps.MapsInitializer.initialize(this, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST) {
            android.util.Log.d("MapsInitializer", "Maps SDK initialized with: $it")
        }

        initKoin {
            androidLogger()
            androidContext(this@TasamaApp)
        }
    }
}
