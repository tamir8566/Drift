package com.tamir.drift.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftCalculatorTest {

    private val rate = 50_00L // 50 ₪ per hour

    @Test
    fun `an edited shift keeps its stored rate unless hours or job change`() {
        val stored = RateSnapshot(50_00L, 1.5)
        val current = RateSnapshot(42_00L, 2.0)
        assertEquals(stored, ShiftCalculator.rateForEdit(current, stored, sameJob = true, sameHours = true))
        assertEquals(current, ShiftCalculator.rateForEdit(current, stored, sameJob = true, sameHours = false))
        assertEquals(current, ShiftCalculator.rateForEdit(current, stored, sameJob = false, sameHours = true))
        // A shift from before rates were stored falls back to the job's current rate.
        assertEquals(current, ShiftCalculator.rateForEdit(current, null, sameJob = true, sameHours = true))
    }

    @Test
    fun `amount from a rate snapshot for both pay types`() {
        val snap = RateSnapshot(50_00L, 1.5)
        assertEquals(400_00L, ShiftCalculator.amount(PayType.HOURLY, 8.0, snap, false))
        assertEquals(600_00L, ShiftCalculator.amount(PayType.HOURLY, 8.0, snap, true))
        val daily = RateSnapshot(400_00L, 1.5)
        assertEquals(400_00L, ShiftCalculator.amount(PayType.DAILY, 0.0, daily, false))
        assertEquals(600_00L, ShiftCalculator.amount(PayType.DAILY, 0.0, daily, true))
        assertEquals(true, ShiftCalculator.sameHours(7.5, 7.5000000001))
        assertEquals(false, ShiftCalculator.sameHours(7.5, 7.75))
    }

    @Test
    fun `minimum wage constant and check`() {
        assertEquals(35_40L, MIN_HOURLY_WAGE_AGOROT)
        assertEquals(true, JobSummary(1, "a", 35_00L).isBelowMinimumWage)
        assertEquals(false, JobSummary(1, "a", 35_40L).isBelowMinimumWage)
        assertEquals(false, JobSummary(1, "a", 0L, payType = PayType.DAILY, dailyRateAgorot = 200_00L).isBelowMinimumWage)
    }

    @Test
    fun `regular shift is hours times rate`() {
        assertEquals(400_00L, ShiftCalculator.amount(8.0, rate, false, 1.5))
        assertEquals(325_00L, ShiftCalculator.amount(6.5, rate, false, 1.5))
    }

    @Test
    fun `shabbat shift applies the multiplier`() {
        assertEquals(600_00L, ShiftCalculator.amount(8.0, rate, true, 1.5))
        assertEquals(800_00L, ShiftCalculator.amount(8.0, rate, true, 2.0))
    }

    @Test
    fun `zero or negative hours give zero`() {
        assertEquals(0L, ShiftCalculator.amount(0.0, rate, false, 1.5))
        assertEquals(0L, ShiftCalculator.amount(-2.0, rate, true, 1.5))
        assertEquals(0L, ShiftCalculator.amount(5.0, 0L, false, 1.5))
    }

    @Test
    fun `manual amount overrides the computed one`() {
        assertEquals(350_00L, ShiftCalculator.resolve(8.0, rate, false, 1.5, 350_00L))
        assertEquals(400_00L, ShiftCalculator.resolve(8.0, rate, false, 1.5, null))
    }

    @Test
    fun `daily job pays the daily rate regardless of hours`() {
        val daily = 400_00L
        assertEquals(400_00L, ShiftCalculator.dailyAmount(daily, false, 1.5))
        assertEquals(600_00L, ShiftCalculator.dailyAmount(daily, true, 1.5))
        assertEquals(0L, ShiftCalculator.dailyAmount(0L, true, 1.5))
        // The typed amount wins; hours and hourly rate are ignored for a daily job.
        assertEquals(400_00L, ShiftCalculator.amount(PayType.DAILY, 0.0, rate, daily, false, 1.5))
        assertEquals(400_00L, ShiftCalculator.resolve(PayType.DAILY, 9.0, rate, daily, false, 1.5, null))
        assertEquals(600_00L, ShiftCalculator.resolve(PayType.DAILY, 0.0, rate, daily, true, 1.5, null))
        assertEquals(450_00L, ShiftCalculator.resolve(PayType.DAILY, 0.0, rate, daily, true, 1.5, 450_00L))
    }

    @Test
    fun `hourly resolve with pay type matches the classic one`() {
        assertEquals(400_00L, ShiftCalculator.resolve(PayType.HOURLY, 8.0, rate, 999_00L, false, 1.5, null))
        assertEquals(600_00L, ShiftCalculator.resolve(PayType.HOURLY, 8.0, rate, 999_00L, true, 1.5, null))
        assertEquals(350_00L, ShiftCalculator.resolve(PayType.HOURLY, 8.0, rate, 999_00L, false, 1.5, 350_00L))
    }

    // ---- travel reimbursement ----

    private val travel = 22_00L // 22 ₪ per shift

    @Test
    fun `hourly day total is wage plus travel`() {
        // 8 h × 50 ₪ = 400 ₪, + 22 ₪ travel.
        assertEquals(422_00L, ShiftCalculator.dayTotal(PayType.HOURLY, 8.0, rate, 0L, false, 1.5, null, travel, true))
        // A job without travel: the total is the wage alone.
        assertEquals(400_00L, ShiftCalculator.dayTotal(PayType.HOURLY, 8.0, rate, 0L, false, 1.5, null, 0L, true))
    }

    @Test
    fun `daily day total is the day rate plus travel`() {
        assertEquals(422_00L, ShiftCalculator.dayTotal(PayType.DAILY, 0.0, rate, 400_00L, false, 1.5, null, travel, true))
    }

    @Test
    fun `shabbat multiplier is not applied to travel`() {
        // 400 × 1.5 = 600, + 22 (not 33).
        assertEquals(622_00L, ShiftCalculator.dayTotal(PayType.HOURLY, 8.0, rate, 0L, true, 1.5, null, travel, true))
        // 400 × 1.5 = 600, + 22.
        assertEquals(622_00L, ShiftCalculator.dayTotal(PayType.DAILY, 0.0, rate, 400_00L, true, 1.5, null, travel, true))
        assertEquals(travel, ShiftCalculator.travel(travel, true))
    }

    @Test
    fun `manual wage override leaves travel untouched`() {
        assertEquals(372_00L, ShiftCalculator.dayTotal(PayType.HOURLY, 8.0, rate, 0L, false, 1.5, 350_00L, travel, true))
        assertEquals(372_00L, ShiftCalculator.dayTotal(PayType.HOURLY, 8.0, rate, 0L, true, 1.5, 350_00L, travel, true))
        // A manual wage of zero still gets the travel.
        assertEquals(travel, ShiftCalculator.dayTotal(PayType.DAILY, 0.0, rate, 400_00L, false, 1.5, 0L, travel, true))
    }

    @Test
    fun `travel toggle off gives the wage alone`() {
        assertEquals(400_00L, ShiftCalculator.dayTotal(PayType.HOURLY, 8.0, rate, 0L, false, 1.5, null, travel, false))
        assertEquals(350_00L, ShiftCalculator.dayTotal(PayType.HOURLY, 8.0, rate, 0L, false, 1.5, 350_00L, travel, false))
        assertEquals(0L, ShiftCalculator.travel(travel, false))
        // A negative amount never reduces the wage.
        assertEquals(0L, ShiftCalculator.travel(-5_00L, true))
    }

    @Test
    fun `short hours format keeps at most one decimal`() {
        assertEquals("23.1", ShiftCalculator.formatHoursShort(23.07))
        assertEquals("23", ShiftCalculator.formatHoursShort(23.0))
        assertEquals("6.5", ShiftCalculator.formatHoursShort(6.5))
        assertEquals("8", ShiftCalculator.formatHoursShort(7.96))
        assertEquals("0", ShiftCalculator.formatHoursShort(0.0))
    }

    @Test
    fun `pay type parses leniently`() {
        assertEquals(PayType.DAILY, PayType.from("DAILY"))
        assertEquals(PayType.HOURLY, PayType.from("HOURLY"))
        assertEquals(PayType.HOURLY, PayType.from(null))
        assertEquals(PayType.HOURLY, PayType.from("junk"))
    }

    @Test
    fun `hours between start and end, including across midnight`() {
        assertEquals(8.0, ShiftCalculator.hoursBetween(9 * 60, 17 * 60), 0.001)
        assertEquals(7.5, ShiftCalculator.hoursBetween(9 * 60 + 30, 17 * 60), 0.001)
        assertEquals(4.0, ShiftCalculator.hoursBetween(22 * 60, 2 * 60), 0.001)
        assertEquals(24.0, ShiftCalculator.hoursBetween(8 * 60, 8 * 60), 0.001)
    }

    @Test
    fun `clock parsing and formatting`() {
        assertEquals(9 * 60 + 30, ShiftCalculator.parseClock("9:30"))
        assertEquals(9 * 60 + 30, ShiftCalculator.parseClock("09.30"))
        assertEquals(17 * 60, ShiftCalculator.parseClock("17"))
        assertNull(ShiftCalculator.parseClock("24:00"))
        assertNull(ShiftCalculator.parseClock("9:60"))
        assertNull(ShiftCalculator.parseClock("abc"))
        assertEquals("08:05", ShiftCalculator.formatClock(8 * 60 + 5))
        assertEquals("17:30", ShiftCalculator.formatClock(17 * 60 + 30))
    }

    @Test
    fun `typed times without a separator are read as hours and minutes`() {
        assertEquals(17 * 60 + 30, ShiftCalculator.parseClock("1730"))
        assertEquals(9 * 60 + 30, ShiftCalculator.parseClock("930"))
        assertEquals(9 * 60 + 30, ShiftCalculator.parseClock("0930"))
        assertEquals(9 * 60, ShiftCalculator.parseClock("9"))
        assertEquals(0, ShiftCalculator.parseClock("000"))
        assertEquals(17 * 60 + 30, ShiftCalculator.parseClock("17 30"))
        assertNull(ShiftCalculator.parseClock("2400"))
        assertNull(ShiftCalculator.parseClock("1760"))
        assertNull(ShiftCalculator.parseClock("12345"))
        assertNull(ShiftCalculator.parseClock("-9"))
        assertNull(ShiftCalculator.parseClock("+9"))
        assertNull(ShiftCalculator.parseClock(""))
        assertNull(ShiftCalculator.parseClock("9:30:00"))
    }

    @Test
    fun `every accepted spelling of a time normalises to HH mm`() {
        assertEquals("17:30", ShiftCalculator.normalizeClock("1730"))
        assertEquals("09:30", ShiftCalculator.normalizeClock("930"))
        assertEquals("09:00", ShiftCalculator.normalizeClock("9"))
        assertEquals("17:30", ShiftCalculator.normalizeClock("17.30"))
        assertEquals("17:30", ShiftCalculator.normalizeClock("17:30"))
        assertEquals("17:30", ShiftCalculator.normalizeClock(" 17:30 "))
        assertEquals("09:05", ShiftCalculator.normalizeClock("9:5"))
        assertNull(ShiftCalculator.normalizeClock("25"))
        assertNull(ShiftCalculator.normalizeClock("שעה"))
    }

    @Test
    fun `hours formatting drops trailing zeros`() {
        assertEquals("6", ShiftCalculator.formatHours(6.0))
        assertEquals("6.5", ShiftCalculator.formatHours(6.5))
        assertEquals("7.25", ShiftCalculator.formatHours(7.25))
        assertEquals("0.05", ShiftCalculator.formatHours(0.05))
    }
}
