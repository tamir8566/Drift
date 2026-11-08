package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.ParsedDate
import com.tamir.drift.domain.model.ParsedTransaction

/**
 * Parser for SMS messages sent by the Israeli card company "max".
 *
 * Primary fixture (a real message, card digits replaced):
 * ```
 * היי, ביקשת שנעדכן אותך על כל עסקת אינטרנט: היום 05/11 בית עסק
 * פז אפליקציית יילו
 * חייב את כרטיסך 1234 בסך 216.79 שח
 * כדאי לעקוב אחר החיובים כאן:
 * https://link.max.co.il/5f093cf
 * ```
 * Merchant = text between "בית עסק" and "חייב את כרטיסך"; amount = number after "בסך"
 * followed by a shekel marker; date = "היום DD/MM" / "אתמול" / "DD/MM"; last-4 = digits right
 * after "כרטיסך". Older template shapes ("ב-WOLT", "אצל רמי לוי", "בשופרסל דיל בתאריך") are
 * still supported as fallbacks, and everything is tolerant to wording changes.
 */
class MaxSmsParser : SourceParser {

    override val name: String = "max-sms"

    private val urlLine = Regex("""https?://\S+""")

    private val last4Patterns = listOf(
        Regex("""כרטיסך\s*[:#]?\s*(\d{4})(?!\d)"""),
        Regex("""כרטיס\D{0,25}?(\d{4})(?!\d)"""),
        Regex("""\*{1,4}\s?(\d{4})(?!\d)"""),
        Regex("""המסתיים ב-?\s?(\d{4})(?!\d)"""),
    )

    /** "בית עסק <merchant> חייב את כרטיסך" — the real max template. */
    private val businessPattern =
        Regex("""בית\s+עסק\s*:?\s*(.+?)\s*(?:חייב|חוייב|חויב|חייבה|זיכה|זוכה)\s+את\s+כרטיס""", RegexOption.DOT_MATCHES_ALL)

    /** Fallback cues for older/other templates. */
    private val merchantPatterns = listOf(
        // "ב-WOLT", "ב- שופרסל"
        Regex("""(?<!\p{L})ב-\s?(\p{L}.*?)(?=$MERCHANT_END)"""),
        // "אצל רמי לוי", "בבית העסק רמי לוי"
        Regex("""(?:אצל|בבית העסק|בית עסק:?|בחנות)\s+(.+?)(?=$MERCHANT_END)"""),
        // "ב" prefixed directly to the word after the amount: "בסך 45.90 ש"ח בשופרסל דיל בתאריך"
        Regex("""(?:₪|ש["״'']ח|שח|ILS|NIS)\s+ב(?!תאריך|שעה|סך|כרטיס)(\S.*?)(?=$MERCHANT_END)"""),
    )

    private val todayDate = Regex("""היום\s*(?:ב-?\s*)?(\d{1,2})[./](\d{1,2})""")
    private val yesterdayDate = Regex("""אתמול\s*(?:ב-?\s*)?(?:(\d{1,2})[./](\d{1,2}))?""")
    private val plainDate =
        Regex("""(?<![\d/.])(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?(?![\d/.])(?!\s*(?:₪|שח|ש["״'']ח|ILS|NIS))""")

    override fun parse(text: String): ParsedTransaction {
        // Drop link lines first; they contain digits that must not confuse anything.
        val clean = TextNormalizer.normalize(text.replace(urlLine, " "))
        val flat = clean.replace('\n', ' ').replace(Regex("""\s+"""), " ")

        val amount = AmountParser.find(flat)
        val last4 = last4Patterns.firstNotNullOfOrNull { it.find(flat)?.groupValues?.get(1) }
        val date = parseDate(flat)

        val merchant = businessPattern.find(flat)?.groupValues?.get(1)
            ?.let { TextNormalizer.cleanMerchant(it) }
            ?: fallbackMerchant(flat, amount)

        val status = when {
            amount != null && merchant != null -> ParseStatus.PARSED
            amount != null -> ParseStatus.AMOUNT_ONLY
            else -> ParseStatus.UNPARSED
        }
        return ParsedTransaction(amount?.amount, merchant, last4, amount?.currency, status, name, date)
    }

    private fun fallbackMerchant(flat: String, amount: AmountParser.Found?): String? {
        // Remove the amount span so "ב-" inside "בסך" etc. does not confuse merchant detection.
        val withoutAmount = amount?.let { flat.removeRange(it.range) } ?: flat
        return merchantPatterns
            .firstNotNullOfOrNull { it.find(withoutAmount)?.groupValues?.get(1) }
            ?.let { TextNormalizer.cleanMerchant(stripTrailingNoise(it)) }
    }

    private fun parseDate(flat: String): ParsedDate? {
        todayDate.find(flat)?.let { return ParsedDate(day = it.groupValues[1].toInt(), month = it.groupValues[2].toInt(), relativeDays = 0) }
        if (flat.contains("היום")) return ParsedDate(relativeDays = 0)
        yesterdayDate.find(flat)?.let { m ->
            val d = m.groupValues[1].toIntOrNull()
            val mo = m.groupValues[2].toIntOrNull()
            return ParsedDate(day = d, month = mo, relativeDays = 1)
        }
        for (m in plainDate.findAll(flat)) {
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            if (d in 1..31 && mo in 1..12) return ParsedDate(day = d, month = mo)
        }
        return null
    }

    private fun stripTrailingNoise(m: String): String =
        m.replace(Regex("""\s+(אושר|אושרה|בוצע|בוצעה)$"""), "")

    private companion object {
        /** Where a merchant name stops. */
        const val MERCHANT_END =
            """\s+בתאריך|\s+ב-?\d{1,2}[./]\d{1,2}|\s+בשעה|\s+יתרת|\s+לפרטים|\s+אושר|\s+מסגרת|\s+כדאי|""" +
                """\s+בכרטיס|\s+בסך|\s+ע["״']?ס|\s+(?:₪|שח|ש["״']ח|ILS|NIS)(?!\p{L})|[.,;\n]|$"""
    }
}
