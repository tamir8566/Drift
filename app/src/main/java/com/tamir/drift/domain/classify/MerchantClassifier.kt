package com.tamir.drift.domain.classify

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.MerchantRuleModel

/**
 * Assigns a category to a merchant name using keyword rules.
 *
 * User-defined rules win over seeded rules; among rules of the same kind the longest
 * keyword wins (more specific). Matching is case-insensitive and ignores punctuation,
 * so "Rav-Kav", "רב קו" and "רב-קו" all behave the same.
 */
class MerchantClassifier(rules: List<MerchantRuleModel>) {

    private val sorted = rules
        .map { it.copy(keyword = normalize(it.keyword)) }
        .filter { it.keyword.isNotBlank() }
        .sortedWith(compareByDescending<MerchantRuleModel> { it.isUserDefined }.thenByDescending { it.keyword.length })

    /** Returns the category key, or [CategoryKeys.OTHER] when no rule matches. */
    fun classify(merchant: String?): String = matchRule(merchant)?.categoryKey ?: CategoryKeys.OTHER

    /** Returns the matching rule (useful to show the user *why*), or null. */
    fun matchRule(merchant: String?): MerchantRuleModel? {
        if (merchant.isNullOrBlank()) return null
        val m = normalize(merchant)
        return sorted.firstOrNull { rule -> matches(m, rule.keyword) }
    }

    /** Short keywords (≤ 3 chars) must match a whole word to avoid false positives. */
    private fun matches(merchant: String, keyword: String): Boolean =
        if (keyword.length <= 3) {
            merchant.split(' ').any { it == keyword }
        } else {
            merchant.contains(keyword)
        }

    companion object {
        private val punctuation = Regex("""[\-־_.,'"״׳*()\[\]/\\]+""")
        private val spaces = Regex("""\s+""")

        fun normalize(s: String): String =
            s.lowercase().replace(punctuation, " ").replace(spaces, " ").trim()

        /** Seed rules for common Israeli merchants. Keyword → category. */
        val SEED_RULES: List<MerchantRuleModel> = buildList {
            fun seed(category: String, vararg keywords: String) {
                keywords.forEach { add(MerchantRuleModel(it, category, isUserDefined = false)) }
            }
            seed(
                CategoryKeys.GROCERIES,
                "שופרסל", "shufersal", "רמי לוי", "rami levy", "ויקטורי", "victory", "יוחננוף", "yochananof",
                "מגה", "יינות ביתן", "אושר עד", "osher ad", "טיב טעם", "tiv taam", "am:pm", "am pm", "ampm",
                "סופר", "מינימרקט", "מכולת", "חצי חינם", "זול ובגדול",
            )
            seed(
                CategoryKeys.EATING_OUT,
                "וולט", "wolt", "תן ביס", "10bis", "tenbis", "מקדונלד", "mcdonald", "ארומה", "aroma", "קפה",
                "cafe", "coffee", "בורגר", "burger", "פיצה", "pizza", "סושי", "sushi", "מסעדה", "restaurant",
                "דומינוס", "domino", "קופיקס", "cofix", "לנדוור", "גרג", "greg", "שגב", "בי בי בי", "bbb",
                "מוזס", "moses", "שווארמה", "פלאפל", "ג'פניקה", "japanika", "yango deli", "יאנגו דלי",
            )
            seed(
                CategoryKeys.TRANSPORT,
                "רב קו", "rav kav", "ravkav", "מוביט", "moovit", "גט", "gett", "יאנגו", "yango", "אובר", "uber",
                "פז", "paz", "יילו", "yellow", "פז אפליקציית יילו", "סונול", "sonol", "דלק", "delek", "דור אלון", "dor alon", "רכבת",
                "rail", "אגד", "egged", "מטרופולין", "קווים", "סופרבוס", "חניה", "parking", "פנגו", "pango",
                "סלופארק", "cellopark", "ליים", "lime", "בירד", "bird", "כביש 6", "דרך ארץ",
            )
            seed(
                CategoryKeys.SUBSCRIPTIONS,
                "ספוטיפיי", "spotify", "נטפליקס", "netflix", "יוטיוב", "youtube", "גוגל", "google", "אפל", "apple",
                "icloud", "סלקום", "cellcom", "פרטנר", "partner", "הוט", "בזק", "bezeq", "פלאפון",
                "pelephone", "גולן", "golan", "רמי לוי תקשורת", "019", "012", "disney", "דיסני", "amazon prime",
                "chatgpt", "openai", "claude", "anthropic", "microsoft", "מיקרוסופט", "dropbox", "notion",
                "הולמס פלייס", "holmes place", "חדר כושר", "gym",
            )
            seed(
                CategoryKeys.LEISURE,
                "סינמה", "cinema", "יס פלאנט", "yes planet", "רב חן", "hot cinema", "פאב", "pub",
                "steam", "playstation", "xbox", "nintendo", "טיקטים", "tickets", "eventim", "לאומי קארד",
                "ביט", "bit", "פייבוקס", "paybox", "פייפאל", "paypal", "קניון", "mall", "בריכה", "pool",
                "לונה פארק", "אסקייפ", "escape", "באולינג", "bowling",
            )
            seed(
                CategoryKeys.CLOTHING,
                "זארה", "zara", "קסטרו", "castro", "פוקס", "fox", "h&m", "h and m", "רנואר", "renuar", "גולף",
                "golf", "אמריקן איגל", "american eagle", "טרמינל", "terminal x", "נייקי", "nike", "אדידס", "adidas",
                "shein", "שיין", "asos", "מנגו", "mango", "pull&bear", "bershka", "ברשקה", "נעלי", "shoes",
            )
            seed(
                CategoryKeys.HEALTH,
                "סופר פארם", "super pharm", "superpharm", "בי דראגסטור", "be drugstore", "ניו פארם", "new pharm",
                "בית מרקחת", "pharm", "מכבי", "maccabi", "כללית", "clalit", "מאוחדת", "meuhedet", "לאומית",
                "רופא", "clinic", "מרפאה", "שיניים", "dental", "אופטיקה", "optic", "משקפיים",
            )
            seed(
                CategoryKeys.HOME,
                "איקאה", "ikea", "הום סנטר", "home center", "חשמל", "electric", "חברת החשמל",
                "מקורות", "עירייה", "ארנונה", "ועד בית", "שכירות", "שכר דירה", "סלון",
                "מזרונים", "כלי בית", "kitchen", "ksp", "אלקטרה", "electra", "מחסני חשמל",
            )
        }
    }
}
