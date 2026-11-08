package com.tamir.drift.domain.classify

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.MerchantRuleModel
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantClassifierTest {

    private val seeded = MerchantClassifier(MerchantClassifier.SEED_RULES)

    @Test
    fun `israeli merchants from the PRD list`() {
        assertEquals(CategoryKeys.GROCERIES, seeded.classify("שופרסל דיל בת חפר"))
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
    fun `learned rule wins over the built-in one`() {
        val rules = MerchantClassifier.SEED_RULES + MerchantRuleModel("וולט", CategoryKeys.LEISURE, isUserDefined = true)
        assertEquals(CategoryKeys.LEISURE, MerchantClassifier(rules).classify("וולט — פיצה"))
    }
}
