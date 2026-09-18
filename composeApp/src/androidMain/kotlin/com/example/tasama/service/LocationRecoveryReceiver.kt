package com.example.tasama.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.tasama.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocationRecoveryReceiver : BroadcastReceiver(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        println("LIVE_LOCATION_SERVICE: LocationRecoveryReceiver onReceive triggered with action: ${intent?.action}")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.settings.first()
                println("LIVE_LOCATION_SERVICE: LocationRecoveryReceiver - partnerMapEnabled: ${settings.partnerMapEnabled}")
                if (settings.partnerMapEnabled) {
                    // Restart service if it was killed
                    val serviceIntent = Intent(context, LocationService::class.java).apply {
                        action = LocationService.ACTION_START
                    }
                    println("LIVE_LOCATION_SERVICE: LocationRecoveryReceiver - Attempting to startForegroundService")
                    context.startForegroundService(serviceIntent)

                    // Reschedule the watchdog alarm to check again in 5 minutes
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val recoveryIntent = Intent(context, LocationRecoveryReceiver::class.java)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        999,
                        recoveryIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerAt = System.currentTimeMillis() + 60000 * 5 // 5 minutes
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                    println("LIVE_LOCATION_SERVICE: LocationRecoveryReceiver - Rescheduled 5-minute watchdog alarm")
                }
            } catch (e: Exception) {
                println("LIVE_LOCATION_SERVICE: LocationRecoveryReceiver - Error: ${e.message}")
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
