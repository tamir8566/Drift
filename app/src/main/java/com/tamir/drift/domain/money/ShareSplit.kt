package com.tamir.drift.domain.money

/** "עם חברים": my share when a charge is split evenly (חצי / שליש / רבע). Amounts are agorot. */
object ShareSplit {

    /** The splits offered as chips: half, a third, a quarter. */
    val PARTS: List<Int> = listOf(2, 3, 4)

    /** One of [parts] equal shares of the amount, rounded to the agora (half up). */
    fun share(amountAgorot: Long, parts: Int): Long {
        if (amountAgorot <= 0L) return 0L
        if (parts <= 1) return amountAgorot
        return (amountAgorot + parts / 2) / parts
    }

    /** Which even split a stored share is (2 / 3 / 4), or null when it is none of them. */
    fun partsOf(amountAgorot: Long, mineAgorot: Long): Int? =
        if (amountAgorot <= 0L) null else PARTS.firstOrNull { share(amountAgorot, it) == mineAgorot }
}
