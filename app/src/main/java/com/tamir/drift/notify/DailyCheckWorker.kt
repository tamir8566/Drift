package com.tamir.drift.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamir.drift.R
import com.tamir.drift.capture.CaptureProcessor
import com.tamir.drift.capture.CaptureStatus
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.capture.CaptureWatchdog
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Once a day, two quiet checks:
 *
 *  1. Capture watchdog — one low-importance notification "לא נקלטו הוצאות כבר N ימים — כדאי לבדוק
 *     הרשאות" when notification access is off, or when it is on but nothing was captured for 4+
 *     days after a fortnight that averaged a capture every two days. At most once every 3 days;
 *     the toggle is in settings.
 *  2. Two employers — once a year, from 5 January, "שנה חדשה — צריך לחדש תיאום מס" when two or
 *     more jobs are active and this year's coordination is not ticked yet.
 */
class DailyCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        runCatching { checkCapture(graph) }
        runCatching { checkTaxReminder(graph) }
        return Result.success()
    }

    private suspend fun checkCapture(graph: AppGraph) {
        val ctx = applicationContext
        val settings = graph.settings.current()
        val now = System.currentTimeMillis()
        val verdict = CaptureWatchdog.evaluate(
            // A source the user switched off (an empty list) needs no permission.
            listenerEnabled = settings.notificationPackages.isEmpty() || CaptureStatus.listenerEnabled(ctx),
            captureTimes = graph.db.rawCaptures().latestTimes(CaptureProcessor.RAW_LOG_SIZE),
            nowMillis = now,
            lastAlertMillis = settings.watchdogLastAlertMillis,
            enabled = settings.captureWatchdogEnabled,
            smsBlocked = settings.smsSenders.isNotEmpty() && !CaptureStatus.smsGranted(ctx),
        )
        if (!verdict.alert) return
        // Without permission to notify nothing would be shown: do not record an alert that was
        // never seen, so it is posted as soon as notifications are allowed.
        if (!graph.notifier.canPost()) return
        // Recorded before posting, so a crash never repeats the notification.
        graph.settings.setWatchdogLastAlert(now)
        // Nothing captured ever: count from the day the app was first used.
        val days = verdict.silentDays ?: (LocalDate.now().toEpochDay() - settings.firstLaunchEpochDay).toInt().coerceAtLeast(0)
        val text = when {
            days >= 2 -> ctx.getString(R.string.watchdog_text_days, days)
            days == 1 -> ctx.getString(R.string.watchdog_text_one_day)
            else -> ctx.getString(R.string.watchdog_text_off)
        }
        graph.notifier.notifyReminder(Notifier.ID_CAPTURE_WATCHDOG, ctx.getString(R.string.watchdog_title), text, DeepLink.SETTINGS)
    }

    private suspend fun checkTaxReminder(graph: AppGraph) {
        val today = LocalDate.now()
        if (today.monthValue != 1 || today.dayOfMonth < TAX_REMINDER_DAY) return
        val settings = graph.settings.current()
        if (settings.taxReminderYear == today.year || settings.taxCoordinationYear == today.year) return
        if (graph.work.getJobs().count { it.isActive } < 2) return
        // Not recorded while notifications are blocked: the reminder waits until it can be shown.
        if (!graph.notifier.canPost()) return
        graph.settings.setTaxReminderYear(today.year)
        val ctx = applicationContext
        graph.notifier.notifyReminder(
            Notifier.ID_TAX_REMINDER, ctx.getString(R.string.two_employers_title), ctx.getString(R.string.two_employers_new_year), DeepLink.WORK,
        )
    }

    private companion object {
        const val TAX_REMINDER_DAY = 5
    }
}

object DailyCheckScheduler {
    private const val WORK_NAME = "daily_check"

    /** Keeps one daily periodic run alive (an existing schedule is left as it is). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
