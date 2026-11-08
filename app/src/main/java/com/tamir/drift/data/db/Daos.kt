package com.tamir.drift.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY isUnparsed DESC, timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to")
    suspend fun getBetween(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE source != 'MANUAL' AND isUnparsed = 0 AND amountFull = :amountFull " +
            "AND timestamp BETWEEN :from AND :to",
    )
    suspend fun findSameAmountAround(amountFull: Long, from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE merchant = :merchant")
    suspend fun getByMerchant(merchant: String): List<TransactionEntity>

    @Insert
    suspend fun insert(t: TransactionEntity): Long

    @Update
    suspend fun update(t: TransactionEntity)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE merchant = :merchant AND merchant != ''")
    suspend fun recategorizeMerchant(merchant: String, categoryId: String)

    @Delete
    suspend fun delete(t: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RawCaptureDao {
    @Query("SELECT * FROM raw_captures ORDER BY capturedAt DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<RawCaptureEntity>>

    @Query("SELECT * FROM raw_captures WHERE transactionId = :transactionId ORDER BY capturedAt")
    suspend fun getForTransaction(transactionId: Long): List<RawCaptureEntity>

    @Query("SELECT * FROM raw_captures WHERE id = :id")
    suspend fun getById(id: Long): RawCaptureEntity?

    @Insert
    suspend fun insert(r: RawCaptureEntity): Long

    @Update
    suspend fun update(r: RawCaptureEntity)

    /** Keeps only the newest [keep] rows (PRD: last 20). */
    @Query(
        "DELETE FROM raw_captures WHERE id NOT IN (SELECT id FROM raw_captures ORDER BY capturedAt DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<CategoryEntity>)

    @Query("UPDATE categories SET monthlyBudget = :budget WHERE id = :id")
    suspend fun setBudget(id: String, budget: Long?)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(list: List<MerchantRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("SELECT COUNT(*) FROM merchant_rules WHERE origin = 'BUILTIN'")
    suspend fun builtinCount(): Int
}

@Dao
interface ParsePatternDao {
    @Query("SELECT * FROM parse_patterns ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ParsePatternEntity>>

    @Query("SELECT * FROM parse_patterns ORDER BY createdAt DESC")
    suspend fun getAll(): List<ParsePatternEntity>

    @Insert
    suspend fun insert(p: ParsePatternEntity): Long

    @Query("DELETE FROM parse_patterns WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY month")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY month")
    suspend fun getAll(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(b: BudgetEntity)
}

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings_goals ORDER BY targetDate")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getGoal(id: Long): SavingsGoalEntity?

    @Query("SELECT * FROM savings_deposits ORDER BY timestamp DESC")
    fun observeDeposits(): Flow<List<SavingsDepositEntity>>

    @Insert
    suspend fun insertGoal(g: SavingsGoalEntity): Long

    @Update
    suspend fun updateGoal(g: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM savings_deposits WHERE goalId = :goalId")
    suspend fun deleteDepositsOf(goalId: Long)

    @Insert
    suspend fun insertDeposit(d: SavingsDepositEntity): Long

    @Query("DELETE FROM savings_deposits WHERE id = :id")
    suspend fun deleteDeposit(id: Long)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY isActive DESC, createdAt")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs ORDER BY isActive DESC, createdAt")
    suspend fun getAll(): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: Long): JobEntity?

    @Query("SELECT COUNT(*) FROM jobs")
    suspend fun count(): Int

    @Insert
    suspend fun insert(j: JobEntity): Long

    @Update
    suspend fun update(j: JobEntity)

    @Query("UPDATE jobs SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE date >= :fromDay AND date <= :toDay ORDER BY date DESC, createdAt DESC")
    suspend fun getBetween(fromDay: Long, toDay: Long): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: Long): ShiftEntity?

    @Insert
    suspend fun insert(s: ShiftEntity): Long

    @Update
    suspend fun update(s: ShiftEntity)

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PlannedDayDao {
    @Query("SELECT * FROM planned_days ORDER BY date")
    fun observeAll(): Flow<List<PlannedDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PlannedDayEntity)

    @Query("DELETE FROM planned_days WHERE date = :date")
    suspend fun delete(date: Long)

    @Query("SELECT COUNT(*) FROM planned_days WHERE date = :date")
    suspend fun countAt(date: Long): Int
}

@Dao
interface AlertSentDao {
    @Query("SELECT `key` FROM alerts_sent WHERE `key` LIKE :monthPrefix || '%'")
    suspend fun keysForMonth(monthPrefix: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(a: AlertSentEntity)

    @Query("DELETE FROM alerts_sent WHERE sentAt < :before")
    suspend fun pruneOlderThan(before: Long)
}
