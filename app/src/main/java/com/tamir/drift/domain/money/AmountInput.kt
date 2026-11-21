package com.tamir.drift.domain.money

/**
 * The one parser for amounts the user types (every amount field goes through it).
 *
 * A comma followed by one or two digits at the very end is a decimal separator ("12,5" → 12.5,
 * "1,234,50" → 1234.5); any other comma is a thousands separator ("1,200" → 1200). When the text
 * has a dot, the dot is the decimal separator and all commas are thousands separators.
 */
object AmountInput {

    private val decimalComma = Regex("""^(.*),(\d{1,2})$""")
    private val plainNumber = Regex("""^(?:\d+(?:\.\d*)?|\.\d+)$""")

    /** The typed value, or null when the text is not a non-negative number. */
    fun parse(text: String): Double? {
        val s = text.trim().replace(" ", "").replace(" ", "").replace("₪", "")
        if (s.isEmpty()) return null
        val normalized = if (s.contains('.')) {
            s.replace(",", "")
        } else {
            val m = decimalComma.matchEntire(s)
            if (m != null) m.groupValues[1].replace(",", "") + "." + m.groupValues[2] else s.replace(",", "")
        }
        if (!plainNumber.matches(normalized)) return null
        return normalized.toDoubleOrNull()
    }
}
