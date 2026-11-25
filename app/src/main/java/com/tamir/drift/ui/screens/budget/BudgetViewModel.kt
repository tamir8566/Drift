package com.tamir.drift.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.repo.BudgetRepository
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.data.settings.Settings
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.budget.MonthSummary
import com.tamir.drift.domain.time.MonthPeriod
import com.tamir.drift.domain.time.sinceStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

data class BudgetHistoryItem(val month: YearMonth, val spent: Double, val budget: Double) {
    val met: Boolean get() = spent <= budget
    val overBy: Double get() = spent - budget
}

data class BudgetUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val summary: MonthSummary? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val history: List<BudgetHistoryItem> = emptyList(),
    val settings: Settings? = null,
)

class BudgetViewModel(private val graph: AppGraph) : ViewModel() {

    /** Read on every use: a time zone changed while the app runs is picked up by the next computation. */
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val state: StateFlow<BudgetUiState> = combine(
        graph.transactions.all,
        graph.categories.categories,
        graph.budgets.all,
        graph.daySettings,
    ) { txs, cats, budgets, settings ->
        val month = YearMonth.now(zone)
        // Tracking start: usage, category usage and history only count transactions from it on.
        val window = settings.trackingWindow
        val summary = graph.monthSummary.build(txs, cats, budgets, month, window)
        val summaries = txs.toSummaries().sinceStart(window, zone)
        val firstMonth = summaries.minOfOrNull { it.timestamp }?.let { YearMonth.from(java.time.Instant.ofEpochMilli(it).atZone(zone)) }
        val history = buildList {
            var m = month.minusMonths(1)
            var count = 0
            while (firstMonth != null && !m.isBefore(firstMonth) && count < 12) {
                val p = MonthPeriod(m, zone)
                // Net, like every other total: a refund is subtracted, "לא נספר" is not counted.
                val spent = summaries.filter { p.contains(it.timestamp) }.sumOf { it.net }
                add(BudgetHistoryItem(m, spent, Money.toShekels(BudgetRepository.resolve(budgets, m))))
                m = m.minusMonths(1); count++
            }
        }
        BudgetUiState(false, month, summary, cats, history, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun setTotalBudget(shekels: Double) {
        viewModelScope.launch {
            graph.budgets.setBudgetFrom(YearMonth.now(zone), Money.toAgorot(shekels))
            // A lower budget may already be crossed by what was spent: alert now, not at the next expense.
            runCatching { graph.alertChecker.checkAndNotify() }
        }
    }

    fun setCategoryBudget(categoryId: String, shekels: Double?) {
        viewModelScope.launch {
            graph.categories.setCategoryBudget(categoryId, shekels?.let { Money.toAgorot(it) })
            runCatching { graph.alertChecker.checkAndNotify() }
        }
    }

    fun setAlert80(enabled: Boolean) { viewModelScope.launch { graph.settings.setAlert80(enabled) } }
    fun setAlert100(enabled: Boolean) { viewModelScope.launch { graph.settings.setAlert100(enabled) } }
}
