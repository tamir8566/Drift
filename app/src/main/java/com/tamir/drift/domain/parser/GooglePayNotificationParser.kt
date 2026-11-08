package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.ParsedTransaction

/**
 * Parser for Google Pay / Google Wallet transaction notifications.
 *
 * The listener service joins the notification title and body with a newline, so the
 * input usually looks like one of:
 *  - `שופרסל דיל\n₪45.90 עם Visa •••• 1234`
 *  - `Wolt\n₪89.00 with Mastercard •••• 5678`
 *  - `שילמת ₪45.90 ב-שופרסל דיל` (single line)
 *  - `You paid ₪12.50 at Aroma`
 */
class GooglePayNotificationParser : SourceParser {

    override val name: String = "google-pay"

    private val last4 = Regex("""(?:•{2,4}|\*{2,4}|\.{3,4})\s?(\d{4})(?!\d)""")

    private val inlineMerchant = listOf(
        Regex("""(?:ב-|אצל|at)\s?(.+?)(?=\s+(?:with|עם|באמצעות)\b|[.\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:₪|ILS|NIS)\s?[\d.,]+\s+ב(?!אמצעות)(\S.*?)(?=\s+(?:with|עם|באמצעות)\b|[.\n]|$)"""),
    )

    /** Lines that are clearly not a merchant (amount lines, card lines, generic titles). */
    private val notMerchantLine = Regex(
        """^(?:₪|ILS|NIS|\d|שילמת|paid|you paid|תשלום|payment|google pay|google wallet|ארנק google|עסקה|transaction)""",
        RegexOption.IGNORE_CASE,
    )

    override fun parse(text: String): ParsedTransaction {
        val clean = TextNormalizer.normalize(text)
        val amount = AmountParser.find(clean)
        val digits = last4.find(clean)?.groupValues?.get(1)

        val lines = clean.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        // 1. Title line as merchant when it does not look like an amount/system line.
        val titleMerchant = lines.firstOrNull()
            ?.takeIf { lines.size > 1 && !notMerchantLine.containsMatchIn(it) && AmountParser.find(it) == null }

        // 2. Otherwise look for an inline "at X" / "ב-X" phrase.
        val inline = inlineMerchant.firstNotNullOfOrNull { it.find(clean)?.groupValues?.get(1) }

        val merchant = TextNormalizer.cleanMerchant(titleMerchant ?: inline)

        val status = when {
            amount != null && merchant != null -> ParseStatus.PARSED
            amount != null -> ParseStatus.AMOUNT_ONLY
            else -> ParseStatus.UNPARSED
        }
        return ParsedTransaction(amount?.amount, merchant, digits, amount?.currency, status, name)
    }
}
