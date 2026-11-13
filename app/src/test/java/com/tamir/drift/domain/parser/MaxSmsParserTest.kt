package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MaxSmsParserTest {

    private val parser = MaxSmsParser()

    /** Real max message (card digits replaced by 1234). Primary fixture. */
    private val realMessage = """
        היי, ביקשת שנעדכן אותך על כל עסקת אינטרנט: היום 05/11 בית עסק
        פז אפליקציית יילו
        חייב את כרטיסך 1234 בסך 216.79 שח
        כדאי לעקוב אחר החיובים כאן:
        https://link.max.co.il/5f093cf
    """.trimIndent()

    @Test
    fun `real max internet transaction message`() {
        val r = parser.parse(realMessage)
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(216.79, r.amount!!, 0.001)
        assertEquals("פז אפליקציית יילו", r.merchant)
        assertEquals("1234", r.last4)
        assertNotNull(r.date)
        assertEquals(5, r.date!!.day)
        assertEquals(11, r.date!!.month)
        assertEquals(0, r.date!!.relativeDays)
    }

    @Test
    fun `real message date resolves to today`() {
        val r = parser.parse(realMessage)
        val today = LocalDate.of(2026, 11, 5)
        assertEquals(today, r.date!!.resolve(today))
    }

    @Test
    fun `real message without card digits`() {
        val r = parser.parse(realMessage.replace("כרטיסך 1234", "כרטיסך"))
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(216.79, r.amount!!, 0.001)
        assertEquals("פז אפליקציית יילו", r.merchant)
        assertNull(r.last4)
    }

    @Test
    fun `real message with XXXX placeholder`() {
        val r = parser.parse(realMessage.replace("1234", "XXXX"))
        assertEquals("פז אפליקציית יילו", r.merchant)
        assertNull(r.last4)
    }

    @Test
    fun `variant wording - regular transaction, shekel symbol, gershayim`() {
        val r = parser.parse(
            "היי, ביקשת שנעדכן אותך על כל עסקת אשראי: היום 02/11 בית עסק שופרסל דיל נתניה חייב את כרטיסך 5678 בסך 1,214.50 ש\"ח",
        )
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(1214.50, r.amount!!, 0.001)
        assertEquals("שופרסל דיל נתניה", r.merchant)
        assertEquals("5678", r.last4)
    }

    @Test
    fun `variant - yesterday`() {
        val r = parser.parse("עדכון על חיוב: אתמול בית עסק וולט חייב את כרטיסך 9999 בסך 89 ₪")
        assertEquals("וולט", r.merchant)
        assertEquals(89.0, r.amount!!, 0.001)
        assertEquals(1, r.date!!.relativeDays)
        val today = LocalDate.of(2026, 11, 8)
        assertEquals(LocalDate.of(2026, 11, 7), r.date!!.resolve(today))
    }

    @Test
    fun `plain date in the future rolls back a year`() {
        val r = parser.parse("בית עסק נטפליקס חייב את כרטיסך בסך 54.90 שח בתאריך 28/12")
        assertEquals("נטפליקס", r.merchant)
        val today = LocalDate.of(2026, 11, 8)
        assertEquals(LocalDate.of(2025, 12, 28), r.date!!.resolve(today))
    }

    @Test
    fun `link digits never become the amount`() {
        val r = parser.parse("בית עסק גט חייב את כרטיסך בסך 42 שח\nhttps://link.max.co.il/12345")
        assertEquals(42.0, r.amount!!, 0.001)
        assertEquals("גט", r.merchant)
    }

    @Test
    fun `facade routes SMS to the max parser`() {
        val r = TransactionTextParser().parse(realMessage, TransactionSource.SMS)
        assertEquals("פז אפליקציית יילו", r.merchant)
        assertEquals(216.79, r.amount!!, 0.001)
    }

    // ---- older template shapes still supported ----

    @Test
    fun `template with dash merchant and card digits`() {
        val r = parser.parse("בכרטיסך 1234 בוצעה עסקה ע\"ס 89.90 ₪ ב-WOLT. יתרת מסגרת 5,000 ₪")
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(89.90, r.amount!!, 0.001)
        assertEquals("WOLT", r.merchant)
        assertEquals("1234", r.last4)
    }

    @Test
    fun `template with date terminator`() {
        val r = parser.parse(
            "שלום, בוצעה עסקה בכרטיס מקס המסתיים ב-5678 בסך 45.90 ש\"ח בשופרסל דיל בתאריך 05/11 12:34",
        )
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(45.90, r.amount!!, 0.001)
        assertEquals("שופרסל דיל", r.merchant)
        assertEquals("5678", r.last4)
    }

    @Test
    fun `template with etzel`() {
        val r = parser.parse("max: חיוב של 120 ₪ אצל רמי לוי אושר")
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(120.0, r.amount!!, 0.001)
        assertEquals("רמי לוי", r.merchant)
        assertNull(r.last4)
    }

    @Test
    fun `multi word hebrew merchant with dash`() {
        val r = parser.parse("בוצעה עסקה ב- סופר פארם רעננה ע\"ס 33.5 ₪ בכרטיס *9876")
        assertEquals(ParseStatus.PARSED, r.status)
        assertEquals(33.5, r.amount!!, 0.001)
        assertEquals("סופר פארם רעננה", r.merchant)
        assertEquals("9876", r.last4)
    }

    @Test
    fun `amount only when merchant missing`() {
        val r = parser.parse("בוצע חיוב של 250 ₪ בכרטיסך")
        assertEquals(ParseStatus.AMOUNT_ONLY, r.status)
        assertEquals(250.0, r.amount!!, 0.001)
        assertNull(r.merchant)
    }

    @Test
    fun `unparsed when no amount`() {
        val r = parser.parse("קוד האימות שלך למקס הוא 4411")
        assertEquals(ParseStatus.UNPARSED, r.status)
        assertNull(r.amount)
    }
}
