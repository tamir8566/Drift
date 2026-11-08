package com.tamir.drift.domain.work

import kotlin.math.roundToLong

/** PRD: amount = hours × rate × (shabbat ? multiplier : 1); the user may override it manually. */
object ShiftCalculator {

    const val MINUTES_PER_DAY = 24 * 60

    fun amount(hours: Double, hourlyRateAgorot: Long, isShabbatRate: Boolean, shabbatMultiplier: Double): Long {
        if (hours <= 0.0 || hourlyRateAgorot <= 0L) return 0L
        val factor = if (isShabbatRate) shabbatMultiplier else 1.0
        return (hours * hourlyRateAgorot * factor).roundToLong()
    }

    /** The stored amount: the manual override when the user typed one, else the computed value. */
    fun resolve(
        hours: Double,
        hourlyRateAgorot: Long,
        isShabbatRate: Boolean,
        shabbatMultiplier: Double,
        manualAmountAgorot: Long?,
    ): Long = manualAmountAgorot ?: amount(hours, hourlyRateAgorot, isShabbatRate, shabbatMultiplier)

    /**
     * Hours between two minutes-of-day. An end at or before the start means the shift crossed
     * midnight (22:00 → 02:00 = 4 hours). Quarter-hour precision is kept (fractional hours).
     */
    fun hoursBetween(startMinute: Int, endMinute: Int): Double {
        var diff = endMinute - startMinute
        if (diff <= 0) diff += MINUTES_PER_DAY
        return diff / 60.0
    }

    /** "8:30" / "08:30" / "8.30" → minutes from midnight; null when not a valid clock time. */
    fun parseClock(text: String): Int? {
        val parts = text.trim().split(':', '.', ' ').filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.size > 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else 0
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** Minutes from midnight → "08:30". */
    fun formatClock(minuteOfDay: Int): String {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val h = m / 60
        val min = m % 60
        return (if (h < 10) "0$h" else "$h") + ":" + (if (min < 10) "0$min" else "$min")
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
