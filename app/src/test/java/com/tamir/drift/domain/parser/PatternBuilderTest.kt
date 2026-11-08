package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.UserPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PatternBuilderTest {

    @Test
    fun `pattern built from a sample matches a sibling message`() {
        val sample = "חיוב בכרטיס 4321: 37.50 שח ב קפה גרג 05/11"
        val clean = TextNormalizer.normalize(sample)
        val a = clean.indexOf("37.50"); val m = clean.indexOf("קפה גרג")
        val regex = PatternBuilder.build(clean, a until a + 5, m until m + 7)
        val matcher = UserPatternMatcher(listOf(UserPattern(1, regex, TransactionSource.SMS)))
        val r = matcher.parse("חיוב בכרטיס 9999: 1,204.00 שח ב שופרסל דיל 06/11", TransactionSource.SMS)
        assertNotNull(r)
        assertEquals(1204.0, r!!.amount!!, 0.001)
        assertEquals("שופרסל דיל", r.merchant)
        assertEquals(ParseStatus.PARSED, r.status)
    }

    @Test
    fun `user pattern takes precedence in the facade`() {
        val sample = "XYZ 10 ILS at Foo"
        val a = sample.indexOf("10"); val m = sample.indexOf("Foo")
        val regex = PatternBuilder.build(sample, a until a + 2, m until m + 3)
        val parser = TransactionTextParser(listOf(UserPattern(7, regex, null)))
        val r = parser.parse("XYZ 25 ILS at Bar", TransactionSource.GPAY)
        assertEquals("user-pattern#7", r.parserName)
        assertEquals("Bar", r.merchant)
    }

    @Test
    fun `pattern for another source is skipped`() {
        val regex = PatternBuilder.build("A 5 B", 2 until 3, null)
        val matcher = UserPatternMatcher(listOf(UserPattern(1, regex, TransactionSource.GPAY)))
        assertEquals(null, matcher.parse("A 9 B", TransactionSource.SMS))
    }
}
