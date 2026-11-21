package com.tamir.drift.domain.classify

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.MerchantRuleModel
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.model.TxnSummary

/** One merchant waiting to be classified: every charge of it sits in "אחר" and no learned rule covers it. */
data class UnsortedMerchant(
    /** The [MerchantKey] all its spellings share. */
    val key: String,
    /** The merchant as written on its most recent charge. */
    val displayName: String,
    /** Net total of its charges (my share, refunds subtracted). */
    val total: Double,
    val count: Int,
    val lastTimestamp: Long,
)

/**
 * The "בתי עסק מחכים לסיווג" inbox: merchants whose transactions are in the "other" category
 * without the user ever having decided so. Choosing a category — or "באמת אחר" — stores a learned
 * rule, which is what takes a merchant off this list.
 */
object UnsortedMerchants {

    fun find(transactions: List<TxnSummary>, rules: List<MerchantRuleModel>): List<UnsortedMerchant> {
        val classifier = MerchantClassifier(rules)
        val decided = HashMap<String, Boolean>()
        return transactions
            .filter { it.categoryKey == CategoryKeys.OTHER && it.kind != TxnKind.TRANSFER && it.merchant.isNotBlank() }
            .filter { t -> !decided.getOrPut(t.merchant) { classifier.matchRule(t.merchant)?.isUserDefined == true } }
            .groupBy { MerchantKey.normalize(it.merchant) }
            .filterKeys { it.isNotEmpty() }
            .map { (key, list) ->
                val latest = list.maxByOrNull { it.timestamp } ?: list.first()
                UnsortedMerchant(key, latest.merchant.trim(), list.sumOf { it.net }, list.size, latest.timestamp)
            }
            .sortedWith(compareByDescending<UnsortedMerchant> { it.total }.thenBy { it.key })
    }
}
