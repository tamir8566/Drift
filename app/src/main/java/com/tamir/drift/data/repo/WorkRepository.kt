package com.tamir.drift.data.repo

import android.content.Context
import com.tamir.drift.R
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.db.JobEntity
import com.tamir.drift.data.db.PlannedDayEntity
import com.tamir.drift.data.db.ShiftEntity
import com.tamir.drift.data.db.pay
import com.tamir.drift.domain.work.JobSummary
import com.tamir.drift.domain.work.PayType
import com.tamir.drift.domain.work.ShiftCalculator
import com.tamir.drift.domain.work.ShiftSummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Jobs, shifts and planned days (PRD "עבודות ומשמרות"). */
class WorkRepository(private val context: Context, private val db: DriftDatabase) {

    val jobs: Flow<List<JobEntity>> = db.jobs().observeAll()
    val shifts: Flow<List<ShiftEntity>> = db.shifts().observeAll()
    val plannedDays: Flow<List<PlannedDayEntity>> = db.plannedDays().observeAll()

    /** PRD: one example job is seeded on first run ("העבודה שלי", 40 ₪ per hour). */
    suspend fun ensureSeeded() {
        if (db.jobs().count() == 0) {
            db.jobs().insert(
                JobEntity(
                    name = context.getString(R.string.job_seed_name),
                    hourlyRateAgorot = SEED_RATE_AGOROT,
                    shabbatMultiplier = 1.5,
                    color = PALETTE.first(),
                ),
            )
        }
    }

    suspend fun getJobs(): List<JobEntity> = db.jobs().getAll()
    suspend fun getJob(id: Long): JobEntity? = db.jobs().getById(id)
    suspend fun getShifts(): List<ShiftEntity> = db.shifts().getAll()
    suspend fun getShift(id: Long): ShiftEntity? = db.shifts().getById(id)

    /**
     * Adds a job. [rateAgorot] is per hour for [PayType.HOURLY] and per day for [PayType.DAILY];
     * the other rate column is stored as 0.
     */
    suspend fun addJob(name: String, payType: PayType, rateAgorot: Long, shabbatMultiplier: Double): Long {
        val used = db.jobs().getAll().map { it.color }
        val color = PALETTE.firstOrNull { it !in used } ?: PALETTE[used.size % PALETTE.size]
        return db.jobs().insert(
            JobEntity(
                name = name.trim(),
                hourlyRateAgorot = if (payType == PayType.HOURLY) rateAgorot else 0L,
                shabbatMultiplier = shabbatMultiplier,
                color = color,
                payType = payType.name,
                dailyRateAgorot = if (payType == PayType.DAILY) rateAgorot else 0L,
            ),
        )
    }

    suspend fun updateJob(job: JobEntity) = db.jobs().update(job.copy(name = job.name.trim()))

    suspend fun setJobActive(id: Long, active: Boolean) = db.jobs().setActive(id, active)

    /**
     * Saves a shift. The amount is computed from the job unless [manualAmountAgorot] is given
     * (PRD: editable computed amount → manualAmount = true).
     */
    suspend fun addShift(
        jobId: Long,
        date: LocalDate,
        startTime: Int?,
        endTime: Int?,
        hours: Double,
        isShabbatRate: Boolean,
        manualAmountAgorot: Long?,
        note: String?,
    ): Long {
        val job = db.jobs().getById(jobId)
        val amount = ShiftCalculator.resolve(
            job?.pay ?: PayType.HOURLY, hours, job?.hourlyRateAgorot ?: 0L, job?.dailyRateAgorot ?: 0L,
            isShabbatRate, job?.shabbatMultiplier ?: 1.5, manualAmountAgorot,
        )
        return db.shifts().insert(
            ShiftEntity(
                jobId = jobId,
                date = date.toEpochDay(),
                startTime = startTime,
                endTime = endTime,
                hours = hours,
                isShabbatRate = isShabbatRate,
                amountAgorot = amount,
                manualAmount = manualAmountAgorot != null,
                note = note?.takeIf { it.isNotBlank() },
            ),
        )
    }

    suspend fun updateShift(
        existing: ShiftEntity,
        jobId: Long,
        date: LocalDate,
        startTime: Int?,
        endTime: Int?,
        hours: Double,
        isShabbatRate: Boolean,
        manualAmountAgorot: Long?,
        note: String?,
    ) {
        val job = db.jobs().getById(jobId)
        val amount = ShiftCalculator.resolve(
            job?.pay ?: PayType.HOURLY, hours, job?.hourlyRateAgorot ?: 0L, job?.dailyRateAgorot ?: 0L,
            isShabbatRate, job?.shabbatMultiplier ?: 1.5, manualAmountAgorot,
        )
        db.shifts().update(
            existing.copy(
                jobId = jobId,
                date = date.toEpochDay(),
                startTime = startTime,
                endTime = endTime,
                hours = hours,
                isShabbatRate = isShabbatRate,
                amountAgorot = amount,
                manualAmount = manualAmountAgorot != null,
                note = note?.takeIf { it.isNotBlank() },
            ),
        )
    }

    suspend fun deleteShift(id: Long) = db.shifts().deleteById(id)

    /** PRD calendar: tap a day → mark / unmark it as planned. */
    suspend fun togglePlanned(date: LocalDate, jobId: Long? = null) {
        val day = date.toEpochDay()
        if (db.plannedDays().countAt(day) > 0) db.plannedDays().delete(day)
        else db.plannedDays().upsert(PlannedDayEntity(date = day, jobId = jobId))
    }

    companion object {
        const val SEED_RATE_AGOROT = 38_00L

        /** Palette only (PRD): lime, teal, orange and their softer variants. */
        val PALETTE: List<Long> = listOf(0xFFC5F36FL, 0xFF2EE6C5L, 0xFFFFB26BL, 0x99C5F36FL, 0x992EE6C5L, 0x99FFB26BL)
    }
}

fun JobEntity.toSummary(): JobSummary = JobSummary(id, name, hourlyRateAgorot, shabbatMultiplier, isActive, pay, dailyRateAgorot)

fun List<JobEntity>.toJobSummaries(): List<JobSummary> = map { it.toSummary() }

fun ShiftEntity.toSummary(): ShiftSummary = ShiftSummary(id, jobId, date, hours, amountAgorot, isShabbatRate)

fun List<ShiftEntity>.toShiftSummaries(): List<ShiftSummary> = map { it.toSummary() }
