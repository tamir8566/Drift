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
    private val rate = 50_00L // 8 hours = 400 ₪
    private var nextId = 1L

    private fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun shift(date: LocalDate, hours: Double, shabbat: Boolean = false, jobId: Long = 1L, travel: Long = 0L): ShiftSummary =
        ShiftSummary(nextId++, jobId, date.toEpochDay(), hours, ShiftCalculator.amount(hours, rate, shabbat, 1.5), shabbat, travel)

    private fun tx(amount: Double, ts: Long, mine: Double = amount, kind: com.tamir.drift.domain.model.TxnKind = com.tamir.drift.domain.model.TxnKind.EXPENSE) =
        TxnSummary(nextId++, amount, mine, "x", CategoryKeys.GROCERIES, ts, TransactionSource.GPAY, isShared = mine != amount, kind = kind)

    @Test
    fun `refunds raise the saving, transfers do not touch it`() {
        val shifts = listOf(shift(day(2026, 11, 2), 8.0))
        val txs = listOf(
            tx(200.0, at(2026, 11, 5)),
            tx(60.0, at(2026, 11, 6), kind = com.tamir.drift.domain.model.TxnKind.REFUND),
            tx(90.0, at(2026, 11, 6), mine = 30.0, kind = com.tamir.drift.domain.model.TxnKind.REFUND),
            tx(5000.0, at(2026, 11, 6), kind = com.tamir.drift.domain.model.TxnKind.TRANSFER),
        )
        val s = GoalCalculator.monthStatus(shifts, txs, nov, day(2026, 11, 8), zone, 2_000_00L, rate)
        assertEquals(110_00L, s.spentAgorot) // 200 − 60 − 30
        assertEquals(400_00L - 110_00L, s.savedAgorot)
        assertEquals(110_00L, GoalCalculator.history(shifts, txs, nov, 2_000_00L, zone, months = 1).single().let { 400_00L - it.savedAgorot })
    }

    @Test
    fun `month spend counts through the end of today, like the budget`() {
        val txs = listOf(tx(40.0, at(2026, 11, 8, 22)), tx(500.0, at(2026, 11, 9, 9)))
        val s = GoalCalculator.monthStatus(emptyList(), txs, nov, day(2026, 11, 8), zone, 2_000_00L, rate)
        assertEquals(40_00L, s.spentAgorot)
        assertEquals(540_00L, GoalCalculator.spentIn(txs, nov, zone))
    }

    @Test
    fun `shifts dated after today are not earned yet`() {
        // Today is Tuesday 10 Nov; the shift of Thursday the 12th was entered ahead of time.
        val shifts = listOf(shift(day(2026, 11, 9), 8.0), shift(day(2026, 11, 12), 8.0))
        val today = day(2026, 11, 10)
        assertEquals(400_00L, GoalCalculator.monthStatus(shifts, emptyList(), nov, today, zone, 2_000_00L, rate).earnedAgorot)
        assertEquals(400_00L, GoalCalculator.weekStatus(shifts, today, 1_000_00L, day(2026, 11, 1)).incomeAgorot)
        assertEquals(400_00L, GoalCalculator.earnedIn(shifts, nov, today.toEpochDay()))
        assertEquals(8.0, GoalCalculator.hoursIn(shifts, nov, today.toEpochDay()), 0.001)
        // Without a cut-off the whole month counts (a past month, the history).
        assertEquals(800_00L, GoalCalculator.earnedIn(shifts, nov))
        // On its day the shift counts.
        assertEquals(800_00L, GoalCalculator.monthStatus(shifts, emptyList(), nov, day(2026, 11, 12), zone, 2_000_00L, rate).earnedAgorot)
        // The history's current month is cut at today as well.
        assertEquals(400_00L, GoalCalculator.history(shifts, emptyList(), nov, 2_000_00L, zone, months = 1, today = today).single().savedAgorot)
    }

    @Test
    fun `month bar keeps the real percent above the goal and below zero`() {
        val over = MonthGoalStatus(nov, 3_000_00L, 400_00L, 2_000_00L, 8, 30, rate)
        assertEquals(1.0, over.bar.fraction.toDouble(), 0.0001)
        assertEquals(130, over.bar.percent)
        val negative = MonthGoalStatus(nov, 100_00L, 600_00L, 2_000_00L, 8, 30, rate)
        assertEquals(0.0, negative.bar.fraction.toDouble(), 0.0001)
        assertEquals(-25, negative.bar.percent)
        assertTrue(negative.bar.isNegative)
    }

    @Test
    fun `week bar, goal and message all use the goal after the carry-over`() {
        // Deficit carried in: goal 1,200, carry −220 → this week needs 1,420.
        val behind = WeekStatus(WeekRange(day(2026, 11, 8), day(2026, 11, 14)), incomeAgorot = 1_200_00L, goalAgorot = 1_200_00L, carryInAgorot = -220_00L)
        assertEquals(1_420_00L, behind.adjustedGoalAgorot)
        assertEquals(85, behind.bar.percent)                       // 1,200 of 1,420 — not "100%"
        assertEquals(-220_00L, behind.balanceAgorot)               // the message: 220 still missing
        assertEquals(behind.incomeAgorot - behind.adjustedGoalAgorot, behind.balanceAgorot)
        assertTrue(behind.hasCarry)

        // Surplus carried in: goal 1,200, carry +180 → this week needs 1,020.
        val ahead = WeekStatus(WeekRange(day(2026, 11, 8), day(2026, 11, 14)), 1_020_00L, 1_200_00L, 180_00L)
        assertEquals(1_020_00L, ahead.adjustedGoalAgorot)
        assertEquals(100, ahead.bar.percent)
        assertEquals(0L, ahead.balanceAgorot)

        // A surplus bigger than the whole goal: nothing is needed, the bar is full.
        val covered = WeekStatus(WeekRange(day(2026, 11, 8), day(2026, 11, 14)), 0L, 1_200_00L, 1_500_00L)
        assertEquals(0L, covered.adjustedGoalAgorot)
        assertTrue(covered.isCoveredByCarry)
        assertEquals(1.0, covered.bar.fraction.toDouble(), 0.0001)
        assertEquals(300_00L, covered.balanceAgorot)

        // No carry: the adjusted goal is the goal.
        val plain = WeekStatus(WeekRange(day(2026, 11, 8), day(2026, 11, 14)), 300_00L, 1_200_00L, 0L)
        assertFalse(plain.hasCarry)
        assertEquals(25, plain.bar.percent)
        assertEquals(0.25, plain.progress, 0.001)
    }

    // ---- month ----

    @Test
    fun `saved is shift income minus my share of expenses`() {
        val shifts = listOf(shift(day(2026, 11, 2), 8.0), shift(day(2026, 11, 3), 8.0), shift(day(2026, 10, 30), 8.0))
        val txs = listOf(tx(200.0, at(2026, 11, 5)), tx(300.0, at(2026, 11, 6), mine = 100.0), tx(999.0, at(2026, 10, 20)))
        val s = GoalCalculator.monthStatus(shifts, txs, nov, day(2026, 11, 8), zone, 2_000_00L, rate, startDate = day(2026, 11, 1))
        assertEquals(800_00L, s.earnedAgorot)
        assertEquals(300_00L, s.spentAgorot)
        assertEquals(500_00L, s.savedAgorot)
        assertEquals(25.0, s.percentOfGoal, 0.001)
        assertEquals(8, s.daysElapsed)
        assertEquals(30, s.daysInMonth)
        // October is before the tracking start: nothing of it is carried into November.
        assertEquals(0L, s.carryInAgorot)
        assertFalse(s.hasCarry)
    }

    @Test
    fun `shift income is wage plus travel`() {
        val s = shift(day(2026, 11, 2), 8.0, travel = 22_00L)
        assertEquals(400_00L, s.amountAgorot)
        assertEquals(22_00L, s.travelAgorot)
        assertEquals(422_00L, s.totalAgorot)
        // Shabbat raises the wage only.
        assertEquals(622_00L, shift(day(2026, 11, 7), 8.0, shabbat = true, travel = 22_00L).totalAgorot)
        assertEquals(400_00L, shift(day(2026, 11, 3), 8.0).totalAgorot)
    }

    @Test
    fun `month totals include travel reimbursement`() {
        // Three November shifts (two with travel) and one October shift with travel.
        val shifts = listOf(
            shift(day(2026, 11, 2), 8.0, travel = 22_00L), shift(day(2026, 11, 3), 8.0, travel = 22_00L),
            shift(day(2026, 11, 4), 8.0), shift(day(2026, 10, 30), 8.0, travel = 22_00L),
        )
        assertEquals(1_244_00L, GoalCalculator.earnedIn(shifts, nov)) // 3 × 400 + 2 × 22
        assertEquals(44_00L, GoalCalculator.travelIn(shifts, nov))
        assertEquals(22_00L, GoalCalculator.travelIn(shifts, YearMonth.of(2026, 10)))
        val s = GoalCalculator.monthStatus(shifts, listOf(tx(200.0, at(2026, 11, 5))), nov, day(2026, 11, 8), zone, 2_000_00L, rate, startDate = day(2026, 11, 1))
        assertEquals(1_244_00L, s.earnedAgorot)
        assertEquals(1_044_00L, s.savedAgorot)
    }

    @Test
    fun `week income and carry-over include travel reimbursement`() {
        // Week 1 (1–7 Nov): 3 × (400 + 22) = 1,266 vs goal 800 → +466 carried. Week 2: 400 + 22.
        // No start date: the count begins with the week of the first shift (Sunday 1 Nov).
        val shifts = listOf(
            shift(day(2026, 11, 1), 8.0, travel = 22_00L), shift(day(2026, 11, 2), 8.0, travel = 22_00L),
            shift(day(2026, 11, 3), 8.0, travel = 22_00L), shift(day(2026, 11, 9), 8.0, travel = 22_00L),
        )
        val w = GoalCalculator.weekStatus(shifts, day(2026, 11, 10), 800_00L)
        assertEquals(422_00L, w.incomeAgorot)
        assertEquals(466_00L, w.carryInAgorot)
        assertEquals(88_00L, w.balanceAgorot) // 466 + 422 − 800
        assertEquals(422_00L, GoalCalculator.incomeIn(shifts, GoalCalculator.weekOf(day(2026, 11, 10))))
    }

    @Test
    fun `history counts travel in the saved amount`() {
        val shifts = listOf(shift(day(2026, 10, 5), 8.0, travel = 22_00L), shift(day(2026, 11, 2), 8.0, travel = 10_50L))
        val h = GoalCalculator.history(shifts, listOf(tx(100.0, at(2026, 10, 7))), nov, 500_00L, zone)
        assertEquals(322_00L, h[4].savedAgorot) // 400 + 22 − 100
        assertEquals(410_50L, h.last().savedAgorot)
    }

    @Test
    fun `on track when saved covers the pro-rated goal`() {
        // Goal 3,000 ₪, day 10 of 30 → expected 1,000 ₪.
        val shifts = listOf(shift(day(2026, 11, 2), 8.0), shift(day(2026, 11, 3), 8.0), shift(day(2026, 11, 4), 8.0), shift(day(2026, 11, 5), 8.0))
        val onTrack = GoalCalculator.monthStatus(shifts, emptyList(), nov, day(2026, 11, 10), zone, 3_000_00L, rate)
        assertEquals(1_000_00L, onTrack.expectedByNowAgorot)
        assertEquals(1_600_00L, onTrack.savedAgorot)
        assertTrue(onTrack.onTrack)
        assertEquals(0L, onTrack.shortfallAgorot)
        assertEquals(0, onTrack.shortfallHours)

        val behind = GoalCalculator.monthStatus(shifts.take(2), emptyList(), nov, day(2026, 11, 10), zone, 3_000_00L, rate)
        assertFalse(behind.onTrack)
        assertEquals(200_00L, behind.shortfallAgorot) // 1,000 − 800
        assertEquals(4, behind.shortfallHours) // 200 / 50
    }

    @Test
    fun `recommendation uses what is still missing to the full goal`() {
        val shifts = listOf(shift(day(2026, 11, 2), 8.0))
        val txs = listOf(tx(90.0, at(2026, 11, 3)))
        val s = GoalCalculator.monthStatus(shifts, txs, nov, day(2026, 11, 8), zone, 1_000_00L, rate)
        assertEquals(310_00L, s.savedAgorot)
        assertEquals(690_00L, s.remainingToGoalAgorot)
        assertEquals(14, s.remainingHours) // ceil(690 / 50) = 14
        assertFalse(s.isGoalMet)

        val met = GoalCalculator.monthStatus(shifts, emptyList(), nov, day(2026, 11, 8), zone, 300_00L, rate)
        assertTrue(met.isGoalMet)
        assertEquals(0L, met.remainingToGoalAgorot)
        assertEquals(0, met.remainingHours)
    }

    @Test
    fun `daily main job expresses the shortfall in days`() {
        // Behind by 200 ₪ (1,000 expected − 800 earned); daily rate 300 ₪ → 1 day.
        val shifts = listOf(shift(day(2026, 11, 2), 8.0), shift(day(2026, 11, 3), 8.0))
        val s = GoalCalculator.monthStatus(shifts, emptyList(), nov, day(2026, 11, 10), zone, 3_000_00L, 300_00L, mainPayType = PayType.DAILY)
        assertTrue(s.mainJobIsDaily)
        assertEquals(200_00L, s.shortfallAgorot)
        assertEquals(1, s.shortfallDays)
        assertEquals(0, s.shortfallHours)
        assertEquals(2_200_00L, s.remainingToGoalAgorot)
        assertEquals(8, s.remainingDays) // ceil(2,200 / 300)
        assertEquals(0, s.remainingHours)

        val hourly = GoalCalculator.monthStatus(shifts, emptyList(), nov, day(2026, 11, 10), zone, 3_000_00L, rate)
        assertFalse(hourly.mainJobIsDaily)
        assertEquals(0, hourly.shortfallDays)
        assertEquals(4, hourly.shortfallHours)
    }

    @Test
    fun `month hours count only the shifts of hourly jobs`() {
        val jobs = listOf(
            JobSummary(1L, "hourly", rate),
            JobSummary(2L, "daily", 0L, payType = PayType.DAILY, dailyRateAgorot = 300_00L),
        )
        val shifts = listOf(
            shift(day(2026, 11, 2), 8.0, jobId = 1L),
            ShiftSummary(nextId++, 2L, day(2026, 11, 3).toEpochDay(), 0.0, 300_00L),
            // A stale hours value on a daily job must not leak into the hours stat.
            ShiftSummary(nextId++, 2L, day(2026, 11, 4).toEpochDay(), 6.0, 300_00L),
        )
        assertEquals(8.0, GoalCalculator.hourlyHoursIn(shifts, jobs, nov), 0.001)
        assertEquals(14.0, GoalCalculator.hoursIn(shifts, nov), 0.001)
        assertEquals(1_000_00L, GoalCalculator.earnedIn(shifts, nov))
        assertEquals(300_00L, jobs[1].unitRateAgorot)
        assertEquals(rate, jobs[0].unitRateAgorot)
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
        assertEquals(700_00L, oct.savedAgorot)
        assertTrue(oct.met)
        assertEquals(400_00L, h.last().savedAgorot)
        assertFalse(h.last().met)
        assertEquals(0L, h.first().savedAgorot)
    }

    // ---- months: the carry-over ----

    private val dec = YearMonth.of(2026, 12)
    private val jan = YearMonth.of(2027, 1)
    private val trackingStart = day(2026, 12, 1)

    /** [count] eight-hour shifts (400 ₪ each) on consecutive days from [from]. */
    private fun shifts(from: LocalDate, count: Int): List<ShiftSummary> = (0 until count).map { shift(from.plusDays(it.toLong()), 8.0) }

    @Test
    fun `a month's deficit is carried into the next month`() {
        // December: 5 shifts = 2,000 earned, 500 spent → saved 1,500 of 2,000 → 500 missing.
        // January so far: 2 shifts = 800 earned, 100 spent → saved 700.
        val work = shifts(day(2026, 12, 1), 5) + shifts(day(2027, 1, 4), 2)
        val txs = listOf(tx(500.0, at(2026, 12, 10)), tx(100.0, at(2027, 1, 6)))
        val s = GoalCalculator.monthStatus(work, txs, jan, day(2027, 1, 10), zone, 2_000_00L, rate, trackingStart)
        assertEquals(-500_00L, s.carryInAgorot)
        assertTrue(s.hasCarry)
        assertEquals(2_000_00L, s.goalAgorot)
        assertEquals(2_500_00L, s.adjustedGoalAgorot)   // the goal plus what December missed
        assertEquals(700_00L, s.savedAgorot)
        assertEquals(28, s.bar.percent)                 // 700 of 2,500
        assertEquals(806_45L, s.expectedByNowAgorot)    // 2,500 × 10 / 31
        assertFalse(s.onTrack)
        assertEquals(106_45L, s.shortfallAgorot)
        assertEquals(1_800_00L, s.remainingToGoalAgorot)
        assertFalse(s.isGoalMet)
        // December itself started clean.
        val december = GoalCalculator.monthStatus(work, txs, dec, day(2026, 12, 31), zone, 2_000_00L, rate, trackingStart)
        assertEquals(0L, december.carryInAgorot)
        assertEquals(1_500_00L, december.savedAgorot)
    }

    @Test
    fun `a month's surplus lowers the next month's goal`() {
        // December: 7 shifts = 2,800 earned, 200 spent → saved 2,600 of 2,000 → 600 ahead.
        val work = shifts(day(2026, 12, 1), 7) + shifts(day(2027, 1, 4), 4)
        val txs = listOf(tx(200.0, at(2026, 12, 10)))
        val s = GoalCalculator.monthStatus(work, txs, jan, day(2027, 1, 31), zone, 2_000_00L, rate, trackingStart)
        assertEquals(600_00L, s.carryInAgorot)
        assertEquals(1_400_00L, s.adjustedGoalAgorot)
        assertFalse(s.isCoveredByCarry)
        assertEquals(1_600_00L, s.savedAgorot)
        assertTrue(s.isGoalMet)                         // 1,600 ≥ 1,400
        assertEquals(0L, s.remainingToGoalAgorot)
        assertEquals(114, s.bar.percent)
    }

    @Test
    fun `a surplus bigger than the goal covers the whole month`() {
        // December: 12 shifts = 4,800 earned, 300 spent → saved 4,500 of 2,000 → 2,500 ahead.
        val work = shifts(day(2026, 12, 1), 12)
        val december = listOf(tx(300.0, at(2026, 12, 10)))
        val light = GoalCalculator.monthStatus(work, december + tx(300.0, at(2027, 1, 5)), jan, day(2027, 1, 10), zone, 2_000_00L, rate, trackingStart)
        assertEquals(2_500_00L, light.carryInAgorot)
        assertTrue(light.isCoveredByCarry)
        assertEquals(0L, light.adjustedGoalAgorot)
        assertEquals(-500_00L, light.needAgorot)        // 500 of the surplus may still be used up
        assertEquals(-300_00L, light.savedAgorot)
        assertTrue(light.onTrack)
        assertEquals(0L, light.shortfallAgorot)
        assertEquals(0L, light.remainingToGoalAgorot)
        assertEquals(1.0, light.bar.fraction.toDouble(), 0.0001)
        assertFalse(light.isGoalMet)                    // covered by the carry, not reached this month

        // Spending more than the extra surplus puts the month behind.
        val heavy = GoalCalculator.monthStatus(work, december + tx(700.0, at(2027, 1, 5)), jan, day(2027, 1, 10), zone, 2_000_00L, rate, trackingStart)
        assertFalse(heavy.onTrack)
        assertEquals(200_00L, heavy.shortfallAgorot)
        assertEquals(200_00L, heavy.remainingToGoalAgorot)
        assertEquals(0.0, heavy.bar.fraction.toDouble(), 0.0001)
    }

    @Test
    fun `nothing from before the tracking start is carried`() {
        // November (the trial month) is far below the goal; the count begins on 1 December.
        val work = shifts(day(2026, 11, 2), 1) + shifts(day(2026, 12, 1), 5)
        val txs = listOf(tx(3_000.0, at(2026, 11, 20)), tx(500.0, at(2026, 12, 10)))
        val december = GoalCalculator.monthStatus(work, txs, dec, day(2026, 12, 10), zone, 2_000_00L, rate, trackingStart)
        assertEquals(0L, december.carryInAgorot)
        assertEquals(2_000_00L, december.adjustedGoalAgorot)
        // January carries December only: 2,000 − 500 − 2,000 = −500.
        val january = GoalCalculator.monthStatus(work, txs, jan, day(2027, 1, 5), zone, 2_000_00L, rate, trackingStart)
        assertEquals(-500_00L, january.carryInAgorot)
        assertEquals(-500_00L, GoalCalculator.monthCarry(work, txs, jan, zone, GoalHistory.fixed(2_000_00L), trackingStart))
        // While the trial month runs, the window starts at the first launch and November has no earlier month.
        val november = GoalCalculator.monthStatus(work, txs, nov, day(2026, 11, 25), zone, 2_000_00L, rate, day(2026, 11, 8))
        assertEquals(0L, november.carryInAgorot)
    }

    @Test
    fun `each month is judged by the goal of its time`() {
        // The goal was 2,000 in December and is 3,000 from 1 January on.
        val goal = GoalHistory.parse(GoalHistory.change(null, 2_000_00L, 3_000_00L, day(2027, 1, 1).toEpochDay()), 3_000_00L)
        val work = shifts(day(2026, 12, 1), 5)
        val txs = listOf(tx(500.0, at(2026, 12, 10)))
        val s = GoalCalculator.monthStatus(work, txs, jan, day(2027, 1, 5), zone, goal, rate, trackingStart)
        assertEquals(3_000_00L, s.goalAgorot)
        assertEquals(-500_00L, s.carryInAgorot)         // December against 2,000 — not against 3,000
        assertEquals(3_500_00L, s.adjustedGoalAgorot)
        val h = GoalCalculator.history(work, txs, jan, goal, zone, months = 2, startDate = trackingStart)
        assertEquals(2_000_00L, h[0].goalAgorot)
        assertEquals(3_000_00L, h[1].goalAgorot)
    }

    @Test
    fun `without a goal nothing is carried`() {
        val work = shifts(day(2026, 12, 1), 5)
        val s = GoalCalculator.monthStatus(work, emptyList(), jan, day(2027, 1, 5), zone, 0L, rate, trackingStart)
        assertEquals(0L, s.carryInAgorot)
        assertFalse(s.hasCarry)
        assertTrue(s.onTrack)
    }

    // ---- weeks ----

    @Test
    fun `weeks run Sunday to Saturday, also across the end of a month`() {
        val w = GoalCalculator.weekOf(day(2026, 11, 11)) // Wednesday
        assertEquals(day(2026, 11, 8), w.start)
        assertEquals(day(2026, 11, 14), w.end)
        assertEquals(7, w.days)
        val across = GoalCalculator.weekOf(day(2026, 12, 1)) // Tuesday
        assertEquals(day(2026, 11, 29), across.start)
        assertEquals(day(2026, 12, 5), across.end)
    }

    @Test
    fun `a week that begins at the tracking start gets a pro-rated goal`() {
        assertEquals(700_00L, GoalCalculator.prorate(700_00L, 7))
        assertEquals(300_00L, GoalCalculator.prorate(700_00L, 3))
        assertEquals(200_00L, GoalCalculator.prorate(700_00L, 2))
    }

    @Test
    fun `surplus of the first week is carried into the second`() {
        // Week 1 (1–7 Nov): 3 shifts × 400 = 1,200 vs goal 1,000 → +200 carried.
        val shifts = listOf(shift(day(2026, 11, 1), 8.0), shift(day(2026, 11, 2), 8.0), shift(day(2026, 11, 3), 8.0), shift(day(2026, 11, 9), 8.0))
        val w = GoalCalculator.weekStatus(shifts, day(2026, 11, 10), 1_000_00L, day(2026, 11, 1))
        assertEquals(day(2026, 11, 8), w.range.start)
        assertEquals(day(2026, 11, 14), w.range.end)
        assertEquals(400_00L, w.incomeAgorot)
        assertEquals(1_000_00L, w.goalAgorot)
        assertEquals(200_00L, w.carryInAgorot)
        assertEquals(800_00L, w.adjustedGoalAgorot)
        assertEquals(-400_00L, w.balanceAgorot) // 200 + 400 − 1,000
        assertTrue(w.isDeficit)
    }

    @Test
    fun `deficit is carried and the balance becomes a surplus when the week is strong`() {
        // Week 1: 400 vs 1,000 → −600. Week 2: 4 shifts = 1,600 → balance −600 + 1,600 − 1,000 = 0.
        val shifts = listOf(
            shift(day(2026, 11, 2), 8.0),
            shift(day(2026, 11, 8), 8.0), shift(day(2026, 11, 9), 8.0), shift(day(2026, 11, 10), 8.0), shift(day(2026, 11, 11), 8.0),
        )
        val w = GoalCalculator.weekStatus(shifts, day(2026, 11, 12), 1_000_00L, day(2026, 11, 1))
        assertEquals(-600_00L, w.carryInAgorot)
        assertEquals(0L, w.balanceAgorot)
        val w2 = GoalCalculator.weekStatus(shifts + shift(day(2026, 11, 12), 4.0), day(2026, 11, 12), 1_000_00L, day(2026, 11, 1))
        assertEquals(200_00L, w2.balanceAgorot)
        assertTrue(w2.isSurplus)
    }

    @Test
    fun `the balance runs on across the end of a month`() {
        // Counting from Sunday 22 Nov, goal 1,000 a week.
        // 22–28 Nov: 3 shifts = 1,200 → +200. 29 Nov – 5 Dec (one week, two months): 3 shifts → +200.
        val shifts = listOf(
            shift(day(2026, 11, 23), 8.0), shift(day(2026, 11, 24), 8.0), shift(day(2026, 11, 25), 8.0),
            shift(day(2026, 11, 30), 8.0), shift(day(2026, 12, 1), 8.0), shift(day(2026, 12, 2), 8.0),
            shift(day(2026, 12, 7), 8.0),
        )
        val w = GoalCalculator.weekStatus(shifts, day(2026, 12, 8), 1_000_00L, day(2026, 11, 22))
        assertEquals(day(2026, 12, 6), w.range.start)
        assertEquals(day(2026, 12, 12), w.range.end)
        assertEquals(400_00L, w.carryInAgorot)          // nothing was reset on 1 December
        assertEquals(400_00L, w.incomeAgorot)
        assertEquals(600_00L, w.adjustedGoalAgorot)
        assertEquals(-200_00L, w.balanceAgorot)
        // The week that holds the turn of the month is one whole week with one whole goal.
        val turn = GoalCalculator.weekStatus(shifts, day(2026, 12, 3), 1_000_00L, day(2026, 11, 22))
        assertEquals(day(2026, 11, 29), turn.range.start)
        assertEquals(day(2026, 12, 5), turn.range.end)
        assertEquals(1_000_00L, turn.goalAgorot)
        assertEquals(1_200_00L, turn.incomeAgorot)
        assertEquals(200_00L, turn.carryInAgorot)
    }

    @Test
    fun `the count begins clean at the tracking start`() {
        // Tracking starts on Tuesday 1 Dec. The two November shifts of that week do not count.
        val shifts = listOf(
            shift(day(2026, 11, 29), 8.0), shift(day(2026, 11, 30), 8.0),
            shift(day(2026, 12, 1), 8.0), shift(day(2026, 12, 2), 8.0),
        )
        val first = GoalCalculator.weekStatus(shifts, day(2026, 12, 3), 700_00L, trackingStart)
        assertEquals(day(2026, 12, 1), first.range.start)
        assertEquals(day(2026, 12, 5), first.range.end)
        assertEquals(500_00L, first.goalAgorot)         // 5 of 7 days
        assertEquals(800_00L, first.incomeAgorot)
        assertEquals(0L, first.carryInAgorot)
        assertEquals(300_00L, first.balanceAgorot)
        // The next week carries that first, partial week — and nothing older.
        val second = GoalCalculator.weekStatus(shifts, day(2026, 12, 8), 700_00L, trackingStart)
        assertEquals(day(2026, 12, 6), second.range.start)
        assertEquals(300_00L, second.carryInAgorot)
        assertEquals(700_00L, second.goalAgorot)
        assertEquals(-400_00L, second.balanceAgorot)
        // The day before the start, the trial window (from the first launch) is still the one in use.
        val trial = GoalCalculator.weekStatus(shifts, day(2026, 11, 30), 700_00L, day(2026, 11, 29))
        assertEquals(800_00L, trial.incomeAgorot)
    }

    @Test
    fun `each week is judged by the goal of its time`() {
        // The goal was 1,000 and is 1,500 from the week of 8 Nov on.
        val goal = GoalHistory.parse(GoalHistory.change(null, 1_000_00L, 1_500_00L, day(2026, 11, 8).toEpochDay()), 1_500_00L)
        val shifts = listOf(shift(day(2026, 11, 2), 8.0), shift(day(2026, 11, 3), 8.0), shift(day(2026, 11, 4), 8.0))
        val w = GoalCalculator.weekStatus(shifts, day(2026, 11, 10), goal, day(2026, 11, 1))
        assertEquals(200_00L, w.carryInAgorot)          // 1,200 against 1,000 — not against 1,500
        assertEquals(1_500_00L, w.goalAgorot)
        assertEquals(1_300_00L, w.adjustedGoalAgorot)
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

class GoalCalculatorStartDateTest {
    private val zone = java.time.ZoneId.of("Asia/Jerusalem")

    @org.junit.Test
    fun `weeks before the tracking start carry nothing`() {
        val today = java.time.LocalDate.of(2026, 11, 8)      // Sunday, 2nd week of November
        val start = java.time.LocalDate.of(2026, 11, 8)
        val w = GoalCalculator.weekStatus(emptyList(), today, 1_000_00L, start)
        org.junit.Assert.assertEquals(0L, w.carryInAgorot)
        org.junit.Assert.assertEquals(1_000_00L, w.goalAgorot)
    }

    @org.junit.Test
    fun `month expectation is pro-rated from the tracking start`() {
        val today = java.time.LocalDate.of(2026, 11, 8)
        val start = java.time.LocalDate.of(2026, 11, 8)
        val s = GoalCalculator.monthStatus(emptyList(), emptyList(), java.time.YearMonth.of(2026, 11), today, zone, 2_300_00L, 50_00L, start)
        org.junit.Assert.assertEquals(23, s.daysInMonth)   // 8..30 November
        org.junit.Assert.assertEquals(1, s.daysElapsed)
        org.junit.Assert.assertEquals(100_00L, s.expectedByNowAgorot) // 2,300 / 23
    }

    @org.junit.Test
    fun `history drops months before the tracking start`() {
        val start = java.time.LocalDate.of(2026, 11, 8)
        val h = GoalCalculator.history(emptyList(), emptyList(), java.time.YearMonth.of(2026, 11), 1L, zone, 6, start)
        org.junit.Assert.assertEquals(1, h.size)
    }
}
