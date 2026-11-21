package com.tamir.drift.domain.parser

/**
 * A captured text that speaks of a credit or a cancelled transaction is a refund, never an
 * expense: "זיכוי", "זוכה", "זיכה", "ביטול עסקה", "refund".
 */
object RefundDetector {
    private val marker = Regex("""זיכוי|זוכה|זיכה|ביטול\s+עסקה|refund""", RegexOption.IGNORE_CASE)

    fun isRefund(text: String): Boolean = marker.containsMatchIn(TextNormalizer.normalize(text))
}
