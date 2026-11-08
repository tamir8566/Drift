package com.tamir.drift.domain.parser

/** Removes bidi control characters and collapses whitespace so regexes behave predictably. */
object TextNormalizer {
    private val bidiMarks = Regex("""[‎‏‪-‮⁦-⁩؜﻿]""")
    private val spaces = Regex("""[ \t ]+""")

    fun normalize(text: String): String =
        text.replace(bidiMarks, "")
            .replace(spaces, " ")
            .replace("\r\n", "\n")
            .trim()

    /** Cleans a merchant name: trims punctuation, collapses spaces, limits length. */
    fun cleanMerchant(raw: String?): String? {
        if (raw == null) return null
        val cleaned = normalize(raw)
            .trim { it.isWhitespace() || it in ".,;:-–—*'\"״׳()[]" }
            .replace(spaces, " ")
        if (cleaned.isBlank()) return null
        return cleaned.take(60)
    }
}
