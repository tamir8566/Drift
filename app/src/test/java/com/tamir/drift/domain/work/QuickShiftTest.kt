package com.tamir.drift.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuickShiftTest {

    private val saturday: LocalDate = LocalDate.of(2026, 11, 21)
    private val sunday: LocalDate = LocalDate.of(2026, 11, 22)
    private var nextId = 1L

    private fun job(id: Long, rate: Long = 50_00L, travel: Long = 0L, active: Boolean = true) =
        JobSummary(id, "job$id", rate, shabbatMultiplier = 1.5, isActive = active, travelPerShiftAgorot = travel)

    private fun daily(id: Long, rate: Long = 400_00L) =
        JobSummary(id, "day$id", 0L, payType = PayType.DAILY, dailyRateAgorot = rate)

    private fun shift(jobId: Long, date: LocalDate, hours: Double = 8.0, start: Int? = null, end: Int? = null, travel: Long = 0L) =
        ShiftSummary(nextId++, jobId, date.toEpochDay(), hours, 0L, travelAgorot = travel, startMinute = start, endMinute = end)

    @Test
    fun `the last shift is the latest day and on one day the one saved last`() {
        val a = shift(1, sunday.minusDays(3))
        val b = shift(1, sunday.minusDays(1))
        val c = shift(1, sunday.minusDays(1))
        shift(2, sunday)
        assertEquals(c.id, QuickShift.lastShift(1, listOf(c, a, b))?.id)
        assertNull(QuickShift.lastShift(7, listOf(a, b, c)))
    }

    @Test
    fun `a template copies hours or start and end and the travel switch`() {
        val j = job(1, travel = 20_00L)
        val byHours = QuickShift.template(j, listOf(shift(1, sunday.minusDays(2), hours = 6.5, travel = 20_00L)))!!
        assertEquals(false, byHours.hasTimes)
        assertEquals(6.5, byHours.hours, 0.0001)
        assertEquals(true, byHours.travelOn)

        val byTimes = QuickShift.template(j, listOf(shift(1, sunday.minusDays(2), hours = 6.0, start = 16 * 60, end = 22 * 60)))!!
        assertEquals(true, byTimes.hasTimes)
        assertEquals(16 * 60, byTimes.startMinute)
        assertEquals(22 * 60, byTimes.endMinute)
        assertEquals(6.0, byTimes.hours, 0.0001)
        assertEquals(false, byTimes.travelOn)

        assertNull(QuickShift.template(j, emptyList()))
    }

    @Test
    fun `a daily job has no hours and no times in its template`() {
        val t = QuickShift.template(daily(2), listOf(shift(2, sunday.minusDays(1), hours = 0.0, start = 8 * 60, end = 16 * 60)))!!
        assertEquals(false, t.hasTimes)
        assertEquals(0.0, t.hours, 0.0001)
    }

    @Test
    fun `travel follows the last shift - the job's amount, or the shift's own when the job has none`() {
        val paying = job(1, travel = 25_00L)
        val notPaying = job(1, travel = 0L)
        val withTravel = listOf(shift(1, sunday.minusDays(1), travel = 20_00L))
        val without = listOf(shift(1, sunday.minusDays(1), travel = 0L))
        assertEquals(25_00L, QuickShift.travelFor(paying, QuickShift.template(paying, withTravel)!!))
        assertEquals(20_00L, QuickShift.travelFor(notPaying, QuickShift.template(notPaying, withTravel)!!))
        assertEquals(0L, QuickShift.travelFor(paying, QuickShift.template(paying, without)!!))
    }

    @Test
    fun `one tap plan - last shift of the picked job at today's rate`() {
        val jobs = listOf(job(1, rate = 50_00L, travel = 20_00L))
        val shifts = listOf(shift(1, sunday.minusDays(7), hours = 8.0, travel = 20_00L))
        val plan = QuickShift.plan(sunday, jobs, shifts)!!
        assertEquals(1L, plan.job.id)
        assertEquals(sunday.toEpochDay(), plan.epochDay)
        assertEquals(8.0, plan.hours, 0.0001)
        assertEquals(false, plan.isShabbatRate)
        assertEquals(400_00L, plan.wageAgorot)
        assertEquals(20_00L, plan.travelAgorot)
        assertEquals(420_00L, plan.totalAgorot)
    }

    @Test
    fun `one tap plan on a Saturday uses the Shabbat multiplier and never multiplies travel`() {
        val jobs = listOf(job(1, rate = 40_00L, travel = 10_00L))
        val shifts = listOf(shift(1, saturday.minusDays(2), hours = 6.0, start = 16 * 60, end = 22 * 60, travel = 10_00L))
        val plan = QuickShift.plan(saturday, jobs, shifts)!!
        assertEquals(true, plan.isShabbatRate)
        assertEquals(true, plan.hasTimes)
        assertEquals(360_00L, plan.wageAgorot)
        assertEquals(370_00L, plan.totalAgorot)
    }

    @Test
    fun `one tap plan for a daily job`() {
        val jobs = listOf(daily(2, rate = 400_00L))
        val plan = QuickShift.plan(sunday, jobs, listOf(shift(2, sunday.minusDays(1), hours = 0.0)))!!
        assertEquals(0.0, plan.hours, 0.0001)
        assertEquals(400_00L, plan.totalAgorot)
    }

    @Test
    fun `no plan without history, without an active job, or when today already has that job's shift`() {
        val jobs = listOf(job(1))
        assertNull(QuickShift.plan(sunday, jobs, emptyList()))
        assertNull(QuickShift.plan(sunday, listOf(job(1, active = false)), listOf(shift(1, sunday.minusDays(1)))))
        assertNull(QuickShift.plan(sunday, jobs, listOf(shift(1, sunday.minusDays(1)), shift(1, sunday))))
        // An hourly job whose last shift carries no hours has nothing to copy.
        assertNull(QuickShift.plan(sunday, jobs, listOf(shift(1, sunday.minusDays(1), hours = 0.0))))
    }

    @Test
    fun `the plan follows the job picked for that weekday`() {
        val jobs = listOf(job(1), job(2, rate = 50_00L))
        val shifts = listOf(
            shift(1, sunday.minusDays(1), hours = 8.0),
            shift(2, sunday.minusDays(7), hours = 5.0),
            shift(2, sunday.minusDays(14), hours = 4.0),
        )
        val plan = QuickShift.plan(sunday, jobs, shifts)!!
        assertEquals(2L, plan.job.id)
        assertEquals(5.0, plan.hours, 0.0001)
        assertEquals(250_00L, plan.wageAgorot)
    }
}
