package com.tamir.drift.data.repo

import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.db.BudgetEntity
import com.tamir.drift.domain.budget.BudgetCalculator
import com.tamir.drift.domain.budget.MonthSummary
import com.tamir.drift.domain.time.MonthPeriod
import com.tamir.drift.domain.time.TrackingWindow
import com.tamir.drift.domain.time.sinceStart
import java.time.YearMonth
import java.time.ZoneId

/**
 * Builds the PRD month summary from entities (shared by the UI, the alert checker and the weekly
 * summary). Only transactions inside [window] (the tracking start) are counted — including the
 * previous-month / previous-week comparisons, which therefore show "—" before the start.
 */
class MonthSummaryUseCase(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun build(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        month: YearMonth,
        window: TrackingWindow,
        nowMillis: Long = System.currentTimeMillis(),
    ): MonthSummary {
        val period = MonthPeriod(month, zone)
        val totalBudget = Money.toShekels(BudgetRepository.resolve(budgets, month))
        val categoryBudgets = categories.mapNotNull { c -> c.monthlyBudget?.let { c.id to Money.toShekels(it) } }.toMap()
        return BudgetCalculator.summarize(
            period = period,
            transactions = transactions.toSummaries().sinceStart(window, zone),
            totalBudget = totalBudget,
            categoryBudgets = categoryBudgets,
            nowMillis = nowMillis,
            categoryOrder = categories.map { it.id },
        )
    }
}
