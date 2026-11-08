package com.tamir.drift.domain.dedupe

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateDetectorTest {

    private val detector = DuplicateDetector()
    private val t0 = 1_700_000_000_000L

    private fun stored(amount: Double, at: Long, source: TransactionSource = TransactionSource.GPAY, merchant: String = "וולט") =
        TxnSummary(1, amount, amount, merchant, CategoryKeys.EATING_OUT, at, source)

    @Test
    fun `same amount from the other source within 10 minutes is a duplicate`() {
        val existing = listOf(stored(89.9, t0))
        val c = DuplicateDetector.Candidate(89.9, t0 + 9 * 60_000, TransactionSource.SMS, "WOLT")
        assertEquals(1L, detector.findDuplicate(c, existing)?.id)
    }

    @Test
    fun `outside the window is not a duplicate`() {
        val existing = listOf(stored(89.9, t0))
        val c = DuplicateDetector.Candidate(89.9, t0 + 11 * 60_000, TransactionSource.SMS, "WOLT")
        assertNull(detector.findDuplicate(c, existing))
    }

    @Test
    fun `different amount is not a duplicate`() {
        val existing = listOf(stored(89.9, t0))
        val c = DuplicateDetector.Candidate(90.0, t0, TransactionSource.SMS, "WOLT")
        assertNull(detector.findDuplicate(c, existing))
    }

    @Test
    fun `same source needs the same merchant`() {
        val existing = listOf(stored(50.0, t0, TransactionSource.GPAY, "ארומה"))
        assertNull(detector.findDuplicate(DuplicateDetector.Candidate(50.0, t0 + 60_000, TransactionSource.GPAY, "קפה גרג"), existing))
        assertEquals(1L, detector.findDuplicate(DuplicateDetector.Candidate(50.0, t0 + 60_000, TransactionSource.GPAY, "ארומה"), existing)?.id)
    }

    @Test
    fun `manual entries never absorb captures`() {
        val existing = listOf(stored(50.0, t0, TransactionSource.MANUAL))
        assertNull(detector.findDuplicate(DuplicateDetector.Candidate(50.0, t0, TransactionSource.SMS, null), existing))
    }
}
