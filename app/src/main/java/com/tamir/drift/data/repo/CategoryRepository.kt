package com.tamir.drift.data.repo

import android.content.Context
import com.tamir.drift.R
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.db.MerchantRuleEntity
import com.tamir.drift.data.db.Origin
import com.tamir.drift.domain.classify.MerchantClassifier
import com.tamir.drift.domain.classify.MerchantKey
import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.MerchantRuleModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Categories, category budgets and merchant → category rules (built-in + learned). */
class CategoryRepository(private val context: Context, private val db: DriftDatabase) {

    val categories: Flow<List<CategoryEntity>> = db.categories().observeAll()

    /** Map id → entity, handy for the UI. */
    val categoryMap: Flow<Map<String, CategoryEntity>> = categories.map { list -> list.associateBy { it.id } }

    val rules: Flow<List<MerchantRuleEntity>> = db.merchantRules().observeAll()

    /**
     * Makes sure the default categories and the built-in merchant rules exist. Runs on every
     * start and only adds what is missing (existing rows — also a learned rule that uses the same
     * keyword — are never touched), so a keyword or a category added to the code in a later
     * version reaches an install that was seeded long ago.
     */
    suspend fun ensureSeeded() {
        db.categories().insertAll(defaultCategories(context))
        db.merchantRules().insertIgnore(MerchantClassifier.SEED_RULES.map { it.toBuiltinEntity() })
    }

    private fun MerchantRuleModel.toBuiltinEntity() = MerchantRuleEntity(keyword, categoryKey, Origin.BUILTIN)

    /**
     * Deletes a learned rule. A learned rule is stored under the merchant's key, which can be the
     * very keyword of a built-in rule ("וולט") and then took its place; deleting it brings the
     * built-in rule back instead of leaving the merchant without any rule.
     */
    suspend fun deleteLearnedRule(keyword: String) {
        db.merchantRules().delete(keyword)
        MerchantClassifier.SEED_RULES.firstOrNull { it.keyword == keyword }?.let { seed ->
            db.merchantRules().insertIgnore(listOf(seed.toBuiltinEntity()))
        }
    }

    /** The undo of [deleteLearnedRule]: the learned rule is stored again (over a built-in one with the same keyword). */
    suspend fun restoreLearnedRule(rule: MerchantRuleEntity) = db.merchantRules().upsert(rule)

    suspend fun classifier(): MerchantClassifier = MerchantClassifier(
        db.merchantRules().getAll().map { MerchantRuleModel(it.keyword, it.categoryId, it.origin == Origin.LEARNED) },
    )

    suspend fun getAll(): List<CategoryEntity> = db.categories().getAll()

    suspend fun setCategoryBudget(id: String, agorot: Long?) = db.categories().setBudget(id, agorot)

    /**
     * PRD: a manual category change teaches a rule on the merchant that beats the built-in one.
     * The rule is stored under the merchant's [MerchantKey] and matches by equality / prefix.
     */
    suspend fun learnRule(merchant: String, categoryId: String) {
        val keyword = MerchantKey.normalize(merchant)
        if (keyword.isBlank()) return
        // An older learned rule for the same merchant stored under another spelling (before the
        // shared key existed) would compete with the new choice — remove it.
        db.merchantRules().getAll()
            .filter { it.origin == Origin.LEARNED && it.keyword != keyword && MerchantKey.normalize(it.keyword) == keyword }
            .forEach { db.merchantRules().delete(it.keyword) }
        db.merchantRules().upsert(MerchantRuleEntity(keyword, categoryId, Origin.LEARNED))
    }

    companion object {
        // Palette only (PRD forbids colors outside it): lime, teal, orange and their softer
        // variants, plus the text tones.
        private const val LIME = 0xFFC5F36FL
        private const val TEAL = 0xFF2EE6C5L
        private const val ORANGE = 0xFFFFB26BL
        private const val LIME_SOFT = 0x99C5F36FL
        private const val TEAL_SOFT = 0x992EE6C5L
        private const val ORANGE_SOFT = 0x99FFB26BL
        private const val TEXT = 0xFFEEF3ECL
        private const val TEXT_SECONDARY = 0xFF8FA89BL
        private const val TEXT_DIM = 0xFF6F857AL

        fun defaultCategories(context: Context): List<CategoryEntity> = listOf(
            CategoryEntity(CategoryKeys.GROCERIES, context.getString(R.string.cat_groceries), LIME, 0),
            CategoryEntity(CategoryKeys.EATING_OUT, context.getString(R.string.cat_eating_out), ORANGE, 1),
            CategoryEntity(CategoryKeys.TRANSPORT, context.getString(R.string.cat_transport), TEAL, 2),
            CategoryEntity(CategoryKeys.SUBSCRIPTIONS, context.getString(R.string.cat_subscriptions), LIME_SOFT, 3),
            CategoryEntity(CategoryKeys.LEISURE, context.getString(R.string.cat_leisure), TEAL_SOFT, 4),
            CategoryEntity(CategoryKeys.CLOTHING, context.getString(R.string.cat_clothing), ORANGE_SOFT, 5),
            CategoryEntity(CategoryKeys.HEALTH, context.getString(R.string.cat_health), TEXT, 6),
            CategoryEntity(CategoryKeys.HOME, context.getString(R.string.cat_home), TEXT_SECONDARY, 7),
            CategoryEntity(CategoryKeys.OTHER, context.getString(R.string.cat_other), TEXT_DIM, 8),
        )
    }
}
