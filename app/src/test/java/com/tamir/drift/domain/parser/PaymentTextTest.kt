package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.UserPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentTextTest {

    @Test
    fun `texts without money are not payments`() {
        assertFalse(PaymentText.looksLikePayment("הכרטיס שלך נוסף ל-Google Wallet"))
        assertFalse(PaymentText.looksLikePayment("Your card is ready to use"))
        assertFalse(PaymentText.looksLikePayment("מבצע חדש מחכה לך באפליקציה"))
        // A card's last digits or a link alone say nothing about money.
        assertFalse(PaymentText.looksLikePayment("הכרטיס המסתיים ב-1234 מוכן לשימוש"))
        assertFalse(PaymentText.looksLikePayment("לפרטים נוספים https://example.com/offer/12.90"))
        assertFalse(PaymentText.looksLikePayment(""))
    }

    @Test
    fun `texts that speak of money are kept for the user to complete`() {
        // A price-like number without any currency sign.
        assertTrue(PaymentText.looksLikePayment("Paid 45.90 at Aroma"))
        // A currency sign the amount parser could not attach to a number.
        assertTrue(PaymentText.looksLikePayment("עסקה בכרטיס 1234 אושרה, הסכום בש\"ח יעודכן בהמשך"))
        // A payment word next to a number.
        assertTrue(PaymentText.looksLikePayment("חיוב חדש בכרטיס 1234"))
        assertTrue(PaymentText.looksLikePayment("Transaction 5567 approved"))
    }

    @Test
    fun `a saved pattern keeps the currency written next to the amount`() {
        val sample = "Paid \$12.99 at Steam"
        val clean = TextNormalizer.normalize(sample)
        val start = clean.indexOf("12.99")
        val regex = PatternBuilder.build(sample, start until start + 5, null)
        val matcher = UserPatternMatcher(listOf(UserPattern(1, regex, TransactionSource.GPAY)))
        val foreign = matcher.parse("Paid \$7.50 at Steam", TransactionSource.GPAY)!!
        assertEquals(7.5, foreign.amount!!, 0.001)
        assertEquals("USD", foreign.currency)
        assertTrue(foreign.isForeign)

        val shekelSample = "שילמת 45.90 ₪ בארומה"
        val shekelClean = TextNormalizer.normalize(shekelSample)
        val s = shekelClean.indexOf("45.90")
        val shekelMatcher = UserPatternMatcher(listOf(UserPattern(2, PatternBuilder.build(shekelSample, s until s + 5, null), TransactionSource.GPAY)))
        assertEquals("ILS", shekelMatcher.parse("שילמת 12.00 ₪ בארומה", TransactionSource.GPAY)!!.currency)
    }

    @Test
    fun `a marked piece is found at its own occurrence, not at the first one`() {
        // "12" is both the day of the date and the amount.
        val original = "ביום 12/11 חויבת ב 12 ₪ אצל קפה"
        val normalized = TextNormalizer.normalize(original)
        val amountAt = original.lastIndexOf("12")
        assertEquals(1, MarkLocator.occurrencesBefore(original, "12", amountAt))
        assertEquals(normalized.lastIndexOf("12"), MarkLocator.locate(original, normalized, "12", amountAt))
        // The first occurrence is still found when that is the marked one.
        assertEquals(normalized.indexOf("12"), MarkLocator.locate(original, normalized, "12", original.indexOf("12")))
        // Unknown position: the first occurrence. Missing piece: -1.
        assertEquals(normalized.indexOf("12"), MarkLocator.locate(original, normalized, "12", -1))
        assertEquals(-1, MarkLocator.locate(original, normalized, "99", 0))
        assertEquals(-1, MarkLocator.nthIndexOf("abc", "b", 1))
        assertEquals(1, MarkLocator.nthIndexOf("abc", "b", 0))
        // Collapsed spaces move the position but not the occurrence.
        val spaced = "סכום   12   ועוד 12"
        val spacedClean = TextNormalizer.normalize(spaced)
        assertEquals(spacedClean.lastIndexOf("12"), MarkLocator.locate(spaced, spacedClean, "12", spaced.lastIndexOf("12")))
    }
}
