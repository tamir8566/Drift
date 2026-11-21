package com.tamir.drift.domain.refund

import com.tamir.drift.domain.classify.MerchantKey
import com.tamir.drift.domain.model.TxnSummary
import kotlin.math.abs

/**
 * Finds the purchase a captured refund belongs to, so the refund inherits its category and its
 * "with friends" share: same merchant key, an expense of at least the refunded amount, made up
 * to [WINDOW_DAYS] days before the refund. The same amount is preferred, then the most recent.
 */
object RefundMatcher {
    const val WINDOW_DAYS = 90L
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val EPSILON = 0.005

    fun findOriginal(merchant: String?, refundAmount: Double, refundMillis: Long, existing: List<TxnSummary>): TxnSummary? {
        if (merchant.isNullOrBlank() || refundAmount <= 0.0) return null
        val key = MerchantKey.normalize(merchant)
        if (key.isEmpty()) return null
        val from = refundMillis - WINDOW_DAYS * DAY_MILLIS
        // A refund message can arrive with the purchase's own date; allow one day of slack forward.
        val to = refundMillis + DAY_MILLIS
        return existing
            .filter { it.isExpense && it.timestamp in from..to }
            .filter { it.amountTotal + EPSILON >= refundAmount }
            .filter { MerchantKey.normalize(it.merchant) == key }
            .sortedWith(
                compareBy<TxnSummary> { if (abs(it.amountTotal - refundAmount) <= EPSILON) 0 else 1 }
                    .thenByDescending { it.timestamp },
            )
            .firstOrNull()
    }

    /** My part of the refund: the same ratio as my part of the original purchase. */
    fun myShareOf(refundAmount: Double, original: TxnSummary): Double =
        if (original.amountTotal <= 0.0) refundAmount
        else refundAmount * (original.myShare / original.amountTotal).coerceIn(0.0, 1.0)
}
