package com.tamir.drift.domain.parser

/**
 * Finds a shekel amount inside free text.
 *
 * Handles: `₪45.90`, `45.90 ₪`, `1,234.50 ש"ח`, `ILS 12`, `NIS 12.5`, `בסך 89.90`,
 * thousands separators and 1–2 decimal digits. Hebrew gershayim variants (״ ' ") are
 * all accepted.
 */
object AmountParser {

    /** A number with optional thousands separators and optional decimals. */
    private const val NUMBER = """(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+,\d{2}|\d+(?:\.\d{1,2})?)"""

    /** Shekel markers. */
    private const val CURRENCY = """(?:₪|ש["״'']ח|שח|ILS|NIS|ש"ח)"""

    /** Hebrew "in the amount of" cues that often precede an amount without a symbol. */
    private const val AMOUNT_CUE = """(?:בסך|ע["״']?ס|סכום:?|סך של|בסכום של|הסכום)"""

    private val currencyBefore = Regex("""$CURRENCY\s*$NUMBER""", RegexOption.IGNORE_CASE)
    private val currencyAfter = Regex("""$NUMBER\s*$CURRENCY""", RegexOption.IGNORE_CASE)
    private val cueBefore = Regex("""$AMOUNT_CUE\s*$NUMBER""", RegexOption.IGNORE_CASE)

    data class Found(val amount: Double, val currency: String, val range: IntRange)

    /** Returns the first amount that is adjacent to a currency marker, or `null`. */
    fun find(text: String): Found? {
        val clean = TextNormalizer.normalize(text)
        val candidates = buildList {
            currencyBefore.find(clean)?.let { add(Found(parseNumber(it.groupValues[1]), "ILS", it.range)) }
            currencyAfter.find(clean)?.let { add(Found(parseNumber(it.groupValues[1]), "ILS", it.range)) }
            cueBefore.find(clean)?.let { add(Found(parseNumber(it.groupValues[1]), "ILS", it.range)) }
        }
        return candidates.filter { it.amount > 0.0 }.minByOrNull { it.range.first }
    }

    /** Parses `1,234.50` → 1234.5. Tolerates a decimal comma when there is no dot (`45,90`). */
    fun parseNumber(raw: String): Double {
        val s = raw.trim()
        val normalized = when {
            s.contains('.') -> s.replace(",", "")
            // "45,90" (two digits after a single comma) is a decimal comma.
            Regex("""^\d+,\d{2}$""").matches(s) -> s.replace(',', '.')
            else -> s.replace(",", "")
        }
        return normalized.toDoubleOrNull() ?: 0.0
    }
}
