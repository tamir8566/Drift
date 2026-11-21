package com.tamir.drift.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `objects arrays and scalars are parsed`() {
        val parsed = MiniJson.parse(" {\"a\": 1, \"b\": [true, false, null, -2.5, \"x\"], \"c\": {\"d\": {}}, \"e\": []} ")
        assertEquals(
            mapOf("a" to 1L, "b" to listOf(true, false, null, -2.5, "x"), "c" to mapOf("d" to emptyMap<String, Any?>()), "e" to emptyList<Any?>()),
            parsed,
        )
    }

    @Test
    fun `a quoted string reads back as it was written`() {
        val text = "שורה \"אחת\"\nשורה\\שתיים\t\u0001 סוף"
        assertEquals(text, MiniJson.parse(MiniJson.quote(text)))
    }

    @Test
    fun `escapes are understood`() {
        assertEquals("a/bא\n", MiniJson.parse("\"a\\/b\\u05d0\\n\""))
    }

    @Test
    fun `large whole numbers stay exact`() {
        assertEquals(1_795_000_000_123L, MiniJson.parse("1795000000123"))
    }

    @Test
    fun `malformed text gives null`() {
        assertNull(MiniJson.parseOrNull(""))
        assertNull(MiniJson.parseOrNull("{"))
        assertNull(MiniJson.parseOrNull("{\"a\":}"))
        assertNull(MiniJson.parseOrNull("[1,]"))
        assertNull(MiniJson.parseOrNull("{\"a\":1} x"))
        assertNull(MiniJson.parseOrNull("\"open"))
        assertNull(MiniJson.parseOrNull("tru"))
        assertNull(MiniJson.parseOrNull("{a:1}"))
        assertNull(MiniJson.parseOrNull("[".repeat(200)))
    }
}
