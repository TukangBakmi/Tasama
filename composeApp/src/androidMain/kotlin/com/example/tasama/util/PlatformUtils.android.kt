package com.example.tasama.util

import android.content.Intent
import android.os.Build
import coil3.request.ImageRequest
import coil3.request.allowHardware

actual fun ImageRequest.Builder.disableHardwareBitmaps(): ImageRequest.Builder = 
    this.allowHardware(false)

actual fun isXiaomiDevice(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return manufacturer == "xiaomi" || manufacturer == "redmi" || manufacturer == "poco"
}

private var appContext: android.content.Context? = null

fun initPlatformUtils(context: android.content.Context) {
    appContext = context.applicationContext
}

actual fun openXiaomiSettings() {
    val context = appContext ?: return
    try {
        val intent = Intent().apply {
            component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent().apply {
                action = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
