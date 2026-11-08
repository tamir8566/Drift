package com.tamir.drift.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    private fun amount(text: String) = AmountParser.find(text)?.amount

    @Test
    fun `symbol before number`() {
        assertEquals(45.90, amount("₪45.90 עם Visa •••• 1234")!!, 0.001)
    }

    @Test
    fun `symbol after number with space`() {
        assertEquals(89.90, amount("בוצעה עסקה ע\"ס 89.90 ₪ ב-WOLT")!!, 0.001)
    }

    @Test
    fun `hebrew shach variants`() {
        assertEquals(120.0, amount("חיוב של 120 ש\"ח אצל רמי לוי")!!, 0.001)
        assertEquals(120.0, amount("חיוב של 120 ש״ח אצל רמי לוי")!!, 0.001)
        assertEquals(120.0, amount("חיוב של 120 שח אצל רמי לוי")!!, 0.001)
    }

    @Test
    fun `thousands separator`() {
        assertEquals(1234.50, amount("1,234.50 ₪ באיקאה")!!, 0.001)
        assertEquals(12000.0, amount("₪12,000")!!, 0.001)
    }

    @Test
    fun `decimal comma`() {
        assertEquals(45.90, amount("45,90 ₪ בשופרסל")!!, 0.001)
    }

    @Test
    fun `ILS and NIS codes`() {
        assertEquals(12.5, amount("NIS 12.5 at Aroma")!!, 0.001)
        assertEquals(30.0, amount("30 ILS")!!, 0.001)
    }

    @Test
    fun `hebrew amount cue without symbol`() {
        assertEquals(55.0, amount("בוצעה עסקה בסך 55 בבית העסק קפה גרג")!!, 0.001)
    }

    @Test
    fun `bidi marks are ignored`() {
        assertEquals(45.90, amount("‏₪‎45.90 עם Visa")!!, 0.001)
    }

    @Test
    fun `no currency means no amount`() {
        assertNull(amount("קוד האימות שלך הוא 123456"))
        assertNull(amount("הפגישה ב-14:30"))
    }

    @Test
    fun `picks the first currency amount not the card digits`() {
        val found = AmountParser.find("בכרטיסך 1234 בוצעה עסקה ע\"ס 89.90 ₪ ב-WOLT")!!
        assertEquals(89.90, found.amount, 0.001)
    }
}
