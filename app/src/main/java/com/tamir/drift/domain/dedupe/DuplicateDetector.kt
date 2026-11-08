package com.tamir.drift.domain.dedupe

import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import kotlin.math.abs

/**
 * Detects when a freshly captured transaction is the same purchase as one already stored
 * (e.g. Google Pay notification + max SMS for the same tap).
 *
 * Rule: same amount (to the agora) within ±[windowMillis], and either a different capture
 * source, or the same source with the same merchant (a re-posted notification).
 */
class DuplicateDetector(
    private val windowMillis: Long = DEFAULT_WINDOW_MINUTES * 60_000L,
) {

    data class Candidate(
        val amountTotal: Double,
        val timestamp: Long,
        val source: TransactionSource,
        val merchant: String?,
    )

    /** Returns the existing transaction that [candidate] duplicates, or null. */
    fun findDuplicate(candidate: Candidate, existing: List<TxnSummary>): TxnSummary? =
        existing
            .filter { isDuplicate(candidate, it) }
            .minByOrNull { abs(it.timestamp - candidate.timestamp) }

    fun isDuplicate(candidate: Candidate, stored: TxnSummary): Boolean {
        if (stored.source == TransactionSource.MANUAL) return false
        if (abs(stored.amountTotal - candidate.amountTotal) > AMOUNT_EPSILON) return false
        if (abs(stored.timestamp - candidate.timestamp) > windowMillis) return false
        if (stored.source != candidate.source) return true
        val m1 = candidate.merchant?.trim()?.lowercase()
        val m2 = stored.merchant.trim().lowercase()
        return m1 != null && m1.isNotEmpty() && m1 == m2
    }

    companion object {
        const val DEFAULT_WINDOW_MINUTES = 10
        private const val AMOUNT_EPSILON = 0.005
    }
}
