package com.tamir.drift.domain.work

/**
 * Pure-Kotlin projections of the work entities (PRD "עבודות ומשמרות"). Amounts are agorot;
 * a shift's day is an epoch day (LocalDate.toEpochDay()) so it does not depend on a time zone.
 */

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
) {
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
    val amountAgorot: Long,
    val isShabbatRate: Boolean = false,
)
