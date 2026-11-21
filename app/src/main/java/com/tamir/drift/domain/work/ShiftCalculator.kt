package com.tamir.drift.domain.work

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The rate a shift was paid at: agorot per hour (hourly job) or per day (daily job), and the
 * Shabbat multiplier. Stored on the shift so a later change of the job's rate does not rewrite
 * the pay of shifts already worked.
 */
data class RateSnapshot(val rateAgorot: Long, val multiplier: Double)

/**
 * PRD: amount = hours × rate × (shabbat ? multiplier : 1); the user may override it manually.
 * A job may also pay a flat travel reimbursement per shift, added on top of the wage ([dayTotal]).
 */
object ShiftCalculator {

    const val MINUTES_PER_DAY = 24 * 60

    fun amount(hours: Double, hourlyRateAgorot: Long, isShabbatRate: Boolean, shabbatMultiplier: Double): Long {
        if (hours <= 0.0 || hourlyRateAgorot <= 0L) return 0L
        val factor = if (isShabbatRate) shabbatMultiplier else 1.0
        return (hours * hourlyRateAgorot * factor).roundToLong()
    }

    /** A daily-paid job: amount = daily rate × (shabbat ? multiplier : 1); no hours involved. */
    fun dailyAmount(dailyRateAgorot: Long, isShabbatRate: Boolean, shabbatMultiplier: Double): Long {
        if (dailyRateAgorot <= 0L) return 0L
        val factor = if (isShabbatRate) shabbatMultiplier else 1.0
        return (dailyRateAgorot * factor).roundToLong()
    }

    /** The computed amount for either pay type. */
    fun amount(
        payType: PayType,
        hours: Double,
        hourlyRateAgorot: Long,
        dailyRateAgorot: Long,
        isShabbatRate: Boolean,
        shabbatMultiplier: Double,
    ): Long = when (payType) {
        PayType.HOURLY -> amount(hours, hourlyRateAgorot, isShabbatRate, shabbatMultiplier)
        PayType.DAILY -> dailyAmount(dailyRateAgorot, isShabbatRate, shabbatMultiplier)
    }

    /**
     * The rate to pay an edited shift at: the rate stored on the shift, as long as the user did
     * not change its job or its hours; otherwise (or for a shift from before rates were stored)
     * the job's current rate.
     */
    fun rateForEdit(current: RateSnapshot, stored: RateSnapshot?, sameJob: Boolean, sameHours: Boolean): RateSnapshot =
        if (stored != null && sameJob && sameHours) stored else current

    fun sameHours(a: Double, b: Double): Boolean = abs(a - b) < 1e-6

    /** The computed amount for either pay type from one [RateSnapshot] (per hour or per day by [payType]). */
    fun amount(payType: PayType, hours: Double, rate: RateSnapshot, isShabbatRate: Boolean): Long = when (payType) {
        PayType.HOURLY -> amount(hours, rate.rateAgorot, isShabbatRate, rate.multiplier)
        PayType.DAILY -> dailyAmount(rate.rateAgorot, isShabbatRate, rate.multiplier)
    }

    /** The stored amount of an hourly shift: the manual override when the user typed one, else the computed value. */
    fun resolve(
        hours: Double,
        hourlyRateAgorot: Long,
        isShabbatRate: Boolean,
        shabbatMultiplier: Double,
        manualAmountAgorot: Long?,
    ): Long = manualAmountAgorot ?: amount(hours, hourlyRateAgorot, isShabbatRate, shabbatMultiplier)

    /** The stored amount for either pay type: the manual override when given, else the computed value. */
    fun resolve(
        payType: PayType,
        hours: Double,
        hourlyRateAgorot: Long,
        dailyRateAgorot: Long,
        isShabbatRate: Boolean,
        shabbatMultiplier: Double,
        manualAmountAgorot: Long?,
    ): Long = manualAmountAgorot ?: amount(payType, hours, hourlyRateAgorot, dailyRateAgorot, isShabbatRate, shabbatMultiplier)

