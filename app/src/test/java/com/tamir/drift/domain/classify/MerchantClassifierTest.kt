package com.tamir.drift.domain.classify

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.MerchantRuleModel
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantClassifierTest {

    private val seeded = MerchantClassifier(MerchantClassifier.SEED_RULES)

    @Test
    fun `israeli merchants from the PRD list`() {
        assertEquals(CategoryKeys.GROCERIES, seeded.classify("שופרסל דיל נתניה"))
        assertEquals(CategoryKeys.GROCERIES, seeded.classify("רמי לוי שיווק השקמה"))
        assertEquals(CategoryKeys.GROCERIES, seeded.classify("ויקטורי"))
        assertEquals(CategoryKeys.GROCERIES, seeded.classify("יוחננוף"))
        assertEquals(CategoryKeys.EATING_OUT, seeded.classify("Wolt"))
        assertEquals(CategoryKeys.EATING_OUT, seeded.classify("וולט — פיצה"))
        assertEquals(CategoryKeys.EATING_OUT, seeded.classify("תן ביס"))
        assertEquals(CategoryKeys.EATING_OUT, seeded.classify("מקדונלד'ס"))
        assertEquals(CategoryKeys.EATING_OUT, seeded.classify("ארומה תל אביב"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("רב־קו"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("Rav-Kav Online"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("מוביט"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("Gett"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("יאנגו"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("פז אפליקציית יילו"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("Yellow"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("סונול"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("דלק מנטה"))
        assertEquals(CategoryKeys.HEALTH, seeded.classify("סופר־פארם"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("Spotify"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("נטפליקס"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("Google *YouTube"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("Apple.com/bill"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("סלקום"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("פרטנר"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("הוט"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("בזק"))
        assertEquals(CategoryKeys.LEISURE, seeded.classify("Bit"))
        assertEquals(CategoryKeys.LEISURE, seeded.classify("Paybox"))
        assertEquals(CategoryKeys.CLOTHING, seeded.classify("ZARA"))
        assertEquals(CategoryKeys.CLOTHING, seeded.classify("קסטרו"))
        assertEquals(CategoryKeys.CLOTHING, seeded.classify("פוקס הום"))
        assertEquals(CategoryKeys.HOME, seeded.classify("IKEA"))
    }

    @Test
    fun `super pharm beats the shorter super keyword`() {
        assertEquals(CategoryKeys.HEALTH, seeded.classify("סופר פארם רעננה"))
        assertEquals(CategoryKeys.GROCERIES, seeded.classify("סופר יודה"))
    }

    @Test
    fun `short keywords need a whole word`() {
        assertEquals(CategoryKeys.OTHER, seeded.classify("גטו"))
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("גט טקסי"))
    }

    @Test
    fun `unknown merchant is other`() {
        assertEquals(CategoryKeys.OTHER, seeded.classify("חנות לא ידועה"))
        assertEquals(CategoryKeys.OTHER, seeded.classify(""))
        assertEquals(CategoryKeys.OTHER, seeded.classify(null))
    }

    @Test
    fun `a learned merchant named like a short keyword does not hijack a longer built-in one`() {
        val rules = MerchantClassifier.SEED_RULES + MerchantRuleModel("סופר", CategoryKeys.LEISURE, isUserDefined = true)
        val c = MerchantClassifier(rules)
        assertEquals(CategoryKeys.LEISURE, c.classify("סופר"))                // the merchant itself
        assertEquals(CategoryKeys.LEISURE, c.classify("סופר 12"))             // same key (branch number)
        assertEquals(CategoryKeys.LEISURE, c.classify("סופר יודה"))           // prefix on a word boundary
        assertEquals(CategoryKeys.HEALTH, c.classify("סופר פארם רעננה"))      // the built-in rule is more specific
        assertEquals(CategoryKeys.HEALTH, c.classify("סופר־פארם"))
        assertEquals(CategoryKeys.GROCERIES, c.classify("מיני סופר השכונה"))  // not a prefix → the learned rule does not apply
        assertEquals(CategoryKeys.GROCERIES, c.classify("סופרמן"))            // not a word boundary
    }

    @Test
    fun `learned rules match by key equality or prefix, never by contains`() {
        val c = MerchantClassifier(listOf(MerchantRuleModel("שופרסל דיל 123 בע\"מ", CategoryKeys.HOME, isUserDefined = true)))
        assertEquals(CategoryKeys.HOME, c.classify("שופרסל דיל"))
        assertEquals(CategoryKeys.HOME, c.classify("שופרסל-דיל 45"))
        assertEquals(CategoryKeys.HOME, c.classify("שופרסל דיל נתניה"))
        assertEquals(CategoryKeys.OTHER, c.classify("שופרסל"))               // shorter than the learned key
        assertEquals(CategoryKeys.OTHER, c.classify("אקספרס שופרסל דיל"))    // contains, but not a prefix
    }

    @Test
    fun `an exact learned rule beats everything, the longest learned prefix wins`() {
        val rules = MerchantClassifier.SEED_RULES + listOf(
            MerchantRuleModel("סופר פארם רעננה", CategoryKeys.HOME, isUserDefined = true),
            MerchantRuleModel("wolt", CategoryKeys.LEISURE, isUserDefined = true),
            MerchantRuleModel("wolt market", CategoryKeys.GROCERIES, isUserDefined = true),
        )
        val c = MerchantClassifier(rules)
        assertEquals(CategoryKeys.HOME, c.classify("סופר-פארם רעננה"))
        assertEquals(CategoryKeys.HEALTH, c.classify("סופר פארם חיפה"))
        assertEquals(CategoryKeys.LEISURE, c.classify("Wolt Restaurant"))   // an unrelated built-in keyword does not override the learned prefix
        assertEquals(CategoryKeys.GROCERIES, c.classify("Wolt Market TLV"))
    }

    @Test
    fun `built-in keywords keep their numbers`() {
        assertEquals(CategoryKeys.TRANSPORT, seeded.classify("כביש 6"))
        assertEquals(CategoryKeys.SUBSCRIPTIONS, seeded.classify("019 מובייל"))
        assertEquals(CategoryKeys.OTHER, seeded.classify("כביש"))
    }

    @Test
    fun `learned rule wins over the built-in one`() {
        val rules = MerchantClassifier.SEED_RULES + MerchantRuleModel("וולט", CategoryKeys.LEISURE, isUserDefined = true)
        assertEquals(CategoryKeys.LEISURE, MerchantClassifier(rules).classify("וולט — פיצה"))
    }
}
