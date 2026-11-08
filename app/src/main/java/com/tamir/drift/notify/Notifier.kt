package com.tamir.drift.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tamir.drift.MainActivity
import com.tamir.drift.R

/** Local notifications: budget alerts (80% / 100%) and the weekly summary. */
class Notifier(private val context: Context) {

    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGET, context.getString(R.string.channel_budget), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_budget_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WEEKLY, context.getString(R.string.channel_weekly), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_weekly_desc) },
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notifyBudget(id: Int, title: String, text: String) = post(CHANNEL_BUDGET, id, title, text)

    fun notifyWeekly(title: String, text: String) = post(CHANNEL_WEEKLY, ID_WEEKLY, title, text)

    /** Saturday 20:00 work summary (same low-importance channel as the weekly summary). */
    fun notifyWorkSummary(title: String, text: String) = post(CHANNEL_WEEKLY, ID_WORK_WEEKLY, title, text)

    private fun post(channel: String, id: Int, title: String, text: String) {
        if (!canPost()) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    companion object {
        const val CHANNEL_BUDGET = "budget_alerts"
        const val CHANNEL_WEEKLY = "weekly_summary"
        const val ID_WEEKLY = 1001
        const val ID_WORK_WEEKLY = 1002
        const val ID_BUDGET_BASE = 2000
    }
}
