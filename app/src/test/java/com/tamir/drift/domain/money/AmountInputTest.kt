package com.tamir.drift.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {

    private fun p(s: String) = AmountInput.parse(s)

    @Test
    fun `comma with one or two digits at the end is a decimal separator`() {
        assertEquals(12.5, p("12,5")!!, 1e-9)
        assertEquals(12.5, p("12,50")!!, 1e-9)
        assertEquals(0.5, p("0,5")!!, 1e-9)
        assertEquals(1234.5, p("1,234,5")!!, 1e-9)
        assertEquals(1234.56, p("1,234,56")!!, 1e-9)
    }

    @Test
    fun `any other comma is a thousands separator`() {
        assertEquals(1200.0, p("1,200")!!, 1e-9)
        assertEquals(1234567.0, p("1,234,567")!!, 1e-9)
        assertEquals(12345.0, p("1,2345")!!, 1e-9)
        assertEquals(12.0, p("12,")!!, 1e-9) // mid-typing
    }

    @Test
    fun `with a dot the dot is the decimal separator`() {
        assertEquals(1234.5, p("1,234.5")!!, 1e-9)
        assertEquals(12.5, p("12.5")!!, 1e-9)
        assertEquals(125.3, p("12,5.3")!!, 1e-9)
        assertEquals(5.0, p("5.")!!, 1e-9)
        assertEquals(0.5, p(".5")!!, 1e-9)
    }

    @Test
    fun `plain numbers, spaces and the shekel sign`() {
        assertEquals(40.0, p("40")!!, 1e-9)
        assertEquals(40.0, p(" 40 ₪ ")!!, 1e-9)
        assertEquals(1200.0, p("1 200")!!, 1e-9)
        assertEquals(0.0, p("0")!!, 1e-9)
    }

    @Test
    fun `not a number`() {
        assertNull(p(""))
        assertNull(p("   "))
        assertNull(p("abc"))
        assertNull(p("-5"))
        assertNull(p("1.2.3"))
        assertNull(p("NaN"))
        assertNull(p(","))
        assertNull(p("."))
    }
}
