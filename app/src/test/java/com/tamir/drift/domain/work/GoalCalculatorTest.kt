package com.tamir.drift.domain.work

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class GoalCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val nov = YearMonth.of(2026, 11)
    private val rate = 38_00L
    private var nextId = 1L

    private fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun shift(date: LocalDate, hours: Double, shabbat: Boolean = false, jobId: Long = 1L): ShiftSummary =
        ShiftSummary(nextId++, jobId, date.toEpochDay(), hours, ShiftCalculator.amount(hours, rate, shabbat, 1.5), shabbat)

    private fun tx(amount: Double, ts: Long, mine: Double = amount) =
        TxnSummary(nextId++, amount, mine, "x", CategoryKeys.GROCERIES, ts, TransactionSource.GPAY, isShared = mine != amount)

    // ---- month ----

    @Test
    fun `saved is shift income minus my share of expenses`() {
        val shifts = listOf(shift(day(2026, 11, 2), 8.0), shift(day(2026, 11, 3), 8.0), shift(day(2026, 10, 30), 8.0))
        val txs = listOf(tx(200.0, at(2026, 11, 5)), tx(300.0, at(2026, 11, 6), mine = 100.0), tx(999.0, at(2026, 10, 20)))
        val s = GoalCalculator.monthStatus(shifts, txs, nov, day(2026, 11, 8), zone, 2_000_00L, rate)
        assertEquals(608_00L, s.earnedAgorot)
        assertEquals(300_00L, s.spentAgorot)
        assertEquals(308_00L, s.savedAgorot)
        assertEquals(15.4, s.percentOfGoal, 0.001)
        assertEquals(8, s.daysElapsed)
        assertEquals(30, s.daysInMonth)
    }

    @Test
    fun `on track when saved covers the pro-rated goal`() {
        // Goal 3,000 ₪, day 10 of 30 → expected 1,000 ₪.
        val shifts = listOf(shift(day(2026, 11, 2), 8.0), shift(day(2026, 11, 3), 8.0), shift(day(2026, 11, 4), 8.0), shift(day(2026, 11, 5), 8.0))
        val onTrack = GoalCalculator.monthStatus(shifts, emptyList(), nov, day(2026, 11, 10), zone, 3_000_00L, rate)
        assertEquals(1_000_00L, onTrack.expectedByNowAgorot)
        assertEquals(1_216_00L, onTrack.savedAgorot)
        assertTrue(onTrack.onTrack)
        assertEquals(0L, onTrack.shortfallAgorot)
        assertEquals(0, onTrack.shortfallHours)

        val behind = GoalCalculator.monthStatus(shifts.take(2), emptyList(), nov, day(2026, 11, 10), zone, 3_000_00L, rate)
        assertFalse(behind.onTrack)
        assertEquals(392_00L, behind.shortfallAgorot) // 1,000 − 608
        assertEquals(11, behind.shortfallHours) // ceil(392 / 38) = 11
    }

    @Test
    fun `recommendation uses what is still missing to the full goal`() {
        val shifts = listOf(shift(day(2026, 11, 2), 8.0))
        val txs = listOf(tx(104.0, at(2026, 11, 3)))
        val s = GoalCalculator.monthStatus(shifts, txs, nov, day(2026, 11, 8), zone, 1_000_00L, rate)
        assertEquals(200_00L, s.savedAgorot)
        assertEquals(800_00L, s.remainingToGoalAgorot)
        assertEquals(22, s.remainingHours) // ceil(800 / 38) = 22
        assertFalse(s.isGoalMet)

        val met = GoalCalculator.monthStatus(shifts, emptyList(), nov, day(2026, 11, 8), zone, 300_00L, rate)
        assertTrue(met.isGoalMet)
        assertEquals(0L, met.remainingToGoalAgorot)
        assertEquals(0, met.remainingHours)
    }

    @Test
    fun `negative saving is possible and never on track`() {
        val txs = listOf(tx(500.0, at(2026, 11, 2)))
        val s = GoalCalculator.monthStatus(emptyList(), txs, nov, day(2026, 11, 2), zone, 1_000_00L, rate)
        assertEquals(-500_00L, s.savedAgorot)
        assertFalse(s.onTrack)
        assertEquals(566_67L, s.shortfallAgorot) // expected 1000×2/30 = 66.67 ₪ plus the 500 ₪ deficit
    }

    @Test
    fun `zero goal is never short and has no percent`() {
        val s = GoalCalculator.monthStatus(emptyList(), emptyList(), nov, day(2026, 11, 8), zone, 0L, rate)
        assertEquals(0.0, s.percentOfGoal, 0.001)
        assertTrue(s.onTrack)
        assertEquals(0L, s.expectedByNowAgorot)
    }

    @Test
    fun `future month has no elapsed days, past month has all`() {
        assertEquals(0, GoalCalculator.monthStatus(emptyList(), emptyList(), nov, day(2026, 10, 20), zone, 100L, rate).daysElapsed)
        assertEquals(30, GoalCalculator.monthStatus(emptyList(), emptyList(), nov, day(2026, 12, 2), zone, 100L, rate).daysElapsed)
    }

    @Test
    fun `history covers six months oldest first with met flag`() {
        val shifts = listOf(shift(day(2026, 10, 5), 8.0), shift(day(2026, 10, 6), 8.0), shift(day(2026, 11, 2), 8.0))
        val txs = listOf(tx(100.0, at(2026, 10, 7)))
        val h = GoalCalculator.history(shifts, txs, nov, 500_00L, zone)
        assertEquals(6, h.size)
        assertEquals(YearMonth.of(2026, 6), h.first().month)
        assertEquals(nov, h.last().month)
        val oct = h[4]
        assertEquals(YearMonth.of(2026, 10), oct.month)
        assertEquals(508_00L, oct.savedAgorot)
        assertTrue(oct.met)
        assertEquals(304_00L, h.last().savedAgorot)
        assertFalse(h.last().met)
        assertEquals(0L, h.first().savedAgorot)
    }

    // ---- weeks ----

    @Test
    fun `weeks run Sunday to Saturday and are clipped to the month`() {
        // November 2026 starts on a Sunday and ends on Monday the 30th.
        val weeks = GoalCalculator.weeksOfMonth(nov)
        assertEquals(5, weeks.size)
        assertEquals(day(2026, 11, 1), weeks[0].start)
        assertEquals(day(2026, 11, 7), weeks[0].end)
        assertEquals(7, weeks[0].days)
        assertEquals(day(2026, 11, 29), weeks[4].start)
        assertEquals(day(2026, 11, 30), weeks[4].end)
        assertEquals(2, weeks[4].days)

        // October 2026 starts on a Thursday: the first week is Thu–Sat (3 days).
        val oct = GoalCalculator.weeksOfMonth(YearMonth.of(2026, 10))
        assertEquals(day(2026, 10, 1), oct[0].start)
        assertEquals(day(2026, 10, 3), oct[0].end)
        assertEquals(3, oct[0].days)
        assertEquals(day(2026, 10, 4), oct[1].start)
        assertEquals(day(2026, 10, 31), oct.last().end)

        val w = GoalCalculator.weekOf(day(2026, 11, 11)) // Wednesday
        assertEquals(day(2026, 11, 8), w.start)
        assertEquals(day(2026, 11, 14), w.end)
    }

    @Test
    fun `clipped weeks get a pro-rated goal`() {
        assertEquals(700_00L, GoalCalculator.prorate(700_00L, 7))
        assertEquals(300_00L, GoalCalculator.prorate(700_00L, 3))
        assertEquals(200_00L, GoalCalculator.prorate(700_00L, 2))
    }

    @Test
    fun `surplus of the first week is carried into the second`() {
        // Week 1 (1–7 Nov): 3 shifts × 304 = 912 vs goal 800 → +112 carried.
        val shifts = listOf(shift(day(2026, 11, 1), 8.0), shift(day(2026, 11, 2), 8.0), shift(day(2026, 11, 3), 8.0), shift(day(2026, 11, 9), 8.0))
        val w = GoalCalculator.weekStatus(shifts, day(2026, 11, 10), 800_00L)
        assertEquals(day(2026, 11, 8), w.range.start)
        assertEquals(304_00L, w.incomeAgorot)
        assertEquals(800_00L, w.goalAgorot)
        assertEquals(112_00L, w.carryInAgorot)
        assertEquals(688_00L, w.adjustedGoalAgorot)
        assertEquals(-384_00L, w.balanceAgorot) // 112 + 304 − 800
        assertTrue(w.isDeficit)
    }

    @Test
    fun `deficit is carried and the balance becomes a surplus when the week is strong`() {
        // Week 1: 304 vs 800 → −496. Week 2: 4 shifts = 1,216 → balance −496 + 1,216 − 800 = −80.
        val shifts = listOf(
            shift(day(2026, 11, 2), 8.0),
            shift(day(2026, 11, 8), 8.0), shift(day(2026, 11, 9), 8.0), shift(day(2026, 11, 10), 8.0), shift(day(2026, 11, 11), 8.0),
        )
        val w = GoalCalculator.weekStatus(shifts, day(2026, 11, 12), 800_00L)
        assertEquals(-496_00L, w.carryInAgorot)
        assertEquals(-80_00L, w.balanceAgorot)
        val w2 = GoalCalculator.weekStatus(shifts + shift(day(2026, 11, 12), 4.0), day(2026, 11, 12), 800_00L)
        assertEquals(72_00L, w2.balanceAgorot)
        assertTrue(w2.isSurplus)
    }

    @Test
    fun `carry-over resets at the start of a month`() {
        // Big October surplus must not reach November's first week.
        val shifts = listOf(shift(day(2026, 10, 26), 8.0), shift(day(2026, 10, 27), 8.0), shift(day(2026, 10, 28), 8.0), shift(day(2026, 10, 29), 8.0))
        val w = GoalCalculator.weekStatus(shifts, day(2026, 11, 2), 800_00L)
        assertEquals(0L, w.carryInAgorot)
        assertEquals(0L, w.incomeAgorot)
        assertEquals(-800_00L, w.balanceAgorot)
    }

    @Test
    fun `first partial week of a month counts only its own days`() {
        // October 2026: Thu 1 – Sat 3 is the first week (3 days → goal 300 of 700).
        val shifts = listOf(shift(day(2026, 9, 30), 8.0), shift(day(2026, 10, 1), 8.0))
        val w = GoalCalculator.weekStatus(shifts, day(2026, 10, 2), 700_00L)
        assertEquals(day(2026, 10, 1), w.range.start)
        assertEquals(304_00L, w.incomeAgorot)
        assertEquals(300_00L, w.goalAgorot)
        assertEquals(4_00L, w.balanceAgorot)
        assertEquals(1.0, w.progress, 0.001)
    }

    // ---- long-term goals ----

    @Test
    fun `projected month at this month's pace`() {
        assertEquals(YearMonth.of(2027, 2), GoalCalculator.projectedGoalMonth(10_000_00L, 2_000_00L, 2_000_00L, nov)) // 4 months: Nov..Feb
        assertEquals(nov, GoalCalculator.projectedGoalMonth(10_000_00L, 9_000_00L, 2_000_00L, nov)) // reached this month
        assertEquals(nov, GoalCalculator.projectedGoalMonth(1_000_00L, 1_000_00L, 0L, nov)) // already reached
        assertNull(GoalCalculator.projectedGoalMonth(10_000_00L, 0L, 0L, nov))
        assertNull(GoalCalculator.projectedGoalMonth(10_000_00L, 0L, -50_00L, nov))
    }
}
