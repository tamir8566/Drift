package com.tamir.drift.domain.capture

/**
 * Decides when to tell the user that automatic capture seems to have stopped.
 *
 * Alert when the notification listener is disabled; or when it is enabled but nothing was
 * captured for [SILENT_DAYS]+ days although the [BASELINE_DAYS] days before the silence averaged
 * at least one capture every two days ([BASELINE_MIN_CAPTURES]). At most one alert every
 * [MIN_DAYS_BETWEEN_ALERTS] days.
 */
object CaptureWatchdog {
    const val SILENT_DAYS = 4
    const val BASELINE_DAYS = 14
    const val BASELINE_MIN_CAPTURES = 7
    const val MIN_DAYS_BETWEEN_ALERTS = 3
    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** [silentDays]: whole days since the last capture; null when nothing was ever captured. */
    data class Verdict(val alert: Boolean, val silentDays: Int?)

    fun daysSince(lastCaptureMillis: Long?, nowMillis: Long): Int? =
        lastCaptureMillis?.let { ((nowMillis - it) / DAY_MILLIS).toInt().coerceAtLeast(0) }

    /**
     * @param captureTimes epoch millis of the stored raw captures (any order; the newest 20 are enough).
     * @param lastAlertMillis when the previous alert was posted; 0 = never.
     */
    fun evaluate(
        listenerEnabled: Boolean,
        captureTimes: List<Long>,
        nowMillis: Long,
        lastAlertMillis: Long,
        enabled: Boolean = true,
    ): Verdict {
        val last = captureTimes.maxOrNull()
        val silent = daysSince(last, nowMillis)
        if (!enabled) return Verdict(false, silent)
        if (lastAlertMillis > 0L && nowMillis - lastAlertMillis < MIN_DAYS_BETWEEN_ALERTS * DAY_MILLIS) return Verdict(false, silent)
        if (!listenerEnabled) return Verdict(true, silent)
        if (last == null || silent == null || silent < SILENT_DAYS) return Verdict(false, silent)
        val baselineStart = last - BASELINE_DAYS * DAY_MILLIS
        val baseline = captureTimes.count { it > baselineStart && it <= last }
        return Verdict(baseline >= BASELINE_MIN_CAPTURES, silent)
    }
}
