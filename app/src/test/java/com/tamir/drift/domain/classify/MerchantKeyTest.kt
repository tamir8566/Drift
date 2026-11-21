package com.tamir.drift.domain.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantKeyTest {

    @Test
    fun `lowercase, punctuation, maqaf and quotes`() {
        assertEquals("rav kav online", MerchantKey.normalize("Rav-Kav  Online"))
        assertEquals("רב קו", MerchantKey.normalize("רב־קו"))
        assertEquals("מקדונלדס", MerchantKey.normalize("מקדונלד'ס"))
        assertEquals("apple com bill", MerchantKey.normalize("Apple.com/bill"))
        assertEquals("וולט פיצה", MerchantKey.normalize("וולט — פיצה"))
        assertEquals("", MerchantKey.normalize("  "))
    }

    @Test
    fun `trailing branch numbers and company suffix are dropped`() {
        assertEquals("שופרסל דיל", MerchantKey.normalize("שופרסל דיל 123"))
        assertEquals("שופרסל דיל", MerchantKey.normalize("שופרסל דיל בע\"מ"))
        assertEquals("שופרסל דיל", MerchantKey.normalize("שופרסל דיל בע״מ 0045"))
        assertEquals("שופרסל דיל", MerchantKey.normalize("שופרסל דיל בע.מ"))
        assertEquals("aroma", MerchantKey.normalize("AROMA LTD #12"))
        assertEquals("10bis", MerchantKey.normalize("10bis"))
        assertEquals("קפה 3 רחובות", MerchantKey.normalize("קפה 3 רחובות")) // a number in the middle stays
    }

    @Test
    fun `the last word is never dropped and the result is stable`() {
        assertEquals("019", MerchantKey.normalize("019"))
        assertEquals("019", MerchantKey.normalize("019 012"))
        for (s in listOf("שופרסל דיל 123 בע\"מ", "Rav-Kav", "019 012", "כביש 6")) {
            val once = MerchantKey.normalize(s)
            assertEquals(once, MerchantKey.normalize(once))
        }
    }

    @Test
    fun `clean keeps every word`() {
        assertEquals("כביש 6", MerchantKey.clean("כביש 6"))
        assertEquals("am pm", MerchantKey.clean("AM:PM"))
    }

    @Test
    fun `same merchant`() {
        assertTrue(MerchantKey.same("WOLT", "wolt 12"))
        assertFalse(MerchantKey.same("wolt", "wolt market"))
        assertFalse(MerchantKey.same("", ""))
        assertFalse(MerchantKey.same(null, "wolt"))
    }
}
