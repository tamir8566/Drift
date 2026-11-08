package com.tamir.drift.data.repo

import android.content.Context
import com.tamir.drift.R
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.db.MerchantRuleEntity
import com.tamir.drift.data.db.Origin
import com.tamir.drift.domain.classify.MerchantClassifier
import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.MerchantRuleModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Categories, category budgets and merchant → category rules (built-in + learned). */
class CategoryRepository(private val context: Context, private val db: DriftDatabase) {

    val categories: Flow<List<CategoryEntity>> = db.categories().observeAll()

    /** Map id → entity, handy for the UI. */
    val categoryMap: Flow<Map<String, CategoryEntity>> = categories.map { list -> list.associateBy { it.id } }

    val rules: Flow<List<MerchantRuleEntity>> = db.merchantRules().observeAll()

    /** Seeds the default categories and the built-in merchant rules on first run. */
    suspend fun ensureSeeded() {
        if (db.categories().count() == 0) {
            db.categories().insertAll(defaultCategories(context))
        }
        if (db.merchantRules().builtinCount() == 0) {
            db.merchantRules().insertIgnore(
                MerchantClassifier.SEED_RULES.map { MerchantRuleEntity(it.keyword, it.categoryKey, Origin.BUILTIN) },
            )
        }
    }

    suspend fun classifier(): MerchantClassifier = MerchantClassifier(
        db.merchantRules().getAll().map { MerchantRuleModel(it.keyword, it.categoryId, it.origin == Origin.LEARNED) },
    )

    suspend fun getAll(): List<CategoryEntity> = db.categories().getAll()

    suspend fun setCategoryBudget(id: String, agorot: Long?) = db.categories().setBudget(id, agorot)

    /** PRD: a manual category change teaches a rule on the merchant that beats the built-in one. */
    suspend fun learnRule(merchant: String, categoryId: String) {
        val keyword = MerchantClassifier.normalize(merchant)
        if (keyword.isBlank()) return
        db.merchantRules().upsert(MerchantRuleEntity(keyword, categoryId, Origin.LEARNED))
    }

    suspend fun categoryBudgets(): Map<String, Double> =
        db.categories().getAll().mapNotNull { c -> c.monthlyBudget?.let { c.id to Money.toShekels(it) } }.toMap()

    suspend fun order(): List<String> = categories.first().map { it.id }

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
