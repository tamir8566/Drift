package com.tamir.drift.domain.insights

import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.time.MonthPeriod
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// (a) "Where to save": merchants with ≥3 purchases in the month
// ---------------------------------------------------------------------------

data class RecurringPurchase(
    val merchant: String,
    val count: Int,
    val monthlyTotal: Double,
    val averageAmount: Double,
) {
    /** PRD: yearly projection = monthly total × 12. */
    val yearlyProjection: Double get() = monthlyTotal * 12

    /** What the month would cost at "once a week instead" (4 purchases of the average). */
    val onceAWeekMonthlyCost: Double get() = averageAmount * 4

    /** Monthly saving when switching to once a week (never negative). */
    val onceAWeekMonthlySaving: Double get() = (monthlyTotal - onceAWeekMonthlyCost).coerceAtLeast(0.0)

    val onceAWeekYearlySaving: Double get() = onceAWeekMonthlySaving * 12
}

object RecurringPurchaseFinder {
    /**
     * PRD: a merchant with at least [minCount] (3) purchases in the month → monthly total,
     * yearly projection, "once a week instead" suggestion. Sorted by monthly total.
     */
    fun find(
        transactions: List<TxnSummary>,
        period: MonthPeriod,
        minCount: Int = 3,
    ): List<RecurringPurchase> =
        transactions
            .filter { period.contains(it.timestamp) }
            .groupBy { normalizeMerchant(it.merchant) }
            .filter { (key, list) -> key.isNotBlank() && list.size >= minCount }
            .map { (_, list) ->
                val total = list.sumOf { it.myShare }
                RecurringPurchase(
                    merchant = list.first().merchant,
                    count = list.size,
                    monthlyTotal = total,
                    averageAmount = total / list.size,
                )
            }
            .sortedByDescending { it.monthlyTotal }
}

// ---------------------------------------------------------------------------
// (b) Subscription detection
// ---------------------------------------------------------------------------

data class Subscription(
    val merchant: String,
    val amount: Double,
    val charges: Int,
    val firstCharge: Long,
    val lastCharge: Long,
    /** PRD: not charged for 45 days → inactive. */
    val isActive: Boolean,
    /** Ids of the transactions that belong to this subscription. */
    val transactionIds: List<Long>,
) {
    val yearlyCost: Double get() = amount * 12
}

object SubscriptionDetector {
    const val MIN_GAP_DAYS = 25L
    const val MAX_GAP_DAYS = 35L
    const val INACTIVE_AFTER_DAYS = 45L

    /**
     * PRD: same merchant, amount within ±5%, 25–35 days between charges, at least 2 charges.
     * Merchants listed in [excludedMerchants] (user said "not a subscription") are skipped.
     */
    fun detect(
        transactions: List<TxnSummary>,
        nowMillis: Long,
        tolerance: Double = 0.05,
        minCharges: Int = 2,
        excludedMerchants: Set<String> = emptySet(),
    ): List<Subscription> {
        val excluded = excludedMerchants.map { normalizeMerchant(it) }.toSet()
        return transactions
            .groupBy { normalizeMerchant(it.merchant) }
            .filter { it.key.isNotBlank() && it.key !in excluded }
            .mapNotNull { (_, list) -> detectForMerchant(list, nowMillis, tolerance, minCharges) }
            .sortedWith(compareByDescending<Subscription> { it.isActive }.thenByDescending { it.amount })
    }

    private fun detectForMerchant(
        list: List<TxnSummary>,
        nowMillis: Long,
        tolerance: Double,
        minCharges: Int,
    ): Subscription? {
        val sorted = list.sortedBy { it.timestamp }
        // Anchor on the most frequent amount (to the agora).
        val anchor = sorted
            .groupBy { (it.amountTotal * 100).roundToInt() }
            .maxByOrNull { it.value.size }?.value?.first()?.amountTotal ?: return null
        val similar = sorted.filter { abs(it.amountTotal - anchor) <= anchor * tolerance }
        if (similar.size < minCharges) return null

        // Walk the chain: keep charges whose gap from the previous kept charge is 25–35 days.
        val chain = mutableListOf(similar.first())
        for (t in similar.drop(1)) {
            val days = daysBetween(chain.last().timestamp, t.timestamp)
            if (days in MIN_GAP_DAYS..MAX_GAP_DAYS) chain += t
            else if (days > MAX_GAP_DAYS) {
                // Chain broken; start again from this charge if the rest could still qualify.
                chain.clear(); chain += t
            }
            // days < MIN_GAP: same-cycle duplicate, ignore.
        }
        if (chain.size < minCharges) return null

        val last = chain.last()
        val sinceLast = daysBetween(last.timestamp, nowMillis)
        return Subscription(
            merchant = last.merchant,
            amount = anchor,
            charges = chain.size,
            firstCharge = chain.first().timestamp,
            lastCharge = last.timestamp,
            isActive = sinceLast <= INACTIVE_AFTER_DAYS,
            transactionIds = chain.map { it.id },
        )
    }

    private fun daysBetween(a: Long, b: Long): Long =
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(a), Instant.ofEpochMilli(b))
}

