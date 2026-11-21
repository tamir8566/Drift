package com.tamir.drift.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxRatesTest {

    private val rates = FxRates(usdAgorot = 3_70L, eurAgorot = 4_00L, gbpAgorot = 4_70L)

    @Test
    fun `estimate in agorot per currency`() {
        assertEquals(48_06L, rates.estimateAgorot(12.99, "USD")) // 12.99 × 3.70 = 48.063
        assertEquals(36_00L, rates.estimateAgorot(9.0, "eur"))
        assertEquals(23_50L, rates.estimateAgorot(5.0, "GBP"))
    }

    @Test
    fun `no estimate for shekels, unknown currency, zero rate or zero amount`() {
        assertNull(rates.estimateAgorot(10.0, "ILS"))
        assertNull(rates.estimateAgorot(10.0, "JPY"))
        assertNull(rates.estimateAgorot(10.0, null))
        assertNull(rates.copy(usdAgorot = 0L).estimateAgorot(10.0, "USD"))
        assertNull(rates.estimateAgorot(0.0, "USD"))
    }

    @Test
    fun `currency helpers`() {
        assertTrue(Currencies.isForeign("USD"))
        assertTrue(Currencies.isForeign("eur"))
        assertFalse(Currencies.isForeign("ILS"))
        assertFalse(Currencies.isForeign(null))
        assertEquals("$", Currencies.symbol("USD"))
        assertEquals("€", Currencies.symbol("EUR"))
        assertEquals("£", Currencies.symbol("GBP"))
        assertEquals(1299L, Currencies.toMinor(12.99))
    }
}
