package com.tamir.drift.domain.parser

import com.tamir.drift.domain.money.Currencies

/**
 * Finds an amount inside free text, together with its currency.
 *
 * Shekels: `₪45.90`, `45.90 ₪`, `1,234.50 ש"ח`, `ILS 12`, `NIS 12.5`, `בסך 89.90`,
 * thousands separators and 1–2 decimal digits. Hebrew gershayim variants (״ ' ") are
 * all accepted.
 *
 * Foreign currency: `$12.99`, `12.99 USD`, `בסך 12.99 דולר`, `€9`, `9 EUR`, `9 אירו` / `יורו`,
 * `£5`, `5 GBP`. A foreign amount is returned in its own currency ([Found.currency] is
 * "USD" / "EUR" / "GBP") and is never read as shekels.
 */
object AmountParser {

    /** A number with optional thousands separators and optional decimals. */
    private const val NUMBER = """(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+,\d{2}|\d+(?:\.\d{1,2})?)"""

    /** Shekel markers. */
    private const val CURRENCY = """(?:₪|ש["״'']ח|שח|ILS|NIS|ש"ח)"""

    /** Hebrew "in the amount of" cues that often precede an amount without a symbol. */
    private const val AMOUNT_CUE = """(?:בסך|ע["״']?ס|סכום:?|סך של|בסכום של|הסכום)"""

    // A normal (escaped) string: "$" must not be read as a template inside the raw strings below.
    private const val DOLLAR = "\\$"

    /** Foreign markers that stand before a number ("$12.99", "USD 12.99"). */
    private const val FOREIGN_BEFORE = "($DOLLAR|USD|€|EUR|£|GBP)"

    /** Foreign markers that stand after a number ("12.99 דולר", "12.99$", "9 אירו"). */
    const val FOREIGN_AFTER_WORDS = """דולר(?:ים)?|אירו|יורו|פאונד|ליש["״'']ט"""
    private const val FOREIGN_AFTER = "($DOLLAR|USD|€|EUR|£|GBP|$FOREIGN_AFTER_WORDS)"

    private val currencyBefore = Regex("""$CURRENCY\s*$NUMBER""", RegexOption.IGNORE_CASE)
    // A marker glued to more letters is part of a word ("5 אירועים", "12 שחקנים"), not a currency.
    private val currencyAfter = Regex("""$NUMBER\s*$CURRENCY(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val cueBefore = Regex("""$AMOUNT_CUE\s*$NUMBER""", RegexOption.IGNORE_CASE)
    private val foreignBefore = Regex(FOREIGN_BEFORE + """\s*""" + NUMBER, RegexOption.IGNORE_CASE)
    private val foreignAfter = Regex(NUMBER + """\s*""" + FOREIGN_AFTER + """(?!\p{L})""", RegexOption.IGNORE_CASE)

    /** [amount] is in [currency] ("ILS", or "USD" / "EUR" / "GBP" for a foreign amount). */
    data class Found(val amount: Double, val currency: String, val range: IntRange) {
        val isForeign: Boolean get() = Currencies.isForeign(currency)
    }

    /**
     * One match. [numberStart] is where the digits begin; [explicit] is false for a bare cue
     * ("בסך 12.99"), which says nothing about the currency.
     */
    private data class Candidate(val amount: Double, val currency: String, val range: IntRange, val numberStart: Int, val explicit: Boolean)

    /** Returns the first amount that is adjacent to a currency marker or an amount cue, or `null`. */
    fun find(text: String): Found? {
        val clean = TextNormalizer.normalize(text)
        val candidates = buildList {
            currencyBefore.find(clean)?.let { add(candidate(it, numberGroup = 1, currency = Currencies.ILS, explicit = true)) }
            currencyAfter.find(clean)?.let { add(candidate(it, numberGroup = 1, currency = Currencies.ILS, explicit = true)) }
            cueBefore.find(clean)?.let { add(candidate(it, numberGroup = 1, currency = Currencies.ILS, explicit = false)) }
            foreignBefore.find(clean)?.let { add(candidate(it, numberGroup = 2, currency = currencyOf(it.groupValues[1]), explicit = true)) }
            foreignAfter.find(clean)?.let { add(candidate(it, numberGroup = 1, currency = currencyOf(it.groupValues[2]), explicit = true)) }
        }.filter { it.amount > 0.0 }
        val best = candidates.minByOrNull { it.range.first } ?: return null
        // "בסך 12.99 דולר": the cue found the number, the marker next to that same number names the currency.
        val marker = if (best.explicit) best else candidates.firstOrNull { it.explicit && it.numberStart == best.numberStart } ?: best
        return Found(best.amount, marker.currency, best.range)
    }

    private fun candidate(match: MatchResult, numberGroup: Int, currency: String, explicit: Boolean): Candidate =
        Candidate(
            amount = parseNumber(match.groupValues[numberGroup]),
            currency = currency,
            range = match.range,
            numberStart = match.groups[numberGroup]?.range?.first ?: match.range.first,
            explicit = explicit,
        )

    /** "$" / "usd" / "דולר" → "USD", "€" / "eur" / "אירו" / "יורו" → "EUR", "£" / "gbp" / "פאונד" / "ליש"ט" → "GBP". */
    private fun currencyOf(marker: String): String {
        val m = marker.lowercase()
        return when {
            m == "$" || m == "usd" || m.startsWith("דולר") -> Currencies.USD
            m == "€" || m == "eur" || m == "אירו" || m == "יורו" -> Currencies.EUR
            else -> Currencies.GBP
        }
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
