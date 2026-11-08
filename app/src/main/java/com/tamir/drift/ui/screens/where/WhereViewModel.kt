package com.tamir.drift.ui.screens.where

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.insights.MerchantRank
import com.tamir.drift.domain.insights.MonthHistory
import com.tamir.drift.domain.insights.MonthTotal
import com.tamir.drift.domain.insights.RecurringPurchase
import com.tamir.drift.domain.insights.RecurringPurchaseFinder
import com.tamir.drift.domain.insights.Subscription
import com.tamir.drift.domain.insights.SubscriptionDetector
import com.tamir.drift.domain.insights.TopMerchants
import com.tamir.drift.domain.time.MonthPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

enum class Period(val months: Int) { MONTH(1), THREE_MONTHS(3), YEAR(12) }

data class CategorySlice(val categoryId: String, val amount: Double, val fraction: Double)

data class WhereUiState(
    val period: Period = Period.MONTH,
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val total: Double = 0.0,
    val slices: List<CategorySlice> = emptyList(),
    val months: List<MonthTotal> = emptyList(),
    val topMerchants: List<MerchantRank> = emptyList(),
    val recurring: List<RecurringPurchase> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val currentMonth: YearMonth = YearMonth.now(),
    val highlightCategory: String? = null,
)

class WhereViewModel(private val graph: AppGraph, initialCategory: String?) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val period = MutableStateFlow(Period.MONTH)
    private val highlight = MutableStateFlow(initialCategory)

    val state: StateFlow<WhereUiState> = combine(
        graph.transactions.all,
        graph.categories.categories,
        graph.settings.settings,
        period,
        highlight,
    ) { txs, cats, settings, p, hl ->
        val now = System.currentTimeMillis()
        val month = YearMonth.now(zone)
        val summaries = txs.toSummaries()
        val end = MonthPeriod(month, zone).endMillis
        val start = MonthPeriod(month.minusMonths((p.months - 1).toLong()), zone).startMillis

        val byCat = MonthHistory.categoryTotals(summaries, start, end)
        val total = byCat.values.sum()
        val slices = cats.map { c -> CategorySlice(c.id, byCat[c.id] ?: 0.0, if (total > 0) (byCat[c.id] ?: 0.0) / total else 0.0) }
            .filter { it.amount > 0 }
            .sortedByDescending { it.amount }

        WhereUiState(
            period = p,
            categories = cats.associateBy { it.id },
            total = total,
            slices = slices,
            months = MonthHistory.lastMonths(summaries, month, 6, zone),
            topMerchants = TopMerchants.rank(summaries, start, end, 5),
            recurring = RecurringPurchaseFinder.find(summaries, MonthPeriod(month, zone)),
            subscriptions = SubscriptionDetector.detect(summaries, now, excludedMerchants = settings.excludedSubscriptions),
            currentMonth = month,
            highlightCategory = hl,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhereUiState(highlightCategory = initialCategory))

    fun setPeriod(p: Period) { period.value = p }

    fun clearHighlight() { highlight.value = null }

    /** PRD: "מנוי → לא מנוי". */
    fun notASubscription(merchant: String) {
        viewModelScope.launch {
            val current = graph.settings.current().excludedSubscriptions
            graph.settings.setExcludedSubscriptions(current + merchant)
        }
    }
}
