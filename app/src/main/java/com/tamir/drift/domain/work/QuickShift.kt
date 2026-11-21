package com.tamir.drift.domain.work

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * What a new shift of a job copies from that job's most recent shift, so a shift that is like the
 * last one needs no typing: the way it was entered (start / end, or a number of hours) and whether
 * it carried a travel reimbursement. The date, the Shabbat rate (by weekday) and the pay rate (the
 * job's current one) are never copied.
 */
data class ShiftTemplate(
    val jobId: Long,
    /** Minutes from midnight; both set when the last shift was entered as start / end. */
    val startMinute: Int?,
    val endMinute: Int?,
    /** 0 for a daily-paid job. */
    val hours: Double,
    /** The last shift carried a travel reimbursement. */
    val travelOn: Boolean,
    /** The travel amount of the last shift, agorot (used when the job itself defines none). */
    val lastTravelAgorot: Long,
) {
    val hasTimes: Boolean get() = startMinute != null && endMinute != null
}

/** A shift ready to be saved with one tap ("כמו בפעם הקודמת"). */
data class QuickShiftPlan(
    val job: JobSummary,
    val epochDay: Long,
    val startMinute: Int?,
    val endMinute: Int?,
    val hours: Double,
    val isShabbatRate: Boolean,
    /** The wage at the job's current rate, without travel. */
    val wageAgorot: Long,
    val travelAgorot: Long,
) {
    val hasTimes: Boolean get() = startMinute != null && endMinute != null

    /** The day total shown on the row and in the confirmation: wage + travel. */
    val totalAgorot: Long get() = wageAgorot + travelAgorot
}

object QuickShift {

    /** The most recent shift of the job: the latest day, and on one day the one saved last. */
    fun lastShift(jobId: Long, shifts: List<ShiftSummary>): ShiftSummary? =
        shifts.filter { it.jobId == jobId }.maxWithOrNull(compareBy<ShiftSummary> { it.epochDay }.thenBy { it.id })

    /** The template of [job] from its most recent shift; null when the job has no shift yet. */
    fun template(job: JobSummary, shifts: List<ShiftSummary>): ShiftTemplate? {
        val last = lastShift(job.id, shifts) ?: return null
        val start = last.startMinute
        val end = last.endMinute
        val timed = !job.isDaily && start != null && end != null
        val hours = when {
            job.isDaily -> 0.0
            start != null && end != null -> ShiftCalculator.hoursBetween(start, end)
            else -> last.hours
        }
        return ShiftTemplate(
            jobId = job.id,
            startMinute = if (timed) start else null,
            endMinute = if (timed) end else null,
            hours = hours,
            travelOn = last.travelAgorot > 0L,
            lastTravelAgorot = last.travelAgorot,
        )
    }

    /**
     * The travel a new shift from [template] carries: nothing when the last shift had none;
     * otherwise the job's current amount, or — for a job that defines none — the last shift's own.
     */
    fun travelFor(job: JobSummary, template: ShiftTemplate): Long = when {
        !template.travelOn -> 0L
        job.travelPerShiftAgorot > 0L -> job.travelPerShiftAgorot
        else -> template.lastTravelAgorot
    }

    /**
     * The one-tap shift for [date]: the job [JobPicker.defaultJobId] picks for that day, with the
     * hours (or start / end) and travel of its last shift, the Shabbat rate by the weekday and the
     * wage at the job's current rate.
     *
     * Null when there is nothing to copy or nothing to add: no active job, the job has no earlier
     * shift, an hourly job whose last shift has no hours, or a shift of that job already on [date].
     */
    fun plan(date: LocalDate, jobs: List<JobSummary>, shifts: List<ShiftSummary>): QuickShiftPlan? {
        val jobId = JobPicker.defaultJobId(date, jobs, shifts) ?: return null
        val job = jobs.firstOrNull { it.id == jobId } ?: return null
        val day = date.toEpochDay()
        if (shifts.any { it.jobId == jobId && it.epochDay == day }) return null
        val template = template(job, shifts) ?: return null
        if (!job.isDaily && template.hours <= 0.0) return null
        val shabbat = date.dayOfWeek == DayOfWeek.SATURDAY
        return QuickShiftPlan(
            job = job,
            epochDay = day,
            startMinute = template.startMinute,
            endMinute = template.endMinute,
            hours = template.hours,
            isShabbatRate = shabbat,
            wageAgorot = ShiftCalculator.amount(job.payType, template.hours, job.hourlyRateAgorot, job.dailyRateAgorot, shabbat, job.shabbatMultiplier),
            travelAgorot = travelFor(job, template),
        )
    }
}
