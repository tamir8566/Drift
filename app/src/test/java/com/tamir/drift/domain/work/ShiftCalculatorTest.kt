package com.tamir.drift.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftCalculatorTest {

    private val rate = 38_00L // אדם מילא, 38 ₪ per hour

    @Test
    fun `regular shift is hours times rate`() {
        assertEquals(304_00L, ShiftCalculator.amount(8.0, rate, false, 1.5))
        assertEquals(247_00L, ShiftCalculator.amount(6.5, rate, false, 1.5))
    }

    @Test
    fun `shabbat shift applies the multiplier`() {
        assertEquals(456_00L, ShiftCalculator.amount(8.0, rate, true, 1.5))
        assertEquals(608_00L, ShiftCalculator.amount(8.0, rate, true, 2.0))
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
        assertEquals(304_00L, ShiftCalculator.resolve(8.0, rate, false, 1.5, null))
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
    fun `hours formatting drops trailing zeros`() {
        assertEquals("6", ShiftCalculator.formatHours(6.0))
        assertEquals("6.5", ShiftCalculator.formatHours(6.5))
        assertEquals("7.25", ShiftCalculator.formatHours(7.25))
        assertEquals("0.05", ShiftCalculator.formatHours(0.05))
    }
}
