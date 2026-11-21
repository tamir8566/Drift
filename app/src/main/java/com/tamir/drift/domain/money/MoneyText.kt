package com.tamir.drift.domain.money

import java.util.Locale

/**
 * Signed numbers for a right-to-left screen. A bare "−1,200" inside Hebrew text is reordered by
 * the bidi algorithm (the minus lands after the number), so a negative value is prefixed with a
 * left-to-right mark: the minus then stays in front of the digits — "−1,200 ₪".
 */
object MoneyText {
    const val MINUS = '−'
    private const val LRM = '‎'

    /** "1,200" — thousands grouping, Western digits. */
    fun group(units: Long): String = String.format(Locale.US, "%,d", units)

    /** "1,200" / "−1,200" (never "−0"). */
    fun signed(units: Long): String = if (units < 0L) "$LRM$MINUS${group(-units)}" else group(units)

    /** Puts a real minus in front of already formatted digits: "45.90" → "−45.90". */
    fun negative(digits: String): String = "$LRM$MINUS$digits"

    /** "43%" / "−12%". */
    fun signedPercent(percent: Int): String = if (percent < 0) "$LRM$MINUS${-percent}%" else "$percent%"
}
