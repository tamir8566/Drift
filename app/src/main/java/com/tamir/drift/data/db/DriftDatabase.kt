package com.tamir.drift.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TransactionEntity::class,
        RawCaptureEntity::class,
        CategoryEntity::class,
        MerchantRuleEntity::class,
        ParsePatternEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        SavingsDepositEntity::class,
        AlertSentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DriftDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun rawCaptures(): RawCaptureDao
    abstract fun categories(): CategoryDao
    abstract fun merchantRules(): MerchantRuleDao
    abstract fun parsePatterns(): ParsePatternDao
    abstract fun budgets(): BudgetDao
    abstract fun savings(): SavingsDao
    abstract fun alertsSent(): AlertSentDao

    companion object {
        fun build(context: Context): DriftDatabase =
            Room.databaseBuilder(context.applicationContext, DriftDatabase::class.java, "drift.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
