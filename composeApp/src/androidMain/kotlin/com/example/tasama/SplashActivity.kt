package com.example.tasama

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.tasama.domain.model.AppTheme
import com.example.tasama.domain.repository.SettingsRepository
import com.example.tasama.presentation.main.AuthState
import com.example.tasama.presentation.main.MainViewModel
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModel()
    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate to avoid flashing
        val settings = runBlocking { settingsRepository.settings.first() }
        setNightMode(settings.theme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            // Wait for AuthState to be determined (either Authenticated or Unauthenticated)
            viewModel.authState
                .filterNot { it is AuthState.Loading }
                .first()

            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setNightMode(theme: AppTheme) {
        val mode = when (theme) {
            AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            AppTheme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val applicationMode = when (theme) {
                AppTheme.LIGHT -> UiModeManager.MODE_NIGHT_NO
                AppTheme.DARK -> UiModeManager.MODE_NIGHT_YES
                AppTheme.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }
            try {
                val method = uiModeManager.javaClass.getMethod("setApplicationNightMode", Int::class.javaPrimitiveType)
                method.invoke(uiModeManager, applicationMode)
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
}
