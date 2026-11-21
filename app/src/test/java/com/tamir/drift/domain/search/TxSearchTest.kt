package com.tamir.drift.domain.search

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.model.TxnSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxSearchTest {

    private var nextId = 1L
    private fun tx(amount: Double, mine: Double = amount, kind: TxnKind = TxnKind.EXPENSE) =
        TxnSummary(nextId++, amount, mine, "m", CategoryKeys.OTHER, 0L, TransactionSource.SMS, isShared = mine != amount, kind = kind)

    @Test
    fun `a number is recognised as an amount query`() {
        assertEquals("214", TxSearch.amountQuery("214"))
        assertEquals("1840", TxSearch.amountQuery("1,840 ₪"))
        assertEquals("214.5", TxSearch.amountQuery(" 214.5 "))
        assertNull(TxSearch.amountQuery("וולט"))
        assertNull(TxSearch.amountQuery("21a"))
        assertNull(TxSearch.amountQuery(""))
    }

    @Test
    fun `a whole number finds that many shekels with any agorot`() {
        assertTrue(TxSearch.amountMatches("214", 214_00L))
        assertTrue(TxSearch.amountMatches("214", 214_50L))
        assertFalse(TxSearch.amountMatches("214", 2_140_00L))
        assertFalse(TxSearch.amountMatches("214", 21_40L))
        assertFalse(TxSearch.amountMatches("21", 214_00L))
        assertTrue(TxSearch.amountMatches("1,840", 1_840_00L))
    }

    @Test
    fun `with a decimal point the amount starts with what was typed`() {
        assertTrue(TxSearch.amountMatches("214.5", 214_50L))
        assertTrue(TxSearch.amountMatches("214.", 214_79L))
        assertTrue(TxSearch.amountMatches("216.79", 216_79L))
        assertFalse(TxSearch.amountMatches("214.5", 214_05L))
        assertFalse(TxSearch.amountMatches("214.5", 2_145_00L))
    }

    @Test
    fun `text matches the merchant the note and the raw text, numbers also the amounts`() {
        assertTrue(TxSearch.matches("", "x", null, null, 0L, 0L))
        assertTrue(TxSearch.matches("WOLT", "Wolt Tel Aviv", null, null, 50_00L, 50_00L))
        assertTrue(TxSearch.matches("יום הולדת", "מסעדה", "יום הולדת של דני", null, 50_00L, 50_00L))
        assertTrue(TxSearch.matches("כרטיסך", "", null, "חייב את כרטיסך 1234", 50_00L, 50_00L))
        assertTrue(TxSearch.matches("214", "סופר", null, null, 214_00L, 107_00L))
        // My share is searched too.
        assertTrue(TxSearch.matches("107", "סופר", null, null, 214_00L, 107_00L))
        assertFalse(TxSearch.matches("215", "סופר", null, null, 214_00L, 107_00L))
        // A number inside the merchant's name still matches as text.
        assertTrue(TxSearch.matches("123", "שופרסל 123", null, null, 80_00L, 80_00L))
    }

    @Test
    fun `summary counts expenses, subtracts refunds and ignores transfers`() {
        val rows = listOf(
            tx(100.0),
            tx(200.0, mine = 100.0),
            tx(40.0, kind = TxnKind.REFUND),
            tx(999.0, kind = TxnKind.TRANSFER),
        )
        val s = TxSearch.summarize(rows)
        assertEquals(2, s.count)
        assertEquals(160.0, s.total, 0.001)
        assertEquals(80.0, s.average, 0.001)
        val empty = TxSearch.summarize(emptyList())
        assertEquals(0, empty.count)
        assertEquals(0.0, empty.average, 0.001)
    }
}
