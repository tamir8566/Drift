package com.tamir.drift.data.repo

import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.db.BudgetEntity
import com.tamir.drift.domain.budget.BudgetCalculator
import com.tamir.drift.domain.budget.MonthSummary
import com.tamir.drift.domain.time.MonthPeriod
import java.time.YearMonth
import java.time.ZoneId

/** Builds the PRD month summary from entities (shared by the UI and the alert checker). */
class MonthSummaryUseCase(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun build(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        month: YearMonth,
        nowMillis: Long = System.currentTimeMillis(),
    ): MonthSummary {
        val period = MonthPeriod(month, zone)
        val totalBudget = Money.toShekels(BudgetRepository.resolve(budgets, month))
        val categoryBudgets = categories.mapNotNull { c -> c.monthlyBudget?.let { c.id to Money.toShekels(it) } }.toMap()
        return BudgetCalculator.summarize(
            period = period,
            transactions = transactions.toSummaries(),
            totalBudget = totalBudget,
            categoryBudgets = categoryBudgets,
            nowMillis = nowMillis,
            categoryOrder = categories.map { it.id },
        )
    }
}
