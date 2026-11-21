package com.tamir.drift.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarValueTest {

    @Test
    fun `inside the range the bar and the percent are the same value`() {
        val b = BarValue(430.0, 1000.0)
        assertEquals(0.43, b.fraction.toDouble(), 0.0001)
        assertEquals(43, b.percent)
        assertFalse(b.isOver)
        assertFalse(b.isNegative)
    }

    @Test
    fun `above the reference the bar is full and the text keeps the real percent`() {
        val b = BarValue(1300.0, 1000.0)
        assertEquals(1.0, b.fraction.toDouble(), 0.0001)
        assertEquals(130, b.percent)
        assertTrue(b.isOver)
    }

    @Test
    fun `exactly at the reference is full but not over`() {
        val b = BarValue.of(1000L, 1000L)
        assertEquals(1.0, b.fraction.toDouble(), 0.0001)
        assertEquals(100, b.percent)
        assertFalse(b.isOver)
    }

    @Test
    fun `negative draws empty and the text says so`() {
        val b = BarValue(-250.0, 1000.0)
        assertEquals(0.0, b.fraction.toDouble(), 0.0001)
        assertEquals(-25, b.percent)
        assertTrue(b.isNegative)
        assertFalse(b.isOver)
    }

    @Test
    fun `no reference - any positive value fills the bar`() {
        assertEquals(1f, BarValue(50.0, 0.0).fraction)
        assertTrue(BarValue(50.0, 0.0).isOver)
        assertEquals(0f, BarValue(0.0, 0.0).fraction)
        assertEquals(0, BarValue(0.0, 0.0).percent)
        assertEquals(0f, BarValue(Double.NaN, 100.0).fraction)
    }

    @Test
    fun `plain fraction`() {
        assertEquals(0.25, BarValue.ofFraction(0.25).fraction.toDouble(), 0.0001)
        assertEquals(25, BarValue.ofFraction(0.25).percent)
    }
}
