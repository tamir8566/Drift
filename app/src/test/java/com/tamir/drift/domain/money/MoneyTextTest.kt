package com.tamir.drift.domain.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTextTest {

    @Test
    fun `positive numbers are grouped`() {
        assertEquals("0", MoneyText.signed(0))
        assertEquals("950", MoneyText.signed(950))
        assertEquals("1,200", MoneyText.signed(1200))
        assertEquals("1,234,567", MoneyText.signed(1234567))
    }

    @Test
    fun `negative numbers get a left-to-right mark and a real minus before the digits`() {
        assertEquals("‎−1,200", MoneyText.signed(-1200))
        assertEquals("‎−5", MoneyText.signed(-5))
    }

    @Test
    fun `signed percent`() {
        assertEquals("43%", MoneyText.signedPercent(43))
        assertEquals("0%", MoneyText.signedPercent(0))
        assertEquals("‎−12%", MoneyText.signedPercent(-12))
    }
}
