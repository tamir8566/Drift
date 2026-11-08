package com.tamir.drift.ui.screens.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.SavingsDepositEntity
import com.tamir.drift.data.db.SavingsGoalEntity
import com.tamir.drift.data.repo.BudgetRepository
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.insights.SavingsGoalCalculator
import com.tamir.drift.domain.insights.SavingsGoalStatus
import com.tamir.drift.domain.time.MonthPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class GoalItem(val goal: SavingsGoalEntity, val status: SavingsGoalStatus, val deposits: List<SavingsDepositEntity>)

data class PastSaving(val month: YearMonth, val saved: Double)

data class SavingsUiState(
    val loading: Boolean = true,
    val potential: Double = 0.0,
    val budget: Double = 0.0,
    val spent: Double = 0.0,
    val goals: List<GoalItem> = emptyList(),
    val past: List<PastSaving> = emptyList(),
)

class SavingsViewModel(private val graph: AppGraph) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    val state: StateFlow<SavingsUiState> = combine(
        graph.transactions.all,
        graph.budgets.all,
        graph.savings.goals,
        graph.savings.deposits,
    ) { txs, budgets, goals, deposits ->
        val now = System.currentTimeMillis()
        val month = YearMonth.now(zone)
        val summaries = txs.toSummaries()
        val period = MonthPeriod(month, zone)
        val spent = summaries.filter { period.contains(it.timestamp) }.sumOf { it.myShare }
        val budget = Money.toShekels(BudgetRepository.resolve(budgets, month))
        val potential = budget - spent

        val items = goals.map { g ->
            val mine = deposits.filter { it.goalId == g.id }
            val saved = Money.toShekels(mine.sumOf { it.amount })
            GoalItem(
                goal = g,
                status = SavingsGoalCalculator.evaluate(Money.toShekels(g.targetAmount), g.targetDate, saved, budget, spent, now, zone),
                deposits = mine,
            )
        }

        val firstMonth = summaries.minOfOrNull { it.timestamp }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) }
        val past = buildList {
            var m = month.minusMonths(1)
            var count = 0
            while (firstMonth != null && !m.isBefore(firstMonth) && count < 12) {
                val p = MonthPeriod(m, zone)
                val s = summaries.filter { p.contains(it.timestamp) }.sumOf { it.myShare }
                add(PastSaving(m, Money.toShekels(BudgetRepository.resolve(budgets, m)) - s))
                m = m.minusMonths(1); count++
            }
        }
        SavingsUiState(false, potential, budget, spent, items, past)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsUiState())

    fun create(name: String, target: Double, targetDate: Long) {
        viewModelScope.launch { graph.savings.create(name, Money.toAgorot(target), targetDate) }
    }

    fun update(goal: SavingsGoalEntity, name: String, target: Double, targetDate: Long) {
        viewModelScope.launch { graph.savings.update(goal.copy(name = name.trim(), targetAmount = Money.toAgorot(target), targetDate = targetDate)) }
    }

    fun deposit(goalId: Long, amount: Double) {
        viewModelScope.launch { graph.savings.deposit(goalId, Money.toAgorot(amount), System.currentTimeMillis()) }
    }

    fun delete(goalId: Long) {
        viewModelScope.launch { graph.savings.delete(goalId) }
    }
}
