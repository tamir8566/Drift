package com.tamir.drift.domain.time

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.work.ShiftSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class TrackingWindowTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val start = LocalDate.of(2026, 12, 1)
    private val window = TrackingWindow(start.toEpochDay())

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun tx(id: Long, ts: Long) =
        TxnSummary(id, 10.0, 10.0, "x", CategoryKeys.GROCERIES, ts, TransactionSource.GPAY)

    private fun shift(id: Long, date: LocalDate) = ShiftSummary(id, 1L, date.toEpochDay(), 8.0, 300_00L)

    @Test
    fun `default start is 1 december 2026`() {
        assertEquals(LocalDate.of(2026, 12, 1), TrackingWindow.DEFAULT_START)
        assertEquals(LocalDate.of(2026, 12, 1).toEpochDay(), TrackingWindow.DEFAULT_START_EPOCH_DAY)
    }

    @Test
    fun `epoch day on or after the start is included, before it is not`() {
        assertFalse(window.includes(LocalDate.of(2026, 11, 30).toEpochDay()))
        assertTrue(window.includes(start.toEpochDay()))
        assertTrue(window.includes(LocalDate.of(2027, 1, 15).toEpochDay()))
    }

    @Test
    fun `timestamp is judged by its local day in the zone`() {
        // 30.11 23:30 Israel time is still before the start even though it is already 1.12 in UTC+2 terms elsewhere.
        assertFalse(window.includes(at(2026, 11, 30, 23, 30), zone))
        // First minute of 1.12 local time counts.
        assertTrue(window.includes(at(2026, 12, 1, 0, 1), zone))
        // 1.12 00:30 Israel time is 30.11 22:30 UTC — must still count (local day decides, not UTC).
        assertTrue(window.includes(at(2026, 12, 1, 0, 30), zone))
    }

    @Test
    fun `null start includes everything`() {
        assertTrue(TrackingWindow.ALL.includes(0L))
        assertTrue(TrackingWindow.ALL.includes(at(2000, 1, 1), zone))
        assertTrue(TrackingWindow.ALL.includesMonth(YearMonth.of(1999, 1)))
    }

    @Test
    fun `month before the start is excluded, the start month and later are included`() {
        assertFalse(window.includesMonth(YearMonth.of(2026, 11)))
        assertTrue(window.includesMonth(YearMonth.of(2026, 12)))
        assertTrue(window.includesMonth(YearMonth.of(2027, 3)))
        // A start in the middle of a month still includes that month.
        val midMonth = TrackingWindow(LocalDate.of(2026, 12, 15).toEpochDay())
        assertTrue(midMonth.includesMonth(YearMonth.of(2026, 12)))
        assertFalse(midMonth.includesMonth(YearMonth.of(2026, 11)))
    }

    @Test
    fun `transactions sinceStart keeps only those from the start day on`() {
        val list = listOf(
            tx(1, at(2026, 11, 15)),
            tx(2, at(2026, 11, 30, 23, 59)),
            tx(3, at(2026, 12, 1, 0, 0)),
            tx(4, at(2026, 12, 20)),
        )
        assertEquals(listOf(3L, 4L), list.sinceStart(window, zone).map { it.id })
        assertEquals(list, list.sinceStart(TrackingWindow.ALL, zone))
    }

    @Test
    fun `shifts sinceStart keeps only days from the start on`() {
        val list = listOf(
            shift(1, LocalDate.of(2026, 11, 29)),
            shift(2, LocalDate.of(2026, 11, 30)),
            shift(3, start),
            shift(4, LocalDate.of(2026, 12, 2)),
        )
        assertEquals(listOf(3L, 4L), list.sinceStart(window).map { it.id })
        assertEquals(list, list.sinceStart(TrackingWindow.ALL))
    }

    @Test
    fun `a future start leaves nothing to count`() {
        val future = TrackingWindow(LocalDate.of(2030, 1, 1).toEpochDay())
        val txs = listOf(tx(1, at(2026, 12, 5)), tx(2, at(2027, 6, 1)))
        val shifts = listOf(shift(1, LocalDate.of(2027, 1, 1)))
        assertTrue(txs.sinceStart(future, zone).isEmpty())
        assertTrue(shifts.sinceStart(future).isEmpty())
    }

    @Test
    fun `startDate round-trips the epoch day`() {
        assertEquals(start, window.startDate)
        assertEquals(null, TrackingWindow.ALL.startDate)
    }

    @Test
    fun `until the tracking start the trial window is used, from that day on the count is clean`() {
        val firstLaunch = LocalDate.of(2026, 11, 8).toEpochDay()
        val trackingStart = start.toEpochDay()
        // Any day of the trial month: everything since the first launch counts.
        assertEquals(TrackingWindow(firstLaunch), TrackingWindow.current(LocalDate.of(2026, 11, 25).toEpochDay(), trackingStart, firstLaunch))
        assertEquals(TrackingWindow(firstLaunch), TrackingWindow.current(LocalDate.of(2026, 11, 30).toEpochDay(), trackingStart, firstLaunch))
        // From 1 December on, only records from that day count — also months later.
        val onTheDay = TrackingWindow.current(trackingStart, trackingStart, firstLaunch)
        assertEquals(TrackingWindow(trackingStart), onTheDay)
        assertFalse(onTheDay.includes(LocalDate.of(2026, 11, 30).toEpochDay()))
        assertTrue(onTheDay.includes(trackingStart))
        assertEquals(TrackingWindow(trackingStart), TrackingWindow.current(LocalDate.of(2027, 3, 1).toEpochDay(), trackingStart, firstLaunch))
    }
}
