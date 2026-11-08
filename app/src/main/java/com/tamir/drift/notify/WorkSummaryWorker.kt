package com.tamir.drift.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamir.drift.R
import com.tamir.drift.data.repo.toShiftSummaries
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.work.GoalCalculator
import com.tamir.drift.ui.format.Fmt
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * PRD "עבודה ויעד": every Saturday at 20:00 — "סיכום שבוע: הרווחת X מתוך Y · במסלול/לא".
 * Re-schedules itself for next Saturday after each run; the toggle lives in settings.
 */
class WorkSummaryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        try {
            if (graph.settings.current().workSummaryEnabled) notify(graph)
        } finally {
            WorkSummaryScheduler.schedule(applicationContext)
        }
        return Result.success()
    }

    private suspend fun notify(graph: AppGraph) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val settings = graph.settings.current()
        val shifts = graph.work.getShifts().toShiftSummaries()
        val week = GoalCalculator.weekStatus(shifts, today, settings.weeklyIncomeGoalAgorot)
        val jobs = graph.work.getJobs()
        val mainRate = jobs.firstOrNull { it.isActive }?.hourlyRateAgorot ?: jobs.firstOrNull()?.hourlyRateAgorot ?: 0L
        val month = GoalCalculator.monthStatus(
            shifts, graph.db.transactions().getAll().toSummaries(), YearMonth.from(today), today, zone,
            settings.monthlySavingGoalAgorot, mainRate,
        )
        val ctx = applicationContext
        val track = ctx.getString(if (month.onTrack) R.string.goal_on_track_short else R.string.goal_off_track_short)
        val text = ctx.getString(R.string.work_summary_text, Fmt.moneyAgorot(week.incomeAgorot), Fmt.moneyAgorot(week.goalAgorot), track)
        graph.notifier.notifyWorkSummary(ctx.getString(R.string.work_summary_title), text)
    }
}

object WorkSummaryScheduler {
    private const val WORK_NAME = "work_weekly_summary"
    private val TIME: LocalTime = LocalTime.of(20, 0)

    /** (Re)schedules the next Saturday 20:00 run. */
    fun schedule(context: Context) {
        val delay = delayUntilNext(LocalDateTime.now())
        val request = OneTimeWorkRequestBuilder<WorkSummaryWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun delayUntilNext(now: LocalDateTime): Duration {
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(TIME)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusWeeks(1)
        return Duration.between(now, next)
    }
}
