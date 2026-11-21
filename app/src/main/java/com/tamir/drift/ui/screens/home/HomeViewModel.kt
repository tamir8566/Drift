package com.tamir.drift.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toShiftSummaries
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.budget.CategorySpend
import com.tamir.drift.domain.budget.MonthSummary
import com.tamir.drift.domain.time.sinceStart
import com.tamir.drift.domain.work.GoalCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val summary: MonthSummary? = null,
    /** Top 4 categories by spend (PRD: 2×2 grid). */
    val topCategories: List<CategorySpend> = emptyList(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val recent: List<TransactionEntity> = emptyList(),
    val unparsedCount: Int = 0,
    /** PRD 8 Nov: shifts income this month (shekels). */
    val earnedThisMonth: Double = 0.0,
    /** PRD 8 Nov: income − my share of expenses (shekels); may be negative. The same figure as the goal screen's "נחסך החודש". */
    val savedThisMonth: Double = 0.0,
    /** True when SMS senders are configured, i.e. the SMS permission matters for capture. */
    val smsCaptureConfigured: Boolean = true,
)

class HomeViewModel(graph: AppGraph) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        graph.transactions.all,
        graph.categories.categories,
        graph.budgets.all,
        graph.work.shifts,
        graph.settings.settings,
    ) { txs, cats, budgets, shifts, settings ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        // Tracking start: totals, "השבוע" and "מול חודש קודם" only count records from it on
        // (a previous month before the start has no spend → the existing "—").
        val window = settings.trackingWindow
        val summary = graph.monthSummary.build(txs, cats, budgets, month, window)
        val top = summary.categories.filter { it.spent > 0 }.take(4).ifEmpty { summary.categories.take(4) }
        // Budget math is untouched; the earned/saved row is purely additive.
        // Computed exactly like the goal screen (agorot, expenses through the end of today), so
        // "נחסכו" here and "נחסך החודש" there are always the same number.
        val earnedAgorot = GoalCalculator.earnedIn(shifts.toShiftSummaries().sinceStart(window), month)
        val spentBefore = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spentAgorot = GoalCalculator.spentIn(txs.toSummaries().sinceStart(window, zone), month, zone, spentBefore)
        val earned = Money.toShekels(earnedAgorot)
        HomeUiState(
            loading = false,
            month = month,
            summary = summary,
            topCategories = top,
            categories = cats.associateBy { it.id },
            recent = txs.filter { !it.isUnparsed }.sortedByDescending { it.timestamp }.take(6),
            unparsedCount = txs.count { it.isUnparsed },
            earnedThisMonth = earned,
            savedThisMonth = Money.toShekels(earnedAgorot - spentAgorot),
            smsCaptureConfigured = settings.smsSenders.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
