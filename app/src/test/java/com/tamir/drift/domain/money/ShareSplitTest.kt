package com.tamir.drift.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareSplitTest {

    @Test
    fun `half third and quarter rounded to the agora`() {
        assertEquals(50_00L, ShareSplit.share(100_00L, 2))
        assertEquals(33_33L, ShareSplit.share(100_00L, 3))
        assertEquals(66_67L, ShareSplit.share(200_00L, 3))
        assertEquals(25_00L, ShareSplit.share(100_00L, 4))
        assertEquals(53_70L, ShareSplit.share(214_79L, 4))
        assertEquals(107_40L, ShareSplit.share(214_79L, 2))
    }

    @Test
    fun `edge amounts`() {
        assertEquals(0L, ShareSplit.share(0L, 2))
        assertEquals(0L, ShareSplit.share(-5L, 2))
        assertEquals(1L, ShareSplit.share(1L, 2))
        assertEquals(80_00L, ShareSplit.share(80_00L, 1))
    }

    @Test
    fun `a stored share is recognised as one of the splits`() {
        assertEquals(2, ShareSplit.partsOf(100_00L, 50_00L))
        assertEquals(3, ShareSplit.partsOf(100_00L, 33_33L))
        assertEquals(4, ShareSplit.partsOf(100_00L, 25_00L))
        assertNull(ShareSplit.partsOf(100_00L, 40_00L))
        assertNull(ShareSplit.partsOf(100_00L, 100_00L))
        assertNull(ShareSplit.partsOf(0L, 0L))
    }
}
