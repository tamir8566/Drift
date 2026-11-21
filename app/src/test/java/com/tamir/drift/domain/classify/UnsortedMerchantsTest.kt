package com.tamir.drift.domain.classify

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.MerchantRuleModel
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.model.TxnSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class UnsortedMerchantsTest {

    private var nextId = 1L
    private fun tx(merchant: String, amount: Double, ts: Long = nextId, cat: String = CategoryKeys.OTHER, kind: TxnKind = TxnKind.EXPENSE) =
        TxnSummary(nextId++, amount, amount, merchant, cat, ts, TransactionSource.SMS, kind = kind)

    private fun learned(keyword: String, cat: String) = MerchantRuleModel(keyword, cat, isUserDefined = true)

    @Test
    fun `merchants in other are grouped by their key, biggest first`() {
        val txs = listOf(
            tx("קיוסק הפינה 12", 20.0, ts = 1),
            tx("קיוסק-הפינה", 30.0, ts = 5),
            tx("חנות הספרים", 120.0, ts = 3),
            tx("שופרסל", 300.0, cat = CategoryKeys.GROCERIES),
        )
        val found = UnsortedMerchants.find(txs, MerchantClassifier.SEED_RULES)
        assertEquals(listOf("חנות הספרים", "קיוסק הפינה"), found.map { it.key })
        assertEquals(2, found[1].count)
        assertEquals(50.0, found[1].total, 0.001)
        // The name shown is the spelling of the most recent charge.
        assertEquals("קיוסק-הפינה", found[1].displayName)
    }

    @Test
    fun `a learned rule - also one that says other - takes the merchant off the list`() {
        val txs = listOf(tx("קיוסק הפינה", 20.0), tx("חנות הספרים", 120.0), tx("חנות הספרים רעננה", 60.0))
        val rules = MerchantClassifier.SEED_RULES + learned("קיוסק הפינה", CategoryKeys.OTHER) + learned("חנות הספרים", CategoryKeys.LEISURE)
        assertEquals(emptyList<String>(), UnsortedMerchants.find(txs, rules).map { it.key })
    }

    @Test
    fun `a built in rule alone does not hide a merchant that still sits in other`() {
        // Classified as groceries by the seed list, but this charge was stored under "other".
        val found = UnsortedMerchants.find(listOf(tx("שופרסל דיל", 80.0)), MerchantClassifier.SEED_RULES)
        assertEquals(listOf("שופרסל דיל"), found.map { it.key })
    }

    @Test
    fun `blank merchants and transfers do not wait for a category, refunds reduce the total`() {
        val txs = listOf(
            tx("", 50.0),
            tx("העברה לחבר", 400.0, kind = TxnKind.TRANSFER),
            tx("חנות הספרים", 120.0),
            tx("חנות הספרים", 20.0, kind = TxnKind.REFUND),
        )
        val found = UnsortedMerchants.find(txs, emptyList())
        assertEquals(1, found.size)
        assertEquals(100.0, found[0].total, 0.001)
        assertEquals(2, found[0].count)
    }
}
