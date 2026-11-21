package com.tamir.drift.domain.work

/**
 * Pure-Kotlin projections of the work entities (PRD "עבודות ומשמרות"). Amounts are agorot;
 * a shift's day is an epoch day (LocalDate.toEpochDay()) so it does not depend on a time zone.
 */

/**
 * The legal minimum hourly wage in Israel, in agorot. The one place this number lives — update it
 * (and the date) when the wage changes; the job dialog warns below it.
 */
const val MIN_HOURLY_WAGE_AGOROT = 35_40L // from 1.4.2026

/** The month [MIN_HOURLY_WAGE_AGOROT] is valid from (shown next to the warning). */
val MIN_HOURLY_WAGE_SINCE: java.time.YearMonth = java.time.YearMonth.of(2026, 4)

/** How a job pays: per worked hour (shifts have hours × rate) or a fixed amount per worked day. */
enum class PayType {
    HOURLY,
    DAILY;

    companion object {
        /** Lenient parse of the stored name; unknown / null → [HOURLY]. */
        fun from(name: String?): PayType = entries.firstOrNull { it.name == name } ?: HOURLY
    }
}

data class JobSummary(
    val id: Long,
    val name: String,
    val hourlyRateAgorot: Long,
    val shabbatMultiplier: Double = 1.5,
    val isActive: Boolean = true,
    val payType: PayType = PayType.HOURLY,
    /** Agorot per worked day; 0 for hourly jobs. */
    val dailyRateAgorot: Long = 0L,
    /** Travel reimbursement ("החזר נסיעות") per shift, agorot; 0 = the job pays none. */
    val travelPerShiftAgorot: Long = 0L,
    /** The user marked this job as the main one ("עבודה ראשית"); at most one job has it. */
    val isMain: Boolean = false,
) {
    /** True for an hourly job whose rate is under the legal minimum ([MIN_HOURLY_WAGE_AGOROT]). */
    val isBelowMinimumWage: Boolean get() = !isDaily && hourlyRateAgorot in 1 until MIN_HOURLY_WAGE_AGOROT
    val isDaily: Boolean get() = payType == PayType.DAILY

    /** The rate that converts money into work units for this job (per hour or per day). */
    val unitRateAgorot: Long get() = if (isDaily) dailyRateAgorot else hourlyRateAgorot
}

data class ShiftSummary(
    val id: Long,
    val jobId: Long,
    val epochDay: Long,
    /** 0 for a day of a daily-paid job. */
    val hours: Double,
    /** The wage of the shift (computed or typed manually), without travel. */
    val amountAgorot: Long,
    val isShabbatRate: Boolean = false,
    /** Travel reimbursement applied to this shift, agorot; kept apart from the wage. */
    val travelAgorot: Long = 0L,
    /** Minutes from midnight when the shift was entered as start / end; null when entered as hours. */
    val startMinute: Int? = null,
    val endMinute: Int? = null,
) {
    /** The shift's income: wage + travel reimbursement. Every income sum uses this. */
    val totalAgorot: Long get() = amountAgorot + travelAgorot
}
