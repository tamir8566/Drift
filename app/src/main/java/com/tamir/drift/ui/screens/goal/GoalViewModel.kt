package com.tamir.drift.ui.screens.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.SavingsDepositEntity
import com.tamir.drift.data.db.SavingsGoalEntity
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toJobSummaries
import com.tamir.drift.data.repo.toShiftSummaries
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.insights.SavingsGoalCalculator
import com.tamir.drift.domain.insights.SavingsGoalStatus
import com.tamir.drift.domain.time.sinceStart
import com.tamir.drift.domain.work.GoalCalculator
import com.tamir.drift.domain.work.JobPicker
import com.tamir.drift.domain.work.MonthGoalStatus
import com.tamir.drift.domain.work.MonthSavingHistory
import com.tamir.drift.domain.work.PayType
import com.tamir.drift.domain.work.WeekStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A long-term savings goal with its status and the "at this month's pace" projection. */
data class LongTermGoalItem(
    val goal: SavingsGoalEntity,
    val status: SavingsGoalStatus,
    val deposits: List<SavingsDepositEntity>,
    /** PRD: "בקצב של החודש תגיע ליעד ב־<חודש>"; null when this month's saving is not positive. */
    val projectedMonth: YearMonth?,
)

data class GoalUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val status: MonthGoalStatus? = null,
    val week: WeekStatus? = null,
    val history: List<MonthSavingHistory> = emptyList(),
    val monthlyGoalAgorot: Long = 0L,
    val weeklyGoalAgorot: Long = 0L,
    val longTerm: List<LongTermGoalItem> = emptyList(),
)

class GoalViewModel(private val graph: AppGraph) : ViewModel() {

    /** Read on every use: a time zone changed while the app runs is picked up by the next computation. */
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val work = combine(graph.work.shifts, graph.work.jobs, graph.daySettings) { shifts, jobs, settings -> Triple(shifts, jobs, settings) }
    private val money = combine(graph.transactions.all, graph.savings.goals, graph.savings.deposits) { txs, goals, deposits -> Triple(txs, goals, deposits) }

    val state: StateFlow<GoalUiState> = combine(work, money) { (shifts, jobs, settings), (txs, goals, deposits) ->
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        // Tracking start: month status, the week and month carry-over, history and the long-term
        // pace only count shifts and transactions from it on. Until that day arrives the window
        // starts at the first launch; on the day itself everything begins clean.
        val window = settings.trackingWindow
        val allShiftSummaries = shifts.toShiftSummaries()
        val shiftSummaries = allShiftSummaries.sinceStart(window)
        val txSummaries = txs.toSummaries().sinceStart(window, zone)
        // The main job ("עבודה ראשית"): the one the user marked, else the busiest of the last
        // 30 days, else the oldest. Its rate is per hour or per day by its pay type.
        val mainJob = JobPicker.mainJob(jobs.toJobSummaries(), allShiftSummaries, today.toEpochDay())
        val mainRate = mainJob?.unitRateAgorot ?: 0L
        val mainPayType = mainJob?.payType ?: PayType.HOURLY

        val startDate = window.startDate
        // Both goals are passed with their history: the carry-over judges each past week and
        // month by the goal that was in force then.
        val status = GoalCalculator.monthStatus(
            shiftSummaries, txSummaries, month, today, zone, settings.monthlySavingGoal, mainRate, startDate, mainPayType,
        )
        val week = GoalCalculator.weekStatus(shiftSummaries, today, settings.weeklyIncomeGoal, startDate)
        val history = GoalCalculator.history(shiftSummaries, txSummaries, month, settings.monthlySavingGoal, zone, startDate = startDate, today = today)

        val savedThisMonth = Money.toShekels(status.savedAgorot)
        val longTerm = goals.map { g ->
            val mine = deposits.filter { it.goalId == g.id }
            val savedAgorot = mine.sumOf { it.amount }
            LongTermGoalItem(
                goal = g,
                // "Fed by the monthly saving": the pace is this month's saving instead of budget − spent.
                status = SavingsGoalCalculator.evaluate(
                    Money.toShekels(g.targetAmount), g.targetDate, Money.toShekels(savedAgorot),
                    monthlyBudget = savedThisMonth, monthSpent = 0.0, nowMillis = System.currentTimeMillis(), zone = zone,
                ),
                deposits = mine,
                projectedMonth = GoalCalculator.projectedGoalMonth(g.targetAmount, savedAgorot, status.savedAgorot, month),
            )
        }
        GoalUiState(
            loading = false,
            month = month,
            status = status,
            week = week,
            history = history,
            monthlyGoalAgorot = settings.monthlySavingGoalAgorot,
            weeklyGoalAgorot = settings.weeklyIncomeGoalAgorot,
            longTerm = longTerm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalUiState())

    fun setMonthlyGoal(agorot: Long) { viewModelScope.launch { graph.settings.setMonthlySavingGoal(agorot) } }
    fun setWeeklyGoal(agorot: Long) { viewModelScope.launch { graph.settings.setWeeklyIncomeGoal(agorot) } }

    fun create(name: String, target: Double, targetDate: Long) {
        viewModelScope.launch { graph.savings.create(name, Money.toAgorot(target), targetDate) }
    }

    fun update(goal: SavingsGoalEntity, name: String, target: Double, targetDate: Long) {
        viewModelScope.launch { graph.savings.update(goal.copy(name = name.trim(), targetAmount = Money.toAgorot(target), targetDate = targetDate)) }
    }

    fun deposit(goalId: Long, amount: Double) {
        viewModelScope.launch { graph.savings.deposit(goalId, Money.toAgorot(amount), System.currentTimeMillis()) }
    }
}
