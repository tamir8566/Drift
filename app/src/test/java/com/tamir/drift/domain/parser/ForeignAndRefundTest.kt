package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignAndRefundTest {

    private val parser = TransactionTextParser()

    private fun found(text: String) = AmountParser.find(text) ?: error("no amount in: $text")

    // ---- foreign currency ----

    @Test
    fun `a cue followed by a dollar word is dollars, not shekels`() {
        val f = found("חייב את כרטיסך 1234 בסך 12.99 דולר")
        assertEquals(12.99, f.amount, 0.001)
        assertEquals("USD", f.currency)
        assertTrue(f.isForeign)
    }

    @Test
    fun `dollar sign and codes`() {
        assertEquals("USD", found("$12.99").currency)
        assertEquals(12.99, found("$12.99").amount, 0.001)
        assertEquals("USD", found("12.99$").currency)
        assertEquals("USD", found("USD 12.99").currency)
        assertEquals("USD", found("12.99 usd").currency)
        assertEquals("USD", found("סכום: 1,250.50 דולרים").currency)
        assertEquals(1250.5, found("סכום: 1,250.50 דולרים").amount, 0.001)
    }

    @Test
    fun `euro and pound`() {
        assertEquals("EUR", found("€9").currency)
        assertEquals("EUR", found("9.50 EUR").currency)
        assertEquals("EUR", found("בסך 9 אירו").currency)
        assertEquals("EUR", found("בסך 9 יורו").currency)
        assertEquals("GBP", found("£5").currency)
        assertEquals("GBP", found("5 GBP").currency)
    }

    @Test
    fun `shekels stay shekels`() {
        assertEquals("ILS", found("בסך 216.79 שח").currency)
        assertEquals("ILS", found("₪45.90").currency)
        assertEquals("ILS", found("בסך 89.90").currency)
        assertFalse(found("45.90 ₪").isForeign)
    }

    @Test
    fun `a marker glued to a longer word is not a currency`() {
        assertNull(AmountParser.find("5 אירועים השבוע"))
        assertNull(AmountParser.find("12 שחקנים"))
        assertNull(AmountParser.find("3 דולרית"))
    }

    @Test
    fun `max sms in dollars keeps merchant, card and date`() {
        val text = """
            היי, ביקשת שנעדכן אותך על כל עסקת אינטרנט: היום 05/11 בית עסק
            AMAZON MKTPLACE
            חייב את כרטיסך 1234 בסך 12.10 דולר
            כדאי לעקוב אחר החיובים כאן:
            https://link.max.co.il/0000000
        """.trimIndent()
        val r = parser.parse(text, TransactionSource.SMS)
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(12.10, r.amount!!, 0.001)
        assertEquals("USD", r.currency)
        assertTrue(r.isForeign)
        assertEquals("AMAZON MKTPLACE", r.merchant)
        assertEquals("1234", r.last4)
        assertEquals(5, r.date?.day)
        assertEquals(11, r.date?.month)
        assertEquals(TxnKind.EXPENSE, r.kind)
    }

    @Test
    fun `a foreign amount is not read as a date`() {
        val r = MaxSmsParser().parse("עסקה בסך 12.10 דולר ב-AMAZON אושרה")
        assertEquals(12.10, r.amount!!, 0.001)
        assertEquals("USD", r.currency)
        assertNull(r.date)
        assertEquals("AMAZON", r.merchant)
    }

    @Test
    fun `google pay notification in a foreign currency`() {
        val r = parser.parse("Steam\n$9.99 with Visa •••• 1234", TransactionSource.GPAY)
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(9.99, r.amount!!, 0.001)
        assertEquals("USD", r.currency)
        assertEquals("Steam", r.merchant)
        val line = parser.parse("€12.00 with Visa •••• 1234", TransactionSource.GPAY)
        assertEquals("EUR", line.currency)
        assertNull(line.merchant)
    }

    @Test
    fun `shekel messages are not foreign`() {
        val r = parser.parse("שופרסל דיל\n₪45.90 עם Visa •••• 1234", TransactionSource.GPAY)
        assertEquals("ILS", r.currency)
        assertFalse(r.isForeign)
    }

    // ---- refunds ----

    @Test
    fun `refund words`() {
        assertTrue(RefundDetector.isRefund("בוצע זיכוי בכרטיסך"))
        assertTrue(RefundDetector.isRefund("כרטיסך זוכה בסך 50 שח"))
        assertTrue(RefundDetector.isRefund("בית עסק זארה זיכה את כרטיסך"))
        assertTrue(RefundDetector.isRefund("ביטול  עסקה בסך 50 שח"))
        assertTrue(RefundDetector.isRefund("Refund of ₪50.00 from Zara"))
        assertFalse(RefundDetector.isRefund("בית עסק זארה חייב את כרטיסך 1234 בסך 50 שח"))
        assertFalse(RefundDetector.isRefund("ביטול מנוי"))
    }

    @Test
    fun `max refund sms is a refund with a positive amount`() {
        val text = "היי, היום 05/11 בית עסק זארה זיכה את כרטיסך 1234 בסך 120.00 שח"
        val r = parser.parse(text, TransactionSource.SMS)
        assertEquals(TxnKind.REFUND, r.kind)
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(120.0, r.amount!!, 0.001)
        assertEquals("זארה", r.merchant)
        assertEquals("ILS", r.currency)
    }

    @Test
    fun `google pay refund is a refund`() {
        val r = parser.parse("Zara\nRefund ₪120.00 to Visa •••• 1234", TransactionSource.GPAY)
        assertEquals(TxnKind.REFUND, r.kind)
        assertEquals(120.0, r.amount!!, 0.001)
    }

    @Test
    fun `a refund text without an amount is still a refund`() {
        val r = parser.parse("בוצע זיכוי בכרטיסך", TransactionSource.SMS)
        assertEquals(ParseStatus.UNPARSED, r.status)
        assertEquals(TxnKind.REFUND, r.kind)
    }

    @Test
    fun `an ordinary purchase is an expense`() {
        val r = parser.parse("היום 05/11 בית עסק זארה חייב את כרטיסך 1234 בסך 120.00 שח", TransactionSource.SMS)
        assertEquals(TxnKind.EXPENSE, r.kind)
    }

    @Test
    fun `a refund in a foreign currency keeps both`() {
        val r = parser.parse("זיכוי בסך 12.99 דולר ב-AMAZON", TransactionSource.SMS)
        assertEquals(TxnKind.REFUND, r.kind)
        assertEquals("USD", r.currency)
        assertEquals(12.99, r.amount!!, 0.001)
    }
}
