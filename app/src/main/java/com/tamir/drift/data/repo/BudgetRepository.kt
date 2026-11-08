package com.tamir.drift.data.repo

import com.tamir.drift.data.db.BudgetEntity
import com.tamir.drift.data.db.DriftDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

/**
 * Monthly total budget. PRD: default 5,000 ₪; a change applies from the current month onward,
 * so each month resolves to the newest budget row whose month is ≤ that month.
 */
class BudgetRepository(private val db: DriftDatabase) {

    val all: Flow<List<BudgetEntity>> = db.budgets().observeAll()

    fun observeBudgetFor(month: YearMonth): Flow<Long> = all.map { resolve(it, month) }

    suspend fun budgetFor(month: YearMonth): Long = resolve(db.budgets().getAll(), month)

    suspend fun setBudgetFrom(month: YearMonth, agorot: Long) {
        db.budgets().upsert(BudgetEntity(month.toString(), agorot))
    }

    companion object {
        const val DEFAULT_AGOROT = 5_000_00L

        fun resolve(rows: List<BudgetEntity>, month: YearMonth): Long {
            val key = month.toString()
            return rows.filter { it.month <= key }.maxByOrNull { it.month }?.total ?: DEFAULT_AGOROT
        }
    }
}
