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

/**
 * What a stored transaction means for the calculations: an expense counts, a refund is
 * subtracted, a transfer ("לא נספר") is ignored everywhere.
 */
enum class TxnKind {
    EXPENSE,
    REFUND,
    TRANSFER;

    companion object {
        /** Lenient parse of the stored name; unknown / null → [EXPENSE]. */
        fun from(name: String?): TxnKind = entries.firstOrNull { it.name == name } ?: EXPENSE
    }
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
    /** [TxnKind.REFUND] when the text speaks of a credit / cancelled transaction, else an expense. */
    val kind: TxnKind = TxnKind.EXPENSE,
) {
    /** True when [amount] is in a foreign currency ([currency] is "USD" / "EUR" / "GBP"), not in shekels. */
    val isForeign: Boolean get() = currency != null && currency != "ILS"

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
     *
     * A message reports a purchase that just happened, so an explicit date further back than
     * [maxAgeDays] is not the purchase date — it is some other number that happens to look like
     * day/month (a phone extension, "1/12" in a promotion). Such a date is dropped (null) and the
     * caller keeps the capture time, instead of moving the expense months or a year back.
     */
    fun resolve(today: java.time.LocalDate, maxAgeDays: Long = MAX_AGE_DAYS): java.time.LocalDate? {
        relativeDays?.let { return today.minusDays(it.toLong()) }
        val d = day ?: return null
        val m = month ?: return null
        val thisYear = runCatching { java.time.LocalDate.of(today.year, m, d) }.getOrNull() ?: return null
        val candidate = if (thisYear.isAfter(today)) thisYear.minusYears(1) else thisYear
        return if (candidate.isBefore(today.minusDays(maxAgeDays))) null else candidate
    }

    companion object {
        /** How far back a date written in a message is still believed. */
        const val MAX_AGE_DAYS = 31L
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
    val kind: TxnKind = TxnKind.EXPENSE,
) {
    val isExpense: Boolean get() = kind == TxnKind.EXPENSE
    val isRefund: Boolean get() = kind == TxnKind.REFUND

    /**
     * What this transaction adds to "spent": my share for an expense, minus my share for a
     * refund, nothing for a transfer. Every calculator sums this — never [myShare] directly.
     */
    val net: Double get() = when (kind) {
        TxnKind.EXPENSE -> myShare
        TxnKind.REFUND -> -myShare
        TxnKind.TRANSFER -> 0.0
    }
}

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