// ---------------------------------------------------------------------------
// (c) Top merchants
// ---------------------------------------------------------------------------

data class MerchantRank(val merchant: String, val total: Double, val count: Int)

object TopMerchants {
    /** PRD: the 5 biggest merchants for the period. */
    fun rank(transactions: List<TxnSummary>, startMillis: Long, endMillis: Long, limit: Int = 5): List<MerchantRank> =
        transactions
            .filter { it.timestamp in startMillis until endMillis }
            .groupBy { normalizeMerchant(it.merchant) }
            .filter { it.key.isNotBlank() }
            .map { (_, list) -> MerchantRank(list.first().merchant, list.sumOf { it.myShare }, list.size) }
            .sortedByDescending { it.total }
            .take(limit)
}

// ---------------------------------------------------------------------------
// (d) Savings goal
// ---------------------------------------------------------------------------

data class SavingsGoalStatus(
    val targetAmount: Double,
    val saved: Double,
    val monthsLeft: Int,
    /** PRD: needed per month = (target − saved) / months left. */
    val neededPerMonth: Double,
    /** PRD: potential = budget − spent (this month). */
    val monthlyPotential: Double,
) {
    val remainingToTarget: Double get() = (targetAmount - saved).coerceAtLeast(0.0)
    val isReached: Boolean get() = saved >= targetAmount

    /** PRD: potential ≥ needed → on track. */
    val onTrack: Boolean get() = isReached || monthlyPotential >= neededPerMonth
    val progress: Double get() = if (targetAmount > 0) (saved / targetAmount).coerceIn(0.0, 1.0) else 0.0
}

object SavingsGoalCalculator {
    fun evaluate(
        targetAmount: Double,
        targetDateMillis: Long,
        saved: Double,
        monthlyBudget: Double,
        monthSpent: Double,
        nowMillis: Long,
        zone: ZoneId,
    ): SavingsGoalStatus {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val target = Instant.ofEpochMilli(targetDateMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(now, target).coerceAtLeast(0)
        // Months left, rounded up, at least 1 so the division is defined.
        val monthsLeft = ceil(days / 30.4375).toInt().coerceAtLeast(1)
        val remaining = (targetAmount - saved).coerceAtLeast(0.0)
        return SavingsGoalStatus(
            targetAmount = targetAmount,
            saved = saved,
            monthsLeft = monthsLeft,
            neededPerMonth = remaining / monthsLeft,
            monthlyPotential = monthlyBudget - monthSpent,
        )
    }
}

// ---------------------------------------------------------------------------
// (e) Month history / comparison
// ---------------------------------------------------------------------------

data class MonthTotal(val yearMonth: YearMonth, val total: Double, val count: Int, val byCategory: Map<String, Double>)

object MonthHistory {
    /** Totals for the last [months] months ending at [end] (oldest first; empty months included). */
    fun lastMonths(transactions: List<TxnSummary>, end: YearMonth, months: Int, zone: ZoneId): List<MonthTotal> {
        val grouped = transactions.groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }
        return (months - 1 downTo 0).map { back ->
            val ym = end.minusMonths(back.toLong())
            val list = grouped[ym].orEmpty()
            MonthTotal(
                yearMonth = ym,
                total = list.sumOf { it.myShare },
                count = list.size,
                byCategory = list.groupBy { it.categoryKey }.mapValues { e -> e.value.sumOf { it.myShare } },
            )
        }
    }

    /** Per-category totals for an arbitrary range. */
    fun categoryTotals(transactions: List<TxnSummary>, startMillis: Long, endMillis: Long): Map<String, Double> =
        transactions
            .filter { it.timestamp in startMillis until endMillis }
            .groupBy { it.categoryKey }
            .mapValues { e -> e.value.sumOf { it.myShare } }
}

// ---------------------------------------------------------------------------
// (f) Weekly summary
// ---------------------------------------------------------------------------

data class WeeklySummary(
    val spent: Double,
    val count: Int,
    val topCategoryKey: String?,
    val topCategoryAmount: Double,
    val budgetPercentUsed: Double,
    val remaining: Double,
)

object WeeklySummaryCalculator {
    fun calculate(
        transactions: List<TxnSummary>,
        weekStartMillis: Long,
        weekEndMillis: Long,
        monthSpent: Double,
        monthBudget: Double,
    ): WeeklySummary {
        val week = transactions.filter { it.timestamp in weekStartMillis until weekEndMillis }
        val top = week.groupBy { it.categoryKey }.mapValues { e -> e.value.sumOf { it.myShare } }.maxByOrNull { it.value }
        return WeeklySummary(
            spent = week.sumOf { it.myShare },
            count = week.size,
            topCategoryKey = top?.key,
            topCategoryAmount = top?.value ?: 0.0,
            budgetPercentUsed = if (monthBudget > 0) monthSpent / monthBudget * 100 else 0.0,
            remaining = monthBudget - monthSpent,
        )
    }
}

internal fun normalizeMerchant(m: String): String = m.trim().lowercase().replace(Regex("""\s+"""), " ")
