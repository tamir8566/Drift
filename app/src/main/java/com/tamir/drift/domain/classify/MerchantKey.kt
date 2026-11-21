package com.tamir.drift.domain.classify

/**
 * The one merchant normaliser of the app. Classification, insights (top merchants, "where to
 * save"), subscription detection, duplicate detection and recategorisation all compare merchants
 * through [normalize], so "שופרסל דיל 123", "שופרסל-דיל" and "שופרסל דיל בע"מ" are one merchant.
 */
object MerchantKey {

    private val quotes = Regex("""['"״׳`’‘“”]""")
    private val punctuation = Regex("""[\-־–—_.,*()\[\]/\\:;#|!?]+""")
    private val spaces = Regex("""\s+""")
    private val digitsOnly = Regex("""\d+""")
    private val companySuffixes = setOf("בעמ", "ltd")

    /**
     * Lowercase, quotes / gershayim removed, punctuation and maqaf turned into spaces, spaces
     * collapsed. Keeps every word — used for the built-in keywords ("כביש 6", "019").
     */
    fun clean(s: String): String =
        s.lowercase().replace(quotes, "").replace(punctuation, " ").replace(spaces, " ").trim()

    /**
     * [clean] plus: trailing branch numbers and a trailing "בע"מ" are dropped (never the last
     * remaining word). Idempotent.
     */
    fun normalize(s: String): String {
        val tokens = clean(s).split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (tokens.size > 1) {
            val last = tokens.last()
            if (digitsOnly.matches(last) || last in companySuffixes) {
                tokens.removeAt(tokens.lastIndex)
            } else if (last == "מ" && tokens.size > 2 && tokens[tokens.lastIndex - 1] == "בע") {
                // "בע.מ" / "בע מ" — the two halves of בע"מ written apart.
                tokens.removeAt(tokens.lastIndex)
                tokens.removeAt(tokens.lastIndex)
            } else {
                break
            }
        }
        return tokens.joinToString(" ")
    }

    /** True when both names are the same merchant (and not blank). */
    fun same(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val ka = normalize(a)
        return ka.isNotEmpty() && ka == normalize(b)
    }
}
