package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GooglePayNotificationParserTest {

    private val parser = GooglePayNotificationParser()

    @Test
    fun `title is merchant and body has amount and card`() {
        val r = parser.parse("שופרסל דיל\n₪45.90 עם Visa •••• 1234")
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(45.90, r.amount!!, 0.001)
        assertEquals("שופרסל דיל", r.merchant)
        assertEquals("1234", r.last4)
    }

    @Test
    fun `english template`() {
        val r = parser.parse("Wolt\n₪89.00 with Mastercard •••• 5678")
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(89.0, r.amount!!, 0.001)
        assertEquals("Wolt", r.merchant)
        assertEquals("5678", r.last4)
    }

    @Test
    fun `single line hebrew paid at`() {
        val r = parser.parse("שילמת ₪45.90 ב-ארומה")
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals("ארומה", r.merchant)
    }

    @Test
    fun `single line english paid at`() {
        val r = parser.parse("You paid ₪12.50 at Aroma Espresso Bar")
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(12.5, r.amount!!, 0.001)
        assertEquals("Aroma Espresso Bar", r.merchant)
    }

    @Test
    fun `generic title is not treated as merchant`() {
        val r = parser.parse("Google Pay\n₪30.00 with Visa •••• 1111")
        assertEquals(ParseStatus.AMOUNT_ONLY, r.status)
        assertEquals(30.0, r.amount!!, 0.001)
        assertNull(r.merchant)
    }

    @Test
    fun `no amount is unparsed`() {
        val r = parser.parse("Google Wallet\nYour card was added")
        assertEquals(ParseStatus.UNPARSED, r.status)
    }
}
