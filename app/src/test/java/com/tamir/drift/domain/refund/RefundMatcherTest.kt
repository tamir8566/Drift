package com.tamir.drift.domain.refund

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.model.TxnSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefundMatcherTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private var nextId = 1L

    private fun tx(merchant: String, amount: Double, daysAgo: Int, mine: Double = amount, cat: String = CategoryKeys.CLOTHING, kind: TxnKind = TxnKind.EXPENSE) =
        TxnSummary(nextId++, amount, mine, merchant, cat, now - daysAgo * day, TransactionSource.SMS, isShared = mine != amount, kind = kind)

    @Test
    fun `same merchant key, amount at least the refund, within ninety days`() {
        val zara = tx("ZARA 123", 300.0, 10)
        val found = RefundMatcher.findOriginal("Zara", 120.0, now, listOf(tx("H&M", 300.0, 5), zara))
        assertEquals(zara.id, found?.id)
    }

    @Test
    fun `the same amount is preferred, then the most recent`() {
        val exact = tx("zara", 120.0, 40)
        val recent = tx("zara", 500.0, 2)
        assertEquals(exact.id, RefundMatcher.findOriginal("zara", 120.0, now, listOf(recent, exact))?.id)
        val older = tx("zara", 400.0, 30)
        assertEquals(recent.id, RefundMatcher.findOriginal("zara", 99.0, now, listOf(older, recent))?.id)
    }

    @Test
    fun `no match`() {
        assertNull(RefundMatcher.findOriginal("zara", 120.0, now, listOf(tx("zara", 100.0, 3))))            // smaller than the refund
        assertNull(RefundMatcher.findOriginal("zara", 120.0, now, listOf(tx("zara", 300.0, 91))))           // too old
        assertNull(RefundMatcher.findOriginal("zara", 120.0, now, listOf(tx("zara home", 300.0, 3))))       // another merchant
        assertNull(RefundMatcher.findOriginal("zara", 120.0, now, listOf(tx("zara", 300.0, 3, kind = TxnKind.REFUND))))
        assertNull(RefundMatcher.findOriginal("zara", 120.0, now, listOf(tx("zara", 300.0, 3, kind = TxnKind.TRANSFER))))
        assertNull(RefundMatcher.findOriginal("", 120.0, now, listOf(tx("", 300.0, 3))))
        assertNull(RefundMatcher.findOriginal(null, 120.0, now, emptyList()))
    }

    @Test
    fun `the refund inherits the share ratio of the purchase`() {
        val shared = tx("wolt", 180.0, 4, mine = 60.0)
        assertEquals(30.0, RefundMatcher.myShareOf(90.0, shared), 0.001)
        val mineOnly = tx("wolt", 180.0, 4)
        assertEquals(90.0, RefundMatcher.myShareOf(90.0, mineOnly), 0.001)
    }
}
