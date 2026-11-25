package com.tamir.drift.domain.parser

/**
 * Decides what to do with a captured text in which no amount could be read.
 *
 * A notification from the wallet app or a message from the card company is not always a payment:
 * "the card was added", an offer, a security notice. Storing each of those as a "לא פוענח" row
 * fills the list with things the user can never complete. Only a text that still looks like a
 * payment is kept for the user to complete by hand; the rest is ignored (it stays in the raw log).
 */
object PaymentText {

    private val digit = Regex("""\d""")

    /** A number written like a price: "45.90", "1,234.50", "45,90". */
    private val priceLike = Regex("""(?<![\d.,/])(?:\d{1,3}(?:,\d{3})+|\d+)[.,]\d{2}(?![\d.,/])""")

    /** Currency signs and codes, shekel or foreign. */
    private val currency = Regex("""₪|ש["״'']ח|(?<!\p{L})שח(?!\p{L})|[$€£]|(?<![A-Za-z])(?:ILS|NIS|USD|EUR|GBP)(?![A-Za-z])|דולר|אירו|יורו""", RegexOption.IGNORE_CASE)

    /** Words a charge or a payment is reported with. */
    private val paymentWord = Regex(
        """חויב|חוייב|חייב את|חיוב|שילמת|תשלום|עסקה|רכישה|קנייה|קניה|זיכוי|זוכה|בסך|(?<![A-Za-z])(?:paid|payment|purchase|charged|transaction|refund)""",
        RegexOption.IGNORE_CASE,
    )

    private val url = Regex("""https?://\S+""")

    /**
     * True when [text] speaks of money although no amount was parsed from it: it has a digit, and
     * next to it a price-like number, a currency sign or a payment word. Links are not looked at
     * (their digits mean nothing).
     */
    fun looksLikePayment(text: String): Boolean {
        val clean = TextNormalizer.normalize(text.replace(url, " "))
        if (!digit.containsMatchIn(clean)) return false
        return priceLike.containsMatchIn(clean) || currency.containsMatchIn(clean) || paymentWord.containsMatchIn(clean)
    }
}
