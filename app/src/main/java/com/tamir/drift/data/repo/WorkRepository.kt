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
import com.tamir.drift.domain.work.RateSnapshot
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
     * the other rate column is stored as 0. [travelPerShiftAgorot] is the travel reimbursement
     * per shift (0 = none).
     */
    suspend fun addJob(
        name: String,
        payType: PayType,
        rateAgorot: Long,
        shabbatMultiplier: Double,
        travelPerShiftAgorot: Long = 0L,
    ): Long {
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
                travelPerShiftAgorot = travelPerShiftAgorot.coerceAtLeast(0L),
            ),
        )
    }

    suspend fun updateJob(job: JobEntity) = db.jobs().update(job.copy(name = job.name.trim()))

    suspend fun setJobActive(id: Long, active: Boolean) = db.jobs().setActive(id, active)

    /** "עבודה ראשית": marks this job and clears the mark on every other one (exactly one main). */
    suspend fun setMainJob(id: Long) = db.jobs().setMain(id)

    /** Removes the main mark when [id] carries it; the automatic choice (JobPicker) applies again. */
    suspend fun clearMainJob(id: Long) {
        if (db.jobs().getById(id)?.isMain == true) db.jobs().clearMain()
    }

    /**
     * Saves a shift. The amount (wage) is computed from the job unless [manualAmountAgorot] is given
     * (PRD: editable computed amount → manualAmount = true). [travelAgorot] is the travel
     * reimbursement applied to this shift (0 = none); it is stored apart from the wage and is not
     * affected by the Shabbat multiplier or by a manual wage. The job's rate and multiplier used
     * now are stored on the shift, so a later change of the job does not rewrite its pay.
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
        travelAgorot: Long = 0L,
    ): Long {
        val job = db.jobs().getById(jobId)
        val payType = job?.pay ?: PayType.HOURLY
        val rate = job.currentRate()
        val amount = manualAmountAgorot ?: ShiftCalculator.amount(payType, hours, rate, isShabbatRate)
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
                travelAgorot = travelAgorot.coerceAtLeast(0L),
                rateAgorotAtShift = job?.let { rate.rateAgorot },
                multiplierAtShift = job?.let { rate.multiplier },
            ),
        )
    }

    /**
     * Updates a shift. Its pay is computed with the rate stored on the shift as long as the job
     * and the hours are unchanged (so re-saving an old shift does not move it to the job's
     * current rate); a new job or new hours take the job's current rate, which is then stored.
     */
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
        /** Defaults to what the shift already has: editing keeps the stored travel. */
        travelAgorot: Long = existing.travelAgorot,
    ) {
        val job = db.jobs().getById(jobId)
        val payType = job?.pay ?: PayType.HOURLY
        val rate = ShiftCalculator.rateForEdit(
            current = job.currentRate(),
            stored = existing.storedRate(),
            sameJob = jobId == existing.jobId,
            sameHours = ShiftCalculator.sameHours(hours, existing.hours),
        )
        val amount = manualAmountAgorot ?: ShiftCalculator.amount(payType, hours, rate, isShabbatRate)
        // Keep a rate on the shift only when it is a real one (the job exists, or it was stored before).
        val keepRate = job != null || existing.rateAgorotAtShift != null
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
                travelAgorot = travelAgorot.coerceAtLeast(0L),
                rateAgorotAtShift = if (keepRate) rate.rateAgorot else null,
                multiplierAtShift = if (keepRate) rate.multiplier else null,
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
        const val SEED_RATE_AGOROT = 40_00L

        /** Palette only (PRD): lime, teal, orange and their softer variants. */
        val PALETTE: List<Long> = listOf(0xFFC5F36FL, 0xFF2EE6C5L, 0xFFFFB26BL, 0x99C5F36FL, 0x992EE6C5L, 0x99FFB26BL)
    }
}

fun JobEntity.toSummary(): JobSummary =
    JobSummary(id, name, hourlyRateAgorot, shabbatMultiplier, isActive, pay, dailyRateAgorot, travelPerShiftAgorot, isMain)

/** The job's rate right now: per day for a daily job, per hour otherwise. A missing job pays nothing. */
fun JobEntity?.currentRate(): RateSnapshot {
    val job = this ?: return RateSnapshot(0L, 1.5)
    return if (job.pay == PayType.DAILY) RateSnapshot(job.dailyRateAgorot, job.shabbatMultiplier)
    else RateSnapshot(job.hourlyRateAgorot, job.shabbatMultiplier)
}

/** The rate stored on the shift when it was saved; null for a shift without one. */
fun ShiftEntity.storedRate(): RateSnapshot? =
    rateAgorotAtShift?.let { RateSnapshot(it, multiplierAtShift ?: 1.5) }

fun List<JobEntity>.toJobSummaries(): List<JobSummary> = map { it.toSummary() }

fun ShiftEntity.toSummary(): ShiftSummary = ShiftSummary(id, jobId, date, hours, amountAgorot, isShabbatRate, travelAgorot)

fun List<ShiftEntity>.toShiftSummaries(): List<ShiftSummary> = map { it.toSummary() }
