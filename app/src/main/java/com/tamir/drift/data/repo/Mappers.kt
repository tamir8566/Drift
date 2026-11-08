package com.tamir.drift.data.repo

import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import kotlin.math.roundToLong

/** Agorot ⇄ shekels. */
object Money {
    fun toAgorot(shekels: Double): Long = (shekels * 100.0).roundToLong()
    fun toShekels(agorot: Long): Double = agorot / 100.0
}

fun TransactionEntity.toSummary(): TxnSummary = TxnSummary(
    id = id,
    amountTotal = Money.toShekels(amountFull),
    myShare = Money.toShekels(amountMine),
    merchant = merchant,
    categoryKey = categoryId,
    timestamp = timestamp,
    source = runCatching { TransactionSource.valueOf(source) }.getOrDefault(TransactionSource.MANUAL),
    isShared = isShared,
)

/** Parsed (non-unparsed) transactions only, as the domain sees them. */
fun List<TransactionEntity>.toSummaries(): List<TxnSummary> = filter { !it.isUnparsed }.map { it.toSummary() }
