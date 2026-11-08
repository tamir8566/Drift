package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.ParsedTransaction
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.UserPattern

/**
 * Facade that picks the right parser for a captured text.
 *
 * Order: user-saved calibration patterns → source-specific parser → generic amount-only
 * fallback. Never throws; worst case returns [ParseStatus.UNPARSED].
 */
class TransactionTextParser(
    private val userPatterns: List<UserPattern> = emptyList(),
    private val maxSmsParser: SourceParser = MaxSmsParser(),
    private val googlePayParser: SourceParser = GooglePayNotificationParser(),
) {

    fun parse(text: String, source: TransactionSource): ParsedTransaction {
        if (text.isBlank()) return ParsedTransaction.unparsed("empty")

        UserPatternMatcher(userPatterns).parse(text, source)?.let { return it }

        val primary = when (source) {
            TransactionSource.SMS -> maxSmsParser.parse(text)
            TransactionSource.GPAY -> googlePayParser.parse(text)
            TransactionSource.MANUAL -> genericFallback(text)
        }
        if (primary.status != ParseStatus.UNPARSED) return primary

        // Try the other parser; templates sometimes leak across sources.
        val secondary = when (source) {
            TransactionSource.SMS -> googlePayParser.parse(text)
            TransactionSource.GPAY -> maxSmsParser.parse(text)
            TransactionSource.MANUAL -> primary
        }
        if (secondary.status != ParseStatus.UNPARSED) return secondary.copy(parserName = "${secondary.parserName}(fallback)")

        return genericFallback(text)
    }

    /** Any number next to a shekel marker → AMOUNT_ONLY, else UNPARSED. */
    private fun genericFallback(text: String): ParsedTransaction {
        val found = AmountParser.find(text) ?: return ParsedTransaction.unparsed("generic")
        return ParsedTransaction(found.amount, null, null, found.currency, ParseStatus.AMOUNT_ONLY, "generic")
    }
}
