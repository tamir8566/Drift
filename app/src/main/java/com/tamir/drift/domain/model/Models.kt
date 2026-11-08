package com.tamir.drift.domain.model

/**
 * Pure-Kotlin domain models. No Android imports here so the domain layer can be
 * unit-tested on the JVM.
 */

/** Where a transaction came from. */
enum class TransactionSource {
    GPAY,
    SMS,
    MANUAL,
}

/** Stable category keys. Display names (Hebrew) live in strings.xml and in the Category table. */
object CategoryKeys {
    const val GROCERIES = "groceries"      // סופר
    const val EATING_OUT = "eating_out"    // אוכל בחוץ
    const val TRANSPORT = "transport"      // תחבורה
    const val SUBSCRIPTIONS = "subscriptions" // מנויים
    const val LEISURE = "leisure"          // בילויים
    const val CLOTHING = "clothing"        // ביגוד
    const val HEALTH = "health"            // בריאות
    const val HOME = "home"                // בית
    const val OTHER = "other"              // אחר

    val all: List<String> = listOf(
        GROCERIES, EATING_OUT, TRANSPORT, SUBSCRIPTIONS, LEISURE, CLOTHING, HEALTH, HOME, OTHER,
    )
}

/** How confident the parser is about a captured text. */
enum class ParseStatus {
    /** Amount and merchant were extracted. */
    PARSED,
    /** Only an amount was found; merchant unknown. */
    AMOUNT_ONLY,
    /** Nothing usable; the user has to fill the transaction in manually. */
    UNPARSED,
}

/** Result of parsing a notification / SMS text. */
data class ParsedTransaction(
    val amount: Double?,
    val merchant: String?,
    val last4: String?,
    val currency: String?,
    val status: ParseStatus,
    /** Which parser produced the result, for the debug log. */
    val parserName: String,
    /** Day/month mentioned in the text ("היום 05/11", "אתמול", "05/11"), when present. */
    val date: ParsedDate? = null,
) {
    companion object {
        fun unparsed(parserName: String) =
            ParsedTransaction(null, null, null, null, ParseStatus.UNPARSED, parserName)
    }
}

/**
 * A date found inside a message. Either a relative word (today / yesterday) or an explicit
 * day + month without a year. [resolve] turns it into a LocalDate relative to "now".
 */
data class ParsedDate(
    val day: Int? = null,
    val month: Int? = null,
    val relativeDays: Int? = null,
) {
    /**
     * Year rule (PRD note): current year, or the previous one when the day/month would be in the
     * future. Relative dates ("היום"/"אתמול") win over an explicit day/month when both exist.
     */
    fun resolve(today: java.time.LocalDate): java.time.LocalDate? {
        relativeDays?.let { return today.minusDays(it.toLong()) }
        val d = day ?: return null
        val m = month ?: return null
        val candidate = runCatching { java.time.LocalDate.of(today.year, m, d) }.getOrNull() ?: return null
        return if (candidate.isAfter(today)) candidate.minusYears(1) else candidate
    }
}

/**
 * Minimal projection of a stored transaction used by the pure domain calculators
 * (budget, insights, dedupe). The Room entity maps to/from this.
 */
data class TxnSummary(
    val id: Long,
    /** Full amount that was charged. */
    val amountTotal: Double,
    /** What the user actually paid (equals amountTotal unless shared). */
    val myShare: Double,
    val merchant: String,
    val categoryKey: String,
    /** Epoch millis of the transaction. */
    val timestamp: Long,
    val source: TransactionSource,
    val isShared: Boolean = false,
)

/** A merchant keyword -> category rule. */
data class MerchantRuleModel(
    val keyword: String,
    val categoryKey: String,
    /** User-created rules win over seeded ones. */
    val isUserDefined: Boolean,
)

/** A user-saved parsing pattern created in the calibration screen. */
data class UserPattern(
    val id: Long,
    /** Regex with named groups `amount` and optionally `merchant` / `last4`. */
    val regex: String,
    /** Which source the pattern applies to (null = any). */
    val source: TransactionSource?,
)
