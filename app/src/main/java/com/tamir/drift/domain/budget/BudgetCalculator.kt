package com.tamir.drift.domain.budget

import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.time.MonthPeriod
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Per-category spend inside a month. All amounts are "my share" (amountMine). */
data class CategorySpend(
    val categoryKey: String,
    val spent: Double,
    /** Null when the user did not set a budget for this category. */
    val budget: Double?,
    val count: Int,
    /** Share of the month's total spend, 0..1 (used for the bar when no budget is set). */
    val shareOfTotal: Double,
) {
    /** Percent used against the category budget, or null when there is no budget. */
    val percentOfBudget: Double? get() = budget?.takeIf { it > 0 }?.let { spent / it * 100.0 }

    /** The value the bar/percent shows: budget usage when a budget exists, else share of total. */
    val displayPercent: Double get() = percentOfBudget ?: (shareOfTotal * 100.0)

    /** PRD: a category is highlighted in orange above 85% of its budget. */
    val isWarning: Boolean get() = (percentOfBudget ?: 0.0) > 85.0

    val remaining: Double? get() = budget?.let { it - spent }
}

/** Everything the home and budget screens need for one month. */
data class MonthSummary(
    val period: MonthPeriod,
    val totalBudget: Double,
    val spent: Double,
    val transactionCount: Int,
    val dayOfMonth: Int,
    val daysInMonth: Int,
    val categories: List<CategorySpend>,
    /** Spend of the previous month up to the same day of month (fair comparison). */
    val previousMonthSpentToSameDay: Double,
    /** Spend of the full previous month. */
    val previousMonthSpent: Double,
    /** Last 7 days (my share). */
    val weekSpent: Double,
    /** The 7 days before those. */
    val previousWeekSpent: Double,
) {
    /** PRD: remaining = budget − spent; may be negative (shown in orange). */
    val remaining: Double get() = totalBudget - spent

    /** PRD: percent used = spent / budget. */
    val percentUsed: Double get() = if (totalBudget > 0) spent / totalBudget * 100.0 else 0.0

    /** Days left including today. */
    val daysLeft: Int get() = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)

    /** PRD: allowed per day = remaining / days left (incl. today); negative → 0 + "exceeded". */
    val allowedPerDay: Double get() = if (remaining <= 0.0) 0.0 else remaining / daysLeft

    val isOverBudget: Boolean get() = remaining < 0.0

    /** Share of the month that has elapsed, in percent (day of month / days in month). */
    val monthElapsedPercent: Double get() = dayOfMonth.toDouble() / daysInMonth * 100.0

    /** PRD: percent used < percent of month elapsed → good pace, otherwise too fast. */
    val isGoodPace: Boolean get() = percentUsed < monthElapsedPercent

    /** Week vs the previous week, in percent (null when the previous week had no spend). */
    val weekChangePercent: Double? get() = changePercent(weekSpent, previousWeekSpent)

    /** This month vs previous month up to the same day, in percent. */
    val monthChangePercent: Double? get() = changePercent(spent, previousMonthSpentToSameDay)

    private fun changePercent(current: Double, previous: Double): Double? =
        if (previous > 0.0) (current - previous) / previous * 100.0 else null
}

/** Budget alert thresholds, in percent. */
enum class AlertLevel(val percent: Int) { WARNING(80), EXCEEDED(100) }

data class BudgetAlert(
    /** Null = total budget, otherwise the category key. */
    val categoryKey: String?,
    val level: AlertLevel,
    val spent: Double,
    val budget: Double,
) {
    val remaining: Double get() = budget - spent
    val overBy: Double get() = spent - budget
}

/** Pure calculator for monthly budget figures and threshold alerts. */
object BudgetCalculator {

    fun summarize(
        period: MonthPeriod,
        transactions: List<TxnSummary>,
        totalBudget: Double,
        categoryBudgets: Map<String, Double>,
        nowMillis: Long,
        categoryOrder: List<String>,
    ): MonthSummary {
        val inMonth = transactions.filter { period.contains(it.timestamp) && it.timestamp <= nowMillis }
        val spent = inMonth.sumOf { it.myShare }

        val byCategory = inMonth.groupBy { it.categoryKey }
        val keys = (categoryOrder + byCategory.keys + categoryBudgets.keys).distinct()
        val categories = keys.map { key ->
            val list = byCategory[key].orEmpty()
            val catSpent = list.sumOf { it.myShare }
            CategorySpend(
                categoryKey = key,
                spent = catSpent,
                budget = categoryBudgets[key],
                count = list.size,
                shareOfTotal = if (spent > 0) catSpent / spent else 0.0,
            )
        }.sortedByDescending { it.spent }

        val dayOfMonth = period.dayOfMonth(nowMillis)
        val previous = period.previous()
        val prevSameDayEnd = previous.endOfDayMillis(dayOfMonth.coerceAtMost(previous.daysInMonth))
        val previousToSameDay = transactions
            .filter { previous.contains(it.timestamp) && it.timestamp <= prevSameDayEnd }
            .sumOf { it.myShare }
        val previousFull = transactions.filter { previous.contains(it.timestamp) }.sumOf { it.myShare }

        val zone = period.zone
        val todayEnd = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = todayEnd - ChronoUnit.DAYS.duration.toMillis() * 7
        val prevWeekStart = weekStart - ChronoUnit.DAYS.duration.toMillis() * 7
        val weekSpent = transactions.filter { it.timestamp in weekStart until todayEnd }.sumOf { it.myShare }
        val prevWeekSpent = transactions.filter { it.timestamp in prevWeekStart until weekStart }.sumOf { it.myShare }

        return MonthSummary(
            period = period,
            totalBudget = totalBudget,
            spent = spent,
            transactionCount = inMonth.size,
            dayOfMonth = dayOfMonth,
            daysInMonth = period.daysInMonth,
            categories = categories,
            previousMonthSpentToSameDay = previousToSameDay,
            previousMonthSpent = previousFull,
            weekSpent = weekSpent,
            previousWeekSpent = prevWeekSpent,
        )
    }

    /**
     * Alerts whose threshold is currently reached. The caller filters out those already
     * sent this month (PRD: each alert fires once per month, per total and per category).
     */
    fun reachedAlerts(summary: MonthSummary): List<BudgetAlert> {
        val alerts = mutableListOf<BudgetAlert>()
        alerts += reachedFor(null, summary.spent, summary.totalBudget)
        for (cat in summary.categories) {
            val budget = cat.budget ?: continue
            alerts += reachedFor(cat.categoryKey, cat.spent, budget)
        }
        return alerts
    }

    fun reachedFor(categoryKey: String?, spent: Double, budget: Double): List<BudgetAlert> {
        if (budget <= 0.0) return emptyList()
        return AlertLevel.entries
            .filter { level -> spent >= budget * level.percent / 100.0 }
            .map { BudgetAlert(categoryKey, it, spent, budget) }
    }

    /** True when the month's spend stayed within the budget. */
    fun metBudget(spent: Double, budget: Double): Boolean = spent <= budget

    /** Helper to compute the zone-aware end of a given day in a period. */
    private fun MonthPeriod.endOfDayMillis(day: Int): Long =
        yearMonth.atDay(day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
}
