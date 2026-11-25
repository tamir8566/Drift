package com.tamir.drift.capture

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Whether automatic capture can work right now (the two permissions it depends on). */
object CaptureStatus {

    /** True when the user gave Drift notification access (Google Pay capture). */
    fun listenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** True when Drift may receive SMS (max capture). */
    fun smsGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** The system screen where notification access is switched on. */
    fun listenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * True when the system does not optimise Drift's battery use. With the optimisation on, some
     * phones put the app to sleep after a few days without opening it, and capture stops silently.
     */
    fun batteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true)
    }

    /**
     * Opens the system's request to stop optimising Drift's battery use. Where that dialog does
     * not exist: the system list of all apps, and last the app's own system page.
     */
    fun requestBatteryExemption(context: Context) {
        val app = Uri.parse("package:${context.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app),
        )
        for (intent in candidates) {
            val started = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (started) return
        }
    }

    /**
     * Asks the system to bind the notification listener again. After an app update or a crash the
     * system sometimes leaves an enabled listener unbound, and capture stops silently; calling
     * this on every start reconnects it. Does nothing without notification access.
     */
    fun requestRebind(context: Context) {
        if (!listenerEnabled(context)) return
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(context, DriftNotificationListener::class.java))
        }
    }
}
