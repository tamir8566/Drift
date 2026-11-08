package com.tamir.drift.domain.insights

import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.time.MonthPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class InsightsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private var nextId = 1L
    private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private fun tx(merchant: String, amount: Double, ts: Long, cat: String = CategoryKeys.OTHER) =
        TxnSummary(nextId++, amount, amount, merchant, cat, ts, TransactionSource.SMS)

    private val now = at(2026, 11, 8)

    // ---- subscriptions ----

    @Test
    fun `two monthly charges within 5 percent make a subscription`() {
        val txs = listOf(tx("Netflix", 54.9, at(2026, 9, 12)), tx("Netflix", 56.9, at(2026, 10, 12)))
        val subs = SubscriptionDetector.detect(txs, now)
        assertEquals(1, subs.size)
        assertEquals(2, subs[0].charges)
        assertTrue(subs[0].isActive) // last charge 27 days ago
        assertEquals(54.9 * 12, subs[0].yearlyCost, 0.001)
    }

    @Test
    fun `gap outside 25-35 days is not a subscription`() {
        val txs = listOf(tx("Shop", 100.0, at(2026, 9, 1)), tx("Shop", 100.0, at(2026, 9, 20)))
        assertTrue(SubscriptionDetector.detect(txs, now).isEmpty())
        val txs2 = listOf(tx("Shop", 100.0, at(2026, 7, 1)), tx("Shop", 100.0, at(2026, 9, 1)))
        assertTrue(SubscriptionDetector.detect(txs2, now).isEmpty())
    }

    @Test
    fun `amount difference above 5 percent breaks the match`() {
        val txs = listOf(tx("Gym", 100.0, at(2026, 9, 1)), tx("Gym", 110.0, at(2026, 10, 1)))
        assertTrue(SubscriptionDetector.detect(txs, now).isEmpty())
    }

    @Test
    fun `no charge for 45 days marks inactive`() {
        val txs = listOf(tx("Spotify", 19.9, at(2026, 7, 1)), tx("Spotify", 19.9, at(2026, 8, 1)))
        val subs = SubscriptionDetector.detect(txs, now)
        assertEquals(1, subs.size)
        assertFalse(subs[0].isActive)
    }

    @Test
    fun `excluded merchants are skipped`() {
        val txs = listOf(tx("Netflix", 54.9, at(2026, 9, 12)), tx("Netflix", 54.9, at(2026, 10, 12)))
        assertTrue(SubscriptionDetector.detect(txs, now, excludedMerchants = setOf("netflix")).isEmpty())
    }

    // ---- where to save ----

    @Test
    fun `three purchases a month suggest once a week`() {
        val period = MonthPeriod(YearMonth.of(2026, 11), zone)
        val txs = listOf(
            tx("ארומה", 18.0, at(2026, 11, 1)), tx("ארומה", 18.0, at(2026, 11, 2)), tx("ארומה", 18.0, at(2026, 11, 3)),
            tx("ארומה", 18.0, at(2026, 11, 4)), tx("ארומה", 18.0, at(2026, 11, 5)), tx("ארומה", 18.0, at(2026, 11, 6)),
            tx("וולט", 90.0, at(2026, 11, 1)), tx("וולט", 90.0, at(2026, 11, 2)),
            tx("ארומה", 18.0, at(2026, 10, 30)), // previous month, ignored
        )
        val r = RecurringPurchaseFinder.find(txs, period)
        assertEquals(1, r.size)
        assertEquals(6, r[0].count)
        assertEquals(108.0, r[0].monthlyTotal, 0.001)
        assertEquals(1296.0, r[0].yearlyProjection, 0.001)
        assertEquals(72.0, r[0].onceAWeekMonthlyCost, 0.001)
        assertEquals(36.0, r[0].onceAWeekMonthlySaving, 0.001)
    }

    // ---- top merchants ----

    @Test
    fun `top five merchants by my share`() {
        val period = MonthPeriod(YearMonth.of(2026, 11), zone)
        val txs = (1..7).map { i -> tx("M$i", i * 10.0, at(2026, 11, 2)) } + tx("M7", 5.0, at(2026, 11, 3))
        val top = TopMerchants.rank(txs, period.startMillis, period.endMillis)
        assertEquals(5, top.size)
        assertEquals("M7", top[0].merchant)
        assertEquals(75.0, top[0].total, 0.001)
        assertEquals(2, top[0].count)
        assertEquals("M3", top[4].merchant)
    }

    // ---- savings goal ----

    @Test
    fun `needed per month and on-track flag`() {
        val target = at(2027, 5, 8) // 6 months ahead
        val s = SavingsGoalCalculator.evaluate(6000.0, target, 1200.0, 5000.0, 3500.0, now, zone)
        assertEquals(6, s.monthsLeft)
        assertEquals(800.0, s.neededPerMonth, 0.001)
        assertEquals(1500.0, s.monthlyPotential, 0.001)
        assertTrue(s.onTrack)
        val tight = SavingsGoalCalculator.evaluate(6000.0, target, 1200.0, 5000.0, 4500.0, now, zone)
        assertFalse(tight.onTrack)
    }

    @Test
    fun `reached goal is on track and progress capped`() {
        val s = SavingsGoalCalculator.evaluate(1000.0, at(2026, 12, 1), 1200.0, 5000.0, 6000.0, now, zone)
        assertTrue(s.isReached)
        assertTrue(s.onTrack)
        assertEquals(1.0, s.progress, 0.001)
        assertEquals(0.0, s.neededPerMonth, 0.001)
    }

    // ---- month history ----

    @Test
    fun `last six months include empty ones oldest first`() {
        val txs = listOf(tx("a", 10.0, at(2026, 11, 1)), tx("b", 20.0, at(2026, 8, 1)))
        val months = MonthHistory.lastMonths(txs, YearMonth.of(2026, 11), 6, zone)
        assertEquals(6, months.size)
        assertEquals(YearMonth.of(2026, 6), months.first().yearMonth)
        assertEquals(YearMonth.of(2026, 11), months.last().yearMonth)
        assertEquals(20.0, months[2].total, 0.001)
        assertEquals(0.0, months[3].total, 0.001)
    }

    // ---- weekly summary ----

    @Test
    fun `weekly summary picks the biggest category`() {
        val txs = listOf(
            tx("a", 100.0, at(2026, 11, 3), CategoryKeys.GROCERIES),
            tx("b", 150.0, at(2026, 11, 4), CategoryKeys.EATING_OUT),
            tx("c", 999.0, at(2026, 10, 20), CategoryKeys.HOME),
        )
        val w = WeeklySummaryCalculator.calculate(txs, at(2026, 11, 1), at(2026, 11, 8), 250.0, 5000.0)
        assertEquals(250.0, w.spent, 0.001)
        assertEquals(2, w.count)
        assertEquals(CategoryKeys.EATING_OUT, w.topCategoryKey)
        assertEquals(5.0, w.budgetPercentUsed, 0.001)
    }
}
