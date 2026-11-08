package com.tamir.drift.domain.parser

import com.tamir.drift.domain.model.ParseStatus
import com.tamir.drift.domain.model.ParsedTransaction
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.UserPattern

/**
 * Builds a reusable regex from a sample text in which the user marked the amount and the
 * merchant (calibration screen).
 *
 * Everything outside the marked spans is kept as literal text, except that digit runs are
 * generalised to `\d+` (dates, card numbers, times) and whitespace to `\s+`, so the
 * pattern matches future messages of the same template.
 */
object PatternBuilder {

    const val GROUP_AMOUNT = "amount"
    const val GROUP_MERCHANT = "merchant"
    const val GROUP_LAST4 = "last4"

    /**
     * @param text the raw sample (will be normalised the same way the parsers do).
     * @param amountRange inclusive-exclusive range of the amount digits inside the normalised text.
     * @param merchantRange optional range of the merchant name inside the normalised text.
     */
    fun build(text: String, amountRange: IntRange, merchantRange: IntRange?): String {
        val clean = TextNormalizer.normalize(text)
        require(amountRange.first >= 0 && amountRange.last < clean.length) { "amount range out of bounds" }
        if (merchantRange != null) {
            require(merchantRange.first >= 0 && merchantRange.last < clean.length) { "merchant range out of bounds" }
            require(!amountRange.overlaps(merchantRange)) { "amount and merchant ranges overlap" }
        }

        // A merchant at the very end of the text has no terminator, so it takes the rest of the line.
        val merchantAtEnd = merchantRange != null && clean.substring(merchantRange.last + 1).isBlank()
        val spans = buildList {
            add(Span(amountRange, """(?<$GROUP_AMOUNT>\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""))
            if (merchantRange != null) {
                add(Span(merchantRange, if (merchantAtEnd) """(?<$GROUP_MERCHANT>[^\n]+?)\s*$""" else """(?<$GROUP_MERCHANT>.+?)"""))
            }
        }.sortedBy { it.range.first }

        val sb = StringBuilder()
        var cursor = 0
        for (span in spans) {
            sb.append(generalise(clean.substring(cursor, span.range.first)))
            sb.append(span.replacement)
            cursor = span.range.last + 1
        }
        if (!merchantAtEnd) sb.append(generalise(clean.substring(cursor)))
        return sb.toString()
    }

    /** Escapes literal text, then generalises digit runs and whitespace. */
    private fun generalise(literal: String): String {
        if (literal.isEmpty()) return ""
        val tokens = Regex("""\d+|\s+|[^\d\s]+""").findAll(literal)
        return tokens.joinToString("") { m ->
            when {
                m.value.first().isDigit() -> """\d+"""
                m.value.first().isWhitespace() -> """\s*"""
                else -> Regex.escape(m.value)
            }
        }
    }

    private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last

    private data class Span(val range: IntRange, val replacement: String)
}

/** Tries user-saved patterns before the built-in parsers. */
class UserPatternMatcher(private val patterns: List<UserPattern>) {

    fun parse(text: String, source: TransactionSource?): ParsedTransaction? {
        val clean = TextNormalizer.normalize(text)
        for (pattern in patterns) {
            if (pattern.source != null && source != null && pattern.source != source) continue
            val regex = runCatching { Regex(pattern.regex) }.getOrNull() ?: continue
            val match = regex.find(clean) ?: continue
            val amountRaw = groupOrNull(match, PatternBuilder.GROUP_AMOUNT) ?: continue
            val amount = AmountParser.parseNumber(amountRaw).takeIf { it > 0 } ?: continue
            val merchant = TextNormalizer.cleanMerchant(groupOrNull(match, PatternBuilder.GROUP_MERCHANT))
            val last4 = groupOrNull(match, PatternBuilder.GROUP_LAST4)
            return ParsedTransaction(
                amount = amount,
                merchant = merchant,
                last4 = last4,
                currency = "ILS",
                status = if (merchant != null) ParseStatus.PARSED else ParseStatus.AMOUNT_ONLY,
                parserName = "user-pattern#${pattern.id}",
            )
        }
        return null
    }

    private fun groupOrNull(match: MatchResult, name: String): String? =
        runCatching { match.groups[name]?.value }.getOrNull()
}
