package com.tamir.drift.domain.work

/**
 * Pure-Kotlin projections of the work entities (PRD "עבודות ומשמרות"). Amounts are agorot;
 * a shift's day is an epoch day (LocalDate.toEpochDay()) so it does not depend on a time zone.
 */

data class JobSummary(
    val id: Long,
    val name: String,
    val hourlyRateAgorot: Long,
    val shabbatMultiplier: Double = 1.5,
    val isActive: Boolean = true,
)

data class ShiftSummary(
    val id: Long,
    val jobId: Long,
    val epochDay: Long,
    val hours: Double,
    val amountAgorot: Long,
    val isShabbatRate: Boolean = false,
)
