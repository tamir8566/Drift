package com.tamir.drift.domain.work

import java.time.LocalDate

/**
 * Which job is meant when the app has to pick one by itself.
 *
 * [jobs] must be ordered oldest first (the DAO order), so "the first" is "the oldest".
 */
object JobPicker {
    const val RECENT_DAYS = 30L
    const val HABIT_DAYS = 120L

    /**
     * The main job ("עבודה ראשית"): the active job the user marked; otherwise the active job with
     * the most shifts in the last [RECENT_DAYS] days; otherwise the oldest active job. With no
     * active job at all the same rules run over every job.
     */
    fun mainJob(jobs: List<JobSummary>, shifts: List<ShiftSummary>, todayEpochDay: Long): JobSummary? {
        val pool = jobs.filter { it.isActive }.ifEmpty { jobs }
        pool.firstOrNull { it.isMain }?.let { return it }
        val from = todayEpochDay - RECENT_DAYS + 1
        val counts = shifts.filter { it.epochDay in from..todayEpochDay }.groupingBy { it.jobId }.eachCount()
        // maxByOrNull keeps the first maximum, so a tie goes to the older job.
        val busiest = pool.filter { (counts[it.id] ?: 0) > 0 }.maxByOrNull { counts[it.id] ?: 0 }
        return busiest ?: pool.firstOrNull()
    }

    /**
     * The job a new shift on [date] starts with: the active job most often worked on that weekday
     * (shifts of the [HABIT_DAYS] days up to [date]; a tie goes to the one worked most recently),
     * else the job of the last shift, else the main job.
     */
    fun defaultJobId(date: LocalDate, jobs: List<JobSummary>, shifts: List<ShiftSummary>): Long? {
        val active = jobs.filter { it.isActive }
        if (active.isEmpty()) return null
        val ids = active.map { it.id }.toSet()
        val day = date.toEpochDay()
        val mine = shifts.filter { it.jobId in ids && it.epochDay <= day }
        val sameWeekday = mine.filter { it.epochDay > day - HABIT_DAYS && LocalDate.ofEpochDay(it.epochDay).dayOfWeek == date.dayOfWeek }
        val habit = sameWeekday.groupBy { it.jobId }
            .maxWithOrNull(compareBy<Map.Entry<Long, List<ShiftSummary>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.epochDay } })
            ?.key
        if (habit != null) return habit
        val lastUsed = mine.maxWithOrNull(compareBy<ShiftSummary> { it.epochDay }.thenBy { it.id })?.jobId
        return lastUsed ?: mainJob(active, shifts, day)?.id
    }
}
