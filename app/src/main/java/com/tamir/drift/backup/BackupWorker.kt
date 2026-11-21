package com.tamir.drift.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamir.drift.di.AppGraph
import java.util.concurrent.TimeUnit

/**
 * The weekly automatic backup: writes a dated file into the folder the user picked and keeps the
 * newest five. A failure (folder gone, permission revoked) is recorded by [BackupManager] and shown
 * as a row on the home screen; the job itself always ends quietly and tries again next week.
 */
class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        if (!graph.settings.current().backupAutoEnabled) return Result.success()
        runCatching { graph.backup.backupToFolder() }
        return Result.success()
    }
}

object BackupScheduler {
    private const val WORK_NAME = "weekly_backup"
    private const val INTERVAL_DAYS = 7L

    /**
     * Keeps the weekly job alive. [restart] (the switch was just turned on, or another folder was
     * picked) starts a new period, whose first run — the first backup — happens right away;
     * otherwise an existing schedule is left as it is.
     */
    fun schedule(context: Context, restart: Boolean) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(INTERVAL_DAYS, TimeUnit.DAYS).build()
        val policy = if (restart) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
