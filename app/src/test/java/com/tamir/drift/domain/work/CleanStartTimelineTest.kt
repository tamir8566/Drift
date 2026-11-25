package com.tamir.drift.domain.work

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.time.TrackingWindow
import com.tamir.drift.domain.time.sinceStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * One story from the trial month to the third counted month, computed exactly the way the screens
 * compute it: the window of the day ([TrackingWindow.current]) filters the records, and the goal
 * calculator gets the window's start.
 *
 * It pins the three promises of the clean count:
 *  1. until the tracking start the trial month keeps counting,
 *  2. on the tracking start everything begins from zero — nothing of the trial is carried,
 *  3. from then on weeks carry into weeks (also across the end of a month) and months into months.
 */
class CleanStartTimelineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val firstLaunch: LocalDate = LocalDate.of(2026, 11, 8)
    private val trackingStart: LocalDate = LocalDate.of(2026, 12, 1)
    private val weeklyGoal = 1_200_00L
    private val monthlyGoal = 2_000_00L
    private val shiftPay = 400_00L // 8 hours at 50 ₪
    private var nextId = 1L

    private fun day(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)
    private fun shift(date: LocalDate) = ShiftSummary(nextId++, 1L, date.toEpochDay(), 8.0, shiftPay)
    private fun expense(shekels: Double, date: LocalDate) = TxnSummary(
        nextId++, shekels, shekels, "x", CategoryKeys.GROCERIES,
        LocalDateTime.of(date.year, date.monthValue, date.dayOfMonth, 12, 0).atZone(zone).toInstant().toEpochMilli(),
        TransactionSource.GPAY,
    )

    private val shifts: List<ShiftSummary> = listOf(
        // The trial month.
        day(2026, 11, 10), day(2026, 11, 17), day(2026, 11, 24), day(2026, 11, 29), day(2026, 11, 30),
        // 1–5 December (the short first week): 800 ₪.
        day(2026, 12, 1), day(2026, 12, 3),
        // 6–12 December: 1,600 ₪.
        day(2026, 12, 6), day(2026, 12, 7), day(2026, 12, 8), day(2026, 12, 9),
        // 13–19 December: 800 ₪.
        day(2026, 12, 13), day(2026, 12, 14),
        // 20–26 December: 1,200 ₪.
        day(2026, 12, 20), day(2026, 12, 21), day(2026, 12, 22),
        // 27 December – 2 January (a week that crosses the end of the month): 1,200 ₪.
        day(2026, 12, 27), day(2026, 12, 28), day(2027, 1, 1),
        // 3–9 January: 800 ₪.
        day(2027, 1, 4), day(2027, 1, 5),
    ).map { shift(it) }

    private val expenses: List<TxnSummary> = listOf(
        expense(300.0, day(2026, 11, 20)),
        expense(1_000.0, day(2026, 12, 5)),
        expense(1_500.0, day(2026, 12, 15)),
        expense(1_900.0, day(2027, 1, 10)),
    )

    /** What the goal screen computes on [today]: the month status and the week status. */
    private fun screen(today: LocalDate): Pair<MonthGoalStatus, WeekStatus> {
        val window = TrackingWindow.current(today.toEpochDay(), trackingStart.toEpochDay(), firstLaunch.toEpochDay())
        val countedShifts = shifts.sinceStart(window)
        val countedExpenses = expenses.sinceStart(window, zone)
        val month = GoalCalculator.monthStatus(
            countedShifts, countedExpenses, YearMonth.from(today), today, zone, GoalHistory.fixed(monthlyGoal), 50_00L, window.startDate,
        )
        val week = GoalCalculator.weekStatus(countedShifts, today, GoalHistory.fixed(weeklyGoal), window.startDate)
        return month to week
    }

    @Test
    fun `the calendar of the story is the real one`() {
        assertEquals(DayOfWeek.SUNDAY, firstLaunch.dayOfWeek)
        assertEquals(DayOfWeek.TUESDAY, trackingStart.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, day(2026, 12, 6).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, day(2027, 1, 3).dayOfWeek)
    }

    @Test
    fun `the last day of the trial month still counts the trial`() {
        val (month, week) = screen(day(2026, 11, 30))
        // November from the first launch: five shifts, one expense.
        assertEquals(YearMonth.of(2026, 11), month.month)
        assertEquals(5 * shiftPay, month.earnedAgorot)
        assertEquals(300_00L, month.spentAgorot)
        assertEquals(0L, month.carryInAgorot)
        // The week of 29.11: two shifts so far, and three trial weeks of 400 ₪ behind it.
        assertEquals(WeekRange(day(2026, 11, 29), day(2026, 12, 5)), week.range)
        assertEquals(2 * shiftPay, week.incomeAgorot)
        assertEquals(3 * (shiftPay - weeklyGoal), week.carryInAgorot)
    }

    @Test
    fun `on the tracking start everything begins from zero`() {
        val (month, week) = screen(trackingStart)
        // December, with only the shift of the day itself; nothing of November is carried.
        assertEquals(YearMonth.of(2026, 12), month.month)
        assertEquals(shiftPay, month.earnedAgorot)
        assertEquals(0L, month.spentAgorot)
        assertEquals(0L, month.carryInAgorot)
        assertFalse(month.hasCarry)
        assertEquals(monthlyGoal, month.adjustedGoalAgorot)
        assertEquals(31, month.daysInMonth)
        assertEquals(1, month.daysElapsed)
        // The week begins on the tracking start (Tuesday): five days, five sevenths of the goal,
        // and the shifts of Sunday and Monday (29–30.11) are not in it.
        assertEquals(WeekRange(trackingStart, day(2026, 12, 5)), week.range)
        assertEquals(5, week.range.days)
        assertEquals(857_14L, week.goalAgorot)
        assertEquals(shiftPay, week.incomeAgorot)
        assertEquals(0L, week.carryInAgorot)
        assertFalse(week.hasCarry)
    }

    @Test
    fun `the first week is carried into the second`() {
        val (_, week) = screen(day(2026, 12, 6))
        assertEquals(WeekRange(day(2026, 12, 6), day(2026, 12, 12)), week.range)
        // 800 ₪ earned against 857.14 ₪: 57.14 ₪ are missing and move on.
        assertEquals(800_00L - 857_14L, week.carryInAgorot)
        assertEquals(weeklyGoal, week.goalAgorot)
        assertEquals(weeklyGoal + 57_14L, week.adjustedGoalAgorot)
        assertEquals(shiftPay, week.incomeAgorot) // only the shift of the 6th so far
    }

    @Test
    fun `the weekly balance runs on across the end of the month`() {
        // Saturday 2 January, the last day of the week that began on 27 December.
        val (_, crossing) = screen(day(2027, 1, 2))
        assertEquals(WeekRange(day(2026, 12, 27), day(2027, 1, 2)), crossing.range)
        assertEquals(7, crossing.range.days)
        assertEquals(weeklyGoal, crossing.goalAgorot) // a full week, not cut at the month's end
        assertEquals(3 * shiftPay, crossing.incomeAgorot) // 27.12, 28.12 and 1.1 together
        // −57.14 (week 1) +400 (week 2) −400 (week 3) ±0 (week 4)
        assertEquals(-57_14L, crossing.carryInAgorot)
        assertEquals(-57_14L, crossing.balanceAgorot)

        // Sunday 3 January: a new week in a new month, and the balance came along.
        val (_, january) = screen(day(2027, 1, 3))
        assertEquals(WeekRange(day(2027, 1, 3), day(2027, 1, 9)), january.range)
        assertEquals(-57_14L, january.carryInAgorot)
        assertEquals(weeklyGoal + 57_14L, january.adjustedGoalAgorot)
    }

    @Test
    fun `december's surplus lowers january's goal`() {
        val (month, _) = screen(day(2027, 1, 3))
        // December: 13 shifts (5,200 ₪) minus 2,500 ₪ of expenses = 2,700 ₪ saved, 700 ₪ above the goal.
        assertEquals(700_00L, month.carryInAgorot)
        assertTrue(month.hasCarry)
        assertEquals(1_300_00L, month.adjustedGoalAgorot)
        assertEquals(shiftPay, month.earnedAgorot) // the shift of 1 January
        assertEquals(1_300_00L - shiftPay, month.remainingToGoalAgorot)
    }

    @Test
    fun `january's deficit raises february's goal`() {
        val (month, _) = screen(day(2027, 2, 1))
        // January: 1,200 ₪ earned − 1,900 ₪ spent = −700 ₪, 2,700 ₪ under its goal.
        // Carried: +700 (December) −2,700 (January) = −2,000.
        assertEquals(-2_000_00L, month.carryInAgorot)
        assertEquals(4_000_00L, month.adjustedGoalAgorot)
    }

    @Test
    fun `the history never shows the trial month again`() {
        val today = day(2027, 2, 1)
        val window = TrackingWindow.current(today.toEpochDay(), trackingStart.toEpochDay(), firstLaunch.toEpochDay())
        val history = GoalCalculator.history(
            shifts.sinceStart(window), expenses.sinceStart(window, zone), YearMonth.from(today),
            GoalHistory.fixed(monthlyGoal), zone, startDate = window.startDate, today = today,
        )
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2)), history.map { it.month })
        assertEquals(listOf(2_700_00L, -700_00L, 0L), history.map { it.savedAgorot })
    }

    @Test
    fun `a goal changed in january does not rewrite december`() {
        // On 10 January the monthly goal goes up to 3,000 ₪ (stamped on the 1st of the month) and
        // the weekly goal down to 1,000 ₪ (stamped on the Sunday of that week).
        val monthly = GoalHistory.parse(
            GoalHistory.change(null, monthlyGoal, 3_000_00L, day(2027, 1, 1).toEpochDay()), 3_000_00L,
        )
        val weekly = GoalHistory.parse(
            GoalHistory.change(null, weeklyGoal, 1_000_00L, GoalCalculator.weekOf(day(2027, 1, 10)).start.toEpochDay()), 1_000_00L,
        )
        val today = day(2027, 1, 10)
        val window = TrackingWindow.current(today.toEpochDay(), trackingStart.toEpochDay(), firstLaunch.toEpochDay())
        val countedShifts = shifts.sinceStart(window)
        val countedExpenses = expenses.sinceStart(window, zone)

        val month = GoalCalculator.monthStatus(countedShifts, countedExpenses, YearMonth.from(today), today, zone, monthly, 50_00L, window.startDate)
        assertEquals(3_000_00L, month.goalAgorot)
        assertEquals(700_00L, month.carryInAgorot) // December is still judged by 2,000 ₪
        assertEquals(2_300_00L, month.adjustedGoalAgorot)

        val week = GoalCalculator.weekStatus(countedShifts, today, weekly, window.startDate)
        assertEquals(WeekRange(day(2027, 1, 10), day(2027, 1, 16)), week.range)
        assertEquals(1_000_00L, week.goalAgorot)
        // Earlier weeks keep 1,200 ₪: −57.14 until 2.1, then 800 − 1,200 in the week of 3.1.
        assertEquals(-57_14L - 400_00L, week.carryInAgorot)
    }
}
