package com.tamir.drift.domain.search

import com.tamir.drift.domain.model.TxnSummary

/**
 * The search of the expenses list: free text over the merchant, the note and the raw captured
 * text, and — when the query is a number — the amount ("214" finds 214 ₪ and 214.50 ₪).
 */
object TxSearch {

    private val numeric = Regex("""^\d+(\.\d{0,2})?$""")

    /** The query as a plain number ("1,840 ₪" → "1840"), or null when it is not one. */
    fun amountQuery(query: String): String? {
        val s = query.trim().replace("₪", "").replace(",", "").replace(" ", "")
        return s.takeIf { numeric.matches(it) }
    }

    /**
     * A whole number matches an amount with exactly those shekels (any agorot): "214" finds 214
     * and 214.50, not 2,140. With a decimal point the amount has to start with what was typed:
     * "214.5" finds 214.50.
     */
    fun amountMatches(query: String, agorot: Long): Boolean {
        val q = amountQuery(query) ?: return false
        if (agorot < 0L) return false
        val whole = (agorot / 100).toString()
        if ('.' !in q) return q.trimStart('0').ifEmpty { "0" } == whole
        val cents = agorot % 100
        val exact = whole + "." + (if (cents < 10) "0$cents" else "$cents")
        return q.substringBefore('.').trimStart('0').ifEmpty { "0" } == whole && exact.startsWith(whole + "." + q.substringAfter('.'))
    }

    /** True when the row answers the query (an empty query matches everything). */
    fun matches(
        query: String,
        merchant: String,
        note: String?,
        rawText: String?,
        amountFullAgorot: Long,
        amountMineAgorot: Long,
    ): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return merchant.lowercase().contains(q) ||
            (note?.lowercase()?.contains(q) ?: false) ||
            (rawText?.lowercase()?.contains(q) ?: false) ||
            amountMatches(q, amountFullAgorot) ||
            amountMatches(q, amountMineAgorot)
    }

    /**
     * The line above a filtered list: how many expenses, their net total (my share, refunds
     * subtracted; transfers add nothing) and the average per expense.
     */
    data class Summary(val count: Int, val total: Double) {
        val average: Double get() = if (count > 0) total / count else 0.0
    }

    fun summarize(rows: List<TxnSummary>): Summary = Summary(rows.count { it.isExpense }, rows.sumOf { it.net })
}
