package com.tamir.drift.domain.backup

/**
 * A very small JSON reader and string writer for the two text files inside a backup
 * (`meta.json`, `settings.json`). Pure Kotlin, so the backup format is covered by unit tests.
 *
 * [parse] returns the usual tree: `Map<String, Any?>` for an object, `List<Any?>` for an array,
 * `String`, `Long` (a number without a fraction or exponent), `Double`, `Boolean` or null.
 * Anything malformed throws [IllegalArgumentException]; use [parseOrNull] to get null instead.
 */
object MiniJson {

    fun parseOrNull(text: String): Any? = try {
        parse(text)
    } catch (e: IllegalArgumentException) {
        null
    }

    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.skipSpace()
        val value = reader.readValue(0)
        reader.skipSpace()
        if (!reader.atEnd()) reader.fail("unexpected text after the value")
        return value
    }

    /** [text] as a JSON string literal, quotes included. */
    fun quote(text: String): String {
        val out = StringBuilder(text.length + 2)
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }

    private const val MAX_DEPTH = 32

    private class Reader(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun fail(message: String): Nothing = throw IllegalArgumentException("$message (at $pos)")

        fun skipSpace() {
            while (pos < text.length && (text[pos] == ' ' || text[pos] == '\n' || text[pos] == '\r' || text[pos] == '\t')) pos++
        }

        fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("nested too deep")
            if (atEnd()) fail("unexpected end")
            val c = text[pos]
            return when {
                c == '{' -> readObject(depth)
                c == '[' -> readArray(depth)
                c == '"' -> readString()
                c == 't' -> readWord("true", true)
                c == 'f' -> readWord("false", false)
                c == 'n' -> readWord("null", null)
                c == '-' || c in '0'..'9' -> readNumber()
                else -> fail("unexpected character")
            }
        }

        private fun readWord(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) fail("unexpected word")
            pos += word.length
            return value
        }

        private fun readNumber(): Any {
            val start = pos
            if (text[pos] == '-') pos++
            while (pos < text.length && (text[pos] in '0'..'9' || text[pos] == '.' || text[pos] == 'e' || text[pos] == 'E' || text[pos] == '+' || text[pos] == '-')) pos++
            val raw = text.substring(start, pos)
            val whole: Long? = raw.toLongOrNull()
            if (whole != null) return whole
            return raw.toDoubleOrNull() ?: fail("bad number")
        }

        private fun readString(): String {
            pos++ // the opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) fail("string without an end")
                val c = text[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) fail("string without an end")
                        when (val e = text[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (pos + 4 > text.length) fail("bad escape")
                                val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("bad escape")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("bad escape '$e'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            pos++ // [
            val items = ArrayList<Any?>()
            skipSpace()
            if (!atEnd() && text[pos] == ']') { pos++; return items }
            while (true) {
                skipSpace()
                items.add(readValue(depth + 1))
                skipSpace()
                if (atEnd()) fail("array without an end")
                when (text[pos++]) {
                    ',' -> Unit
                    ']' -> return items
                    else -> fail("expected , or ]")
                }
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            pos++ // {
            val fields = LinkedHashMap<String, Any?>()
            skipSpace()
            if (!atEnd() && text[pos] == '}') { pos++; return fields }
            while (true) {
                skipSpace()
                if (atEnd() || text[pos] != '"') fail("expected a field name")
                val name = readString()
                skipSpace()
                if (atEnd() || text[pos] != ':') fail("expected :")
                pos++
                skipSpace()
                fields[name] = readValue(depth + 1)
                skipSpace()
                if (atEnd()) fail("object without an end")
                when (text[pos++]) {
                    ',' -> Unit
                    '}' -> return fields
                    else -> fail("expected , or }")
                }
            }
        }
    }
}
