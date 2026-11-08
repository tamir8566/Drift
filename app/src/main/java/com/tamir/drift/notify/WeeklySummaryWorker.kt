package com.tamir.drift.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamir.drift.R
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.data.settings.Settings
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.insights.WeeklySummaryCalculator
import com.tamir.drift.ui.format.Fmt
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * PRD: weekly summary notification (default Sunday 09:00, configurable): spent this week,
 * biggest category, state vs budget. Re-schedules itself for the next week after each run.
 */
class WeeklySummaryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        val settings = graph.settings.current()
        try {
            if (settings.weeklySummaryEnabled) notify(graph, settings)
        } finally {
            WeeklySummaryScheduler.schedule(applicationContext, settings)
        }
        return Result.success()
    }

    private suspend fun notify(graph: AppGraph, settings: Settings) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val weekEnd = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = weekEnd - TimeUnit.DAYS.toMillis(7)
        val all = graph.db.transactions().getAll()
        val month = YearMonth.from(now)
        val summary = graph.monthSummary.build(all, graph.db.categories().getAll(), graph.db.budgets().getAll(), month)
        val weekly = WeeklySummaryCalculator.calculate(all.toSummaries(), weekStart, weekEnd, summary.spent, summary.totalBudget)
        val ctx = applicationContext
        val categoryName = weekly.topCategoryKey?.let { key -> graph.db.categories().getById(key)?.name }
        val budgetLine = if (summary.remaining >= 0) {
            ctx.getString(R.string.weekly_remaining, Fmt.money(summary.remaining), Fmt.percent(summary.percentUsed))
        } else {
            ctx.getString(R.string.weekly_over, Fmt.money(-summary.remaining))
        }
        val text = buildString {
            append(ctx.getString(R.string.weekly_spent, Fmt.money(weekly.spent), weekly.count))
            if (categoryName != null) {
                append(" · ")
                append(ctx.getString(R.string.weekly_top_category, categoryName, Fmt.money(weekly.topCategoryAmount)))
            }
            append(" · ")
            append(budgetLine)
        }
        graph.notifier.notifyWeekly(ctx.getString(R.string.weekly_title), text)
    }
}

object WeeklySummaryScheduler {
    private const val WORK_NAME = "weekly_summary"

    /** (Re)schedules the next run at the configured day/time. */
    fun schedule(context: Context, settings: Settings) {
        val delay = delayUntilNext(settings, LocalDateTime.now())
        val request = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun delayUntilNext(settings: Settings, now: LocalDateTime): Duration {
        val day = DayOfWeek.of(settings.weeklySummaryDay.coerceIn(1, 7))
        val time = LocalTime.of(settings.weeklySummaryHour.coerceIn(0, 23), settings.weeklySummaryMinute.coerceIn(0, 59))
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(time)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusWeeks(1)
        return Duration.between(now, next)
    }
}
