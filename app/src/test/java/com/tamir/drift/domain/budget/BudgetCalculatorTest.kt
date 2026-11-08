package com.tamir.drift.domain.budget

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.time.MonthPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class BudgetCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val nov = MonthPeriod(YearMonth.of(2026, 11), zone)
    private var nextId = 1L

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tx(amount: Double, ts: Long, cat: String = CategoryKeys.GROCERIES, mine: Double = amount) =
        TxnSummary(nextId++, amount, mine, "x", cat, ts, TransactionSource.GPAY, isShared = mine != amount)

    // 8 Nov 2026, 15:00 — same day as the design mockup ("יום 8/30").
    private val now = at(2026, 11, 8, 15)

    @Test
    fun `remaining, percent, allowed per day use my share only`() {
        val txs = listOf(
            tx(180.0, at(2026, 11, 8), CategoryKeys.EATING_OUT, mine = 60.0), // shared: only 60 counts
            tx(214.0, at(2026, 11, 7)),
            tx(120.0, at(2026, 11, 7), CategoryKeys.TRANSPORT),
        )
        val s = BudgetCalculator.summarize(nov, txs, 5000.0, emptyMap(), now, CategoryKeys.all)
        assertEquals(394.0, s.spent, 0.001)
        assertEquals(4606.0, s.remaining, 0.001)
        assertEquals(7.88, s.percentUsed, 0.001)
        assertEquals(8, s.dayOfMonth)
        assertEquals(30, s.daysInMonth)
        assertEquals(23, s.daysLeft)
        assertEquals(4606.0 / 23, s.allowedPerDay, 0.001)
        assertFalse(s.isOverBudget)
        assertTrue(s.isGoodPace) // 7.9% used < 26.7% of month elapsed
    }

    @Test
    fun `over budget gives negative remaining and zero per day`() {
        val s = BudgetCalculator.summarize(nov, listOf(tx(5200.0, at(2026, 11, 3))), 5000.0, emptyMap(), now, CategoryKeys.all)
        assertEquals(-200.0, s.remaining, 0.001)
        assertEquals(0.0, s.allowedPerDay, 0.001)
        assertTrue(s.isOverBudget)
        assertFalse(s.isGoodPace)
    }

    @Test
    fun `week vs previous week and month vs previous month to the same day`() {
        val txs = listOf(
            tx(100.0, at(2026, 11, 8)),  // this week (2–8 Nov)
            tx(100.0, at(2026, 11, 2)),  // this week
            tx(50.0, at(2026, 11, 1)),   // previous week (26 Oct–1 Nov)
            tx(300.0, at(2026, 10, 5)),  // Oct, up to day 8 → counts
            tx(900.0, at(2026, 10, 20)), // Oct after day 8 → not in fair comparison
        )
        val s = BudgetCalculator.summarize(nov, txs, 5000.0, emptyMap(), now, CategoryKeys.all)
        assertEquals(200.0, s.weekSpent, 0.001)
        assertEquals(50.0, s.previousWeekSpent, 0.001)
        assertEquals(300.0, s.weekChangePercent!!, 0.001)
        assertEquals(250.0, s.spent, 0.001)
        assertEquals(300.0, s.previousMonthSpentToSameDay, 0.001)
        assertEquals(1200.0, s.previousMonthSpent, 0.001)
        assertEquals(-16.666, s.monthChangePercent!!, 0.01)
    }

    @Test
    fun `no previous data gives null comparisons`() {
        val s = BudgetCalculator.summarize(nov, listOf(tx(10.0, at(2026, 11, 8))), 5000.0, emptyMap(), now, CategoryKeys.all)
        assertNull(s.monthChangePercent)
        assertNull(s.weekChangePercent)
    }

    @Test
    fun `category with budget shows percent of budget, without shows share of total`() {
        val txs = listOf(
            tx(450.0, at(2026, 11, 5), CategoryKeys.EATING_OUT),
            tx(150.0, at(2026, 11, 5), CategoryKeys.GROCERIES),
        )
        val s = BudgetCalculator.summarize(nov, txs, 5000.0, mapOf(CategoryKeys.EATING_OUT to 500.0), now, CategoryKeys.all)
        val eating = s.categories.first { it.categoryKey == CategoryKeys.EATING_OUT }
        val groceries = s.categories.first { it.categoryKey == CategoryKeys.GROCERIES }
        assertEquals(90.0, eating.displayPercent, 0.001)
        assertTrue(eating.isWarning) // > 85%
        assertEquals(25.0, groceries.displayPercent, 0.001) // 150 / 600
        assertFalse(groceries.isWarning)
        assertEquals(CategoryKeys.EATING_OUT, s.categories.first().categoryKey) // sorted by spend
    }

    @Test
    fun `alerts fire at 80 and 100 percent for total and categories`() {
        val txs = listOf(tx(4100.0, at(2026, 11, 5), CategoryKeys.HOME), tx(510.0, at(2026, 11, 6), CategoryKeys.EATING_OUT))
        val s = BudgetCalculator.summarize(nov, txs, 5000.0, mapOf(CategoryKeys.EATING_OUT to 500.0), now, CategoryKeys.all)
        val alerts = BudgetCalculator.reachedAlerts(s)
        assertTrue(alerts.any { it.categoryKey == null && it.level == AlertLevel.WARNING })
        assertFalse(alerts.any { it.categoryKey == null && it.level == AlertLevel.EXCEEDED })
        assertTrue(alerts.any { it.categoryKey == CategoryKeys.EATING_OUT && it.level == AlertLevel.WARNING })
        assertTrue(alerts.any { it.categoryKey == CategoryKeys.EATING_OUT && it.level == AlertLevel.EXCEEDED })
        val over = alerts.first { it.categoryKey == CategoryKeys.EATING_OUT && it.level == AlertLevel.EXCEEDED }
        assertEquals(10.0, over.overBy, 0.001)
    }

    @Test
    fun `transactions outside the month are ignored`() {
        val txs = listOf(tx(100.0, at(2026, 10, 31, 23)), tx(100.0, at(2026, 12, 1, 0)), tx(5.0, at(2026, 11, 1, 0)))
        val s = BudgetCalculator.summarize(nov, txs, 5000.0, emptyMap(), now, CategoryKeys.all)
        assertEquals(5.0, s.spent, 0.001)
        assertEquals(1, s.transactionCount)
    }
}
