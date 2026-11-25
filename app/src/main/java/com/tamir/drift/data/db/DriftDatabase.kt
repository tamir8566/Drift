package com.tamir.drift.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The schema version. Raising it requires a migration from the previous version in
 * [DriftDatabase.MIGRATIONS]; without one the app refuses to open the database.
 */
const val DRIFT_DB_VERSION = 5

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
    version = DRIFT_DB_VERSION,
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

        /**
         * v3 → v4: travel reimbursement — the per-shift amount on a job and the amount applied to
         * each shift. Existing jobs and shifts get 0 (no reimbursement).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jobs` ADD COLUMN `travelPerShiftAgorot` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `travelAgorot` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v4 → v5:
         *  - transactions: `kind` (EXPENSE / REFUND / TRANSFER; existing rows are expenses), the
         *    original foreign amount (`origAmountMinor`, `origCurrency`) and `isEstimate`;
         *  - shifts: the rate and Shabbat multiplier the shift was paid at (nullable). Existing
         *    shifts are given their job's rate as it is at migration time, so that from now on a
         *    change of the job's rate no longer rewrites them; a shift whose job row is gone stays
         *    NULL and falls back to the current rate;
         *  - jobs: `isMain` (no job is marked; the automatic choice applies until the user marks one).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `origAmountMinor` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `origCurrency` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isEstimate` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `rateAgorotAtShift` INTEGER")
                db.execSQL("ALTER TABLE `shifts` ADD COLUMN `multiplierAtShift` REAL")
                db.execSQL("ALTER TABLE `jobs` ADD COLUMN `isMain` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE `shifts` SET " +
                        "`rateAgorotAtShift` = (SELECT CASE WHEN `jobs`.`payType` = 'DAILY' THEN `jobs`.`dailyRateAgorot` " +
                        "ELSE `jobs`.`hourlyRateAgorot` END FROM `jobs` WHERE `jobs`.`id` = `shifts`.`jobId`), " +
                        "`multiplierAtShift` = (SELECT `jobs`.`shabbatMultiplier` FROM `jobs` WHERE `jobs`.`id` = `shifts`.`jobId`)",
                )
            }
        }

        /** The database file name (under the app's databases directory). */
        const val NAME = "drift.db"

        /** Every version step up to [DRIFT_DB_VERSION] (a unit test checks that none is missing). */
        val MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        /**
         * There is no destructive fallback on purpose. A version step without a migration (or a
         * database written by a newer build) makes opening fail loudly instead of silently
         * erasing the user's data; the data stays on the device until a fixed build is installed.
         */
        fun build(context: Context): DriftDatabase =
            Room.databaseBuilder(context.applicationContext, DriftDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        /**
         * The same database over another file — used to try a backup's database before it replaces
         * the real one. Opening it runs the migrations and Room's schema check; there is no
         * destructive fallback here, so a file that cannot be brought to the current schema fails
         * to open instead of being silently emptied.
         */
        fun buildForCheck(context: Context, fileName: String): DriftDatabase =
            Room.databaseBuilder(context.applicationContext, DriftDatabase::class.java, fileName)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
