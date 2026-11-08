package com.tamir.drift.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.budget.CategorySpend
import com.tamir.drift.domain.budget.MonthSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val summary: MonthSummary? = null,
    /** Top 4 categories by spend (PRD: 2×2 grid). */
    val topCategories: List<CategorySpend> = emptyList(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val recent: List<TransactionEntity> = emptyList(),
    val unparsedCount: Int = 0,
)

class HomeViewModel(graph: AppGraph) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        graph.transactions.all,
        graph.categories.categories,
        graph.budgets.all,
    ) { txs, cats, budgets ->
        val month = YearMonth.now()
        val summary = graph.monthSummary.build(txs, cats, budgets, month)
        val top = summary.categories.filter { it.spent > 0 }.take(4).ifEmpty { summary.categories.take(4) }
        HomeUiState(
            loading = false,
            month = month,
            summary = summary,
            topCategories = top,
            categories = cats.associateBy { it.id },
            recent = txs.filter { !it.isUnparsed }.sortedByDescending { it.timestamp }.take(6),
            unparsedCount = txs.count { it.isUnparsed },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
