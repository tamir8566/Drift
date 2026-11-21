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

/**
 * Local notifications: budget alerts (80% / 100%), the weekly summaries and quiet reminders.
 * Each one opens the screen it is about ([DeepLink]).
 */
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
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_reminders_desc) },
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Budget alerts open the budget screen. */
    fun notifyBudget(id: Int, title: String, text: String) = post(CHANNEL_BUDGET, id, title, text, DeepLink.BUDGET)

    /** The weekly spend summary opens "לאן הכסף הולך". */
    fun notifyWeekly(title: String, text: String) = post(CHANNEL_WEEKLY, ID_WEEKLY, title, text, DeepLink.WHERE)

    /** Saturday 20:00 work summary (same low-importance channel as the weekly summary); opens the goal screen. */
    fun notifyWorkSummary(title: String, text: String) = post(CHANNEL_WEEKLY, ID_WORK_WEEKLY, title, text, DeepLink.GOAL)

    /** A quiet reminder (capture watchdog, yearly tax coordination) that opens [target]. */
    fun notifyReminder(id: Int, title: String, text: String, target: String) = post(CHANNEL_REMINDERS, id, title, text, target)

    private fun post(channel: String, id: Int, title: String, text: String, target: String) {
        if (!canPost()) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(DeepLink.EXTRA, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Extras do not make two PendingIntents different; a request code per target does.
        val open = PendingIntent.getActivity(
            context, target.hashCode(), intent,
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
        const val CHANNEL_REMINDERS = "reminders"
        const val ID_WEEKLY = 1001
        const val ID_WORK_WEEKLY = 1002
        const val ID_CAPTURE_WATCHDOG = 1003
        const val ID_TAX_REMINDER = 1004
        const val ID_BUDGET_BASE = 2000
    }
}