    /**
     * Travel reimbursement ("החזר נסיעות") applied to one shift: the per-shift amount when the
     * toggle is on, otherwise 0. A flat amount — never multiplied by the Shabbat multiplier.
     */
    fun travel(travelPerShiftAgorot: Long, travelEnabled: Boolean): Long =
        if (travelEnabled && travelPerShiftAgorot > 0L) travelPerShiftAgorot else 0L

    /**
     * Day total = wage + travel. The wage is the manual override when given, else the computed
     * amount (with the Shabbat multiplier); travel is added as is and is not touched by either.
     */
    fun dayTotal(
        payType: PayType,
        hours: Double,
        hourlyRateAgorot: Long,
        dailyRateAgorot: Long,
        isShabbatRate: Boolean,
        shabbatMultiplier: Double,
        manualAmountAgorot: Long?,
        travelPerShiftAgorot: Long,
        travelEnabled: Boolean,
    ): Long = resolve(payType, hours, hourlyRateAgorot, dailyRateAgorot, isShabbatRate, shabbatMultiplier, manualAmountAgorot) +
        travel(travelPerShiftAgorot, travelEnabled)

    /**
     * Hours between two minutes-of-day. An end at or before the start means the shift crossed
     * midnight (22:00 → 02:00 = 4 hours). Quarter-hour precision is kept (fractional hours).
     */
    fun hoursBetween(startMinute: Int, endMinute: Int): Double {
        var diff = endMinute - startMinute
        if (diff <= 0) diff += MINUTES_PER_DAY
        return diff / 60.0
    }

    /**
     * A typed clock time → minutes from midnight; null when it is not a valid one.
     * With a separator: "8:30" / "08:30" / "17.30" / "17 30". Digits only: one or two digits are a
     * whole hour ("9" → 09:00, "17" → 17:00); three or four end with the two minute digits
     * ("930" → 09:30, "1730" → 17:30).
     */
    fun parseClock(text: String): Int? {
        val parts = text.trim().split(':', '.', ' ').filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.size > 2) return null
        if (parts.any { part -> part.any { c -> c !in '0'..'9' } }) return null
        val h: Int
        val m: Int
        if (parts.size == 1 && parts[0].length in 3..4) {
            val digits = parts[0]
            h = digits.dropLast(2).toIntOrNull() ?: return null
            m = digits.takeLast(2).toIntOrNull() ?: return null
        } else {
            h = parts[0].toIntOrNull() ?: return null
            m = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else 0
        }
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /**
     * The typed time in its one stored / shown form "HH:mm" ("1730", "17.30", "930", "9" →
     * "17:30", "17:30", "09:30", "09:00"); null when it is not a valid clock time. Time fields
     * apply it when they lose focus and before saving.
     */
    fun normalizeClock(text: String): String? = parseClock(text)?.let { formatClock(it) }

    /** Minutes from midnight → "08:30". */
    fun formatClock(minuteOfDay: Int): String {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val h = m / 60
        val min = m % 60
        return (if (h < 10) "0$h" else "$h") + ":" + (if (min < 10) "0$min" else "$min")
    }

    /** Compact form for stat tiles: "23" / "23.1" — at most one decimal, no trailing zero. */
    fun formatHoursShort(hours: Double): String {
        val rounded = (hours * 10).roundToLong()
        val whole = rounded / 10
        val frac = rounded % 10
        return if (frac == 0L) whole.toString() else "$whole.$frac"
    }

    /** "6" for whole hours, "6.5" otherwise (at most two decimals, no trailing zeros). */
    fun formatHours(hours: Double): String {
        val rounded = (hours * 100).roundToLong()
        val whole = rounded / 100
        val frac = rounded % 100
        return when {
            frac == 0L -> whole.toString()
            frac % 10 == 0L -> "$whole.${frac / 10}"
            else -> "$whole." + (if (frac < 10) "0$frac" else "$frac")
        }
    }
}
