package com.tamir.drift.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureWatchdogTest {

    private val day = CaptureWatchdog.DAY_MILLIS
    private val now = 1_800_000_000_000L

    /** One capture every [everyDays] days, the newest [lastDaysAgo] days before now. */
    private fun captures(count: Int, lastDaysAgo: Int, everyDays: Double = 1.0): List<Long> =
        (0 until count).map { i -> now - lastDaysAgo * day - (i * everyDays * day).toLong() }

    @Test
    fun `listener disabled always alerts`() {
        val v = CaptureWatchdog.evaluate(listenerEnabled = false, captureTimes = captures(3, 2), nowMillis = now, lastAlertMillis = 0L)
        assertTrue(v.alert)
        assertEquals(2, v.silentDays)
        val never = CaptureWatchdog.evaluate(false, emptyList(), now, 0L)
        assertTrue(never.alert)
        assertNull(never.silentDays)
    }

    @Test
    fun `enabled and silent for four days after a busy fortnight alerts`() {
        val v = CaptureWatchdog.evaluate(true, captures(10, 4), now, 0L)
        assertTrue(v.alert)
        assertEquals(4, v.silentDays)
    }

    @Test
    fun `three silent days are not enough`() {
        assertFalse(CaptureWatchdog.evaluate(true, captures(10, 3), now, 0L).alert)
    }

    @Test
    fun `a quiet user is not alerted`() {
        // 6 captures in the fortnight before the silence: fewer than one every two days.
        assertFalse(CaptureWatchdog.evaluate(true, captures(6, 5, everyDays = 2.2), now, 0L).alert)
        // exactly one every two days → 7 in 14 days.
        assertTrue(CaptureWatchdog.evaluate(true, captures(7, 5, everyDays = 2.0), now, 0L).alert)
        // old captures outside the 14 days do not count.
        assertFalse(CaptureWatchdog.evaluate(true, captures(3, 5) + captures(10, 40), now, 0L).alert)
        assertFalse(CaptureWatchdog.evaluate(true, emptyList(), now, 0L).alert)
    }

    @Test
    fun `at most one alert every three days`() {
        assertFalse(CaptureWatchdog.evaluate(false, emptyList(), now, lastAlertMillis = now - 2 * day).alert)
        assertTrue(CaptureWatchdog.evaluate(false, emptyList(), now, lastAlertMillis = now - 3 * day).alert)
        assertFalse(CaptureWatchdog.evaluate(true, captures(10, 6), now, lastAlertMillis = now - day).alert)
    }

    @Test
    fun `switched off in settings never alerts`() {
        assertFalse(CaptureWatchdog.evaluate(false, emptyList(), now, 0L, enabled = false).alert)
    }

    @Test
    fun `the newest twenty rows are enough for the baseline`() {
        // 3 captures a day: the 20 kept rows cover only a week, still at least 7.
        assertTrue(CaptureWatchdog.evaluate(true, captures(20, 4, everyDays = 0.33), now, 0L).alert)
    }
}
