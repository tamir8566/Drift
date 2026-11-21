package com.tamir.drift.domain.money

import kotlin.math.roundToLong

/**
 * The small, user-editable table of exchange rates (settings → "שערי מטבע"). No network: the
 * rates only produce an estimate ("≈") for a purchase captured in a foreign currency, until the
 * user types the final shekel amount. Each rate is the price of one unit, in agorot.
 */
data class FxRates(val usdAgorot: Long, val eurAgorot: Long, val gbpAgorot: Long) {

    fun rateFor(currency: String?): Long? = when (currency?.uppercase()) {
        Currencies.USD -> usdAgorot
        Currencies.EUR -> eurAgorot
        Currencies.GBP -> gbpAgorot
        else -> null
    }

    /** Shekel estimate (agorot) of [amount] units of [currency]; null for shekels, an unknown currency or a missing rate. */
    fun estimateAgorot(amount: Double, currency: String?): Long? {
        val rate = rateFor(currency)?.takeIf { it > 0L } ?: return null
        if (amount <= 0.0) return null
        return (amount * rate).roundToLong()
    }

    companion object {
        /** Placeholders only — the user edits them in settings. */
        val DEFAULT = FxRates(usdAgorot = 3_40L, eurAgorot = 3_90L, gbpAgorot = 4_50L)
    }
}

object Currencies {
    const val ILS = "ILS"
    const val USD = "USD"
    const val EUR = "EUR"
    const val GBP = "GBP"

    val foreign: List<String> = listOf(USD, EUR, GBP)

    fun isForeign(code: String?): Boolean = code != null && code.uppercase() in foreign

    /** The sign shown next to an original amount ("$", "€", "£"). */
    fun symbol(code: String?): String = when (code?.uppercase()) {
        USD -> "$"
        EUR -> "€"
        GBP -> "£"
        else -> "₪"
    }

    /** Amount in the currency's minor unit (cents / pence): 12.99 → 1299. */
    fun toMinor(amount: Double): Long = (amount * 100.0).roundToLong()
}
