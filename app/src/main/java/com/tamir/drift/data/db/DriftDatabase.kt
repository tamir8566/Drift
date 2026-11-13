package com.tamir.drift.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        JobEntity::class,
        ShiftEntity::class,
        PlannedDayEntity::class,
    ],
    version = 3,
    exportSchema = false,
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
    abstract fun jobs(): JobDao
    abstract fun shifts(): ShiftDao
    abstract fun plannedDays(): PlannedDayDao

    companion object {
        /** v1 → v2: work & goal tables (jobs, shifts, planned_days). Existing data is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `jobs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`hourlyRateAgorot` INTEGER NOT NULL, " +
                        "`shabbatMultiplier` REAL NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shifts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`jobId` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`startTime` INTEGER, " +
                        "`endTime` INTEGER, " +
                        "`hours` REAL NOT NULL, " +
                        "`isShabbatRate` INTEGER NOT NULL, " +
                        "`amountAgorot` INTEGER NOT NULL, " +
                        "`manualAmount` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_jobId` ON `shifts` (`jobId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_date` ON `shifts` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `planned_days` (" +
                        "`date` INTEGER NOT NULL, " +
                        "`jobId` INTEGER, " +
                        "PRIMARY KEY(`date`))",
                )
            }
        }

        /** v2 → v3: job pay type (per hour / per day) and the daily rate. Existing jobs stay hourly. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jobs` ADD COLUMN `payType` TEXT NOT NULL DEFAULT 'HOURLY'")
                db.execSQL("ALTER TABLE `jobs` ADD COLUMN `dailyRateAgorot` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): DriftDatabase =
            Room.databaseBuilder(context.applicationContext, DriftDatabase::class.java, "drift.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // Safety net for any other version jump (was already the policy before v2).
                .fallbackToDestructiveMigration()
                .build()
    }
}
