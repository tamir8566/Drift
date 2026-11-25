package com.tamir.drift.domain.budget

import com.tamir.drift.domain.format.BarValue
import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.time.MonthPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters

/**
 * Per-category spend inside a month. All amounts are "my share" (amountMine), net of refunds;
 * transfers are not counted.
 */
data class CategorySpend(
    val categoryKey: String,
    val spent: Double,
    /** Null when the user did not set a budget for this category. */
    val budget: Double?,
    /** Number of purchases (expenses) of the category in the month. */
    val count: Int,
    /** Share of the month's spend, 0..1 (used for the bar when no budget is set). */
    val shareOfTotal: Double,
) {
    val hasBudget: Boolean get() = budget != null && budget > 0.0

    /**
     * The one value the category's bar draws and its percent prints: budget usage when a budget
     * exists, else the share of the month's spend.
     */
    val bar: BarValue get() = if (hasBudget) BarValue(spent, budget ?: 0.0) else BarValue.ofFraction(shareOfTotal)

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
    /** This Sunday–Saturday week so far (my share) — the same week the work and goal screens use. */
    val weekSpent: Double,
    /** The whole previous Sunday–Saturday week. */
    val previousWeekSpent: Double,
) {
    /** The one value the budget line draws and its percent prints: spent against the total budget. */
    val bar: BarValue get() = BarValue(spent, totalBudget)

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

    /** False when no monthly budget is set: there is no usage and no pace to speak of. */
    val hasBudget: Boolean get() = totalBudget > 0.0

    /**
     * PRD: percent used < percent of month elapsed → good pace, otherwise too fast. Only
     * meaningful with a budget ([hasBudget]); without one the screens show no pace at all.
     */
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
        val zone = period.zone
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        // "Until now" means through the end of today: an expense dated later today (an edited
        // date keeps its clock time) must count as soon as it is saved. Later days do not.
        val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val inMonth = transactions.filter { period.contains(it.timestamp) && it.timestamp < todayEnd }
        val spent = inMonth.sumOf { it.net }

        val byCategory = inMonth.groupBy { it.categoryKey }
        val keys = (categoryOrder + byCategory.keys + categoryBudgets.keys).distinct()
        val spentByKey = keys.associateWith { key -> byCategory[key].orEmpty().sumOf { it.net } }
        // Shares are taken over the categories that actually cost money, so a category whose
        // refunds exceed its purchases is 0% and the others still add up to 100%.
        val positiveTotal = spentByKey.values.sumOf { it.coerceAtLeast(0.0) }
        val categories = keys.map { key ->
            val catSpent = spentByKey[key] ?: 0.0
            CategorySpend(
                categoryKey = key,
                spent = catSpent,
                budget = categoryBudgets[key],
                count = byCategory[key].orEmpty().count { it.isExpense },
                shareOfTotal = if (positiveTotal > 0) catSpent.coerceAtLeast(0.0) / positiveTotal else 0.0,
            )
        }.sortedByDescending { it.spent }

        val dayOfMonth = period.dayOfMonth(nowMillis)
        val previous = period.previous()
        val prevSameDayEnd = previous.endOfDayMillis(dayOfMonth.coerceAtMost(previous.daysInMonth))
        val previousToSameDay = transactions
            .filter { previous.contains(it.timestamp) && it.timestamp <= prevSameDayEnd }
            .sumOf { it.net }
        val previousFull = transactions.filter { previous.contains(it.timestamp) }.sumOf { it.net }

        // "השבוע" = the Sunday–Saturday week of today, up to the end of today.
        val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val weekStart = sunday.atStartOfDay(zone).toInstant().toEpochMilli()
        val prevWeekStart = sunday.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekSpent = transactions.filter { it.timestamp in weekStart until todayEnd }.sumOf { it.net }
        val prevWeekSpent = transactions.filter { it.timestamp in prevWeekStart until weekStart }.sumOf { it.net }

        return MonthSummary(
            period = period,
            totalBudget = totalBudget,
            spent = spent,
            transactionCount = inMonth.count { it.isExpense },
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
