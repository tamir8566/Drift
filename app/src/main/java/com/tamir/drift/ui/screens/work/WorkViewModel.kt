package com.tamir.drift.ui.screens.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.JobEntity
import com.tamir.drift.data.db.ShiftEntity
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toShiftSummaries
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.work.GoalCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One 2×2 card: a job's month figures and its week income vs the weekly goal. */
data class JobCardItem(
    val job: JobEntity,
    val monthHours: Double,
    val monthAmountAgorot: Long,
    val weekAmountAgorot: Long,
    val weeklyGoalAgorot: Long,
) {
    val weekFraction: Double get() = if (weeklyGoalAgorot > 0) (weekAmountAgorot.toDouble() / weeklyGoalAgorot).coerceIn(0.0, 1.0) else 0.0
    val weekPercent: Int get() = if (weeklyGoalAgorot > 0) (weekAmountAgorot * 100 / weeklyGoalAgorot).toInt() else 0
}

data class WorkUiState(
    val loading: Boolean = true,
    /** The month of the big numbers (always the current one). */
    val month: YearMonth = YearMonth.now(),
    /** The month shown in the calendar (pageable). */
    val calendarMonth: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val earnedMonthAgorot: Long = 0L,
    val earnedWeekAgorot: Long = 0L,
    val hoursMonth: Double = 0.0,
    val shiftsMonth: Int = 0,
    val workedDays: Set<Long> = emptySet(),
    val plannedDays: Set<Long> = emptySet(),
    val jobs: List<JobEntity> = emptyList(),
    val jobCards: List<JobCardItem> = emptyList(),
    val recentShifts: List<ShiftEntity> = emptyList(),
    val jobsById: Map<Long, JobEntity> = emptyMap(),
)

class WorkViewModel(private val graph: AppGraph) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val calendarMonth = MutableStateFlow(YearMonth.now(zone))

    val state: StateFlow<WorkUiState> = combine(
        graph.work.shifts,
        graph.work.jobs,
        graph.work.plannedDays,
        graph.settings.settings,
        calendarMonth,
    ) { shifts, jobs, planned, settings, calMonth ->
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        val summaries = shifts.toShiftSummaries()
        val week = GoalCalculator.weekOf(today)
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        val inMonth = shifts.filter { it.date in first..last }

        val cards = jobs.filter { it.isActive }.map { job ->
            val mine = inMonth.filter { it.jobId == job.id }
            JobCardItem(
                job = job,
                monthHours = mine.sumOf { it.hours },
                monthAmountAgorot = mine.sumOf { it.amountAgorot },
                weekAmountAgorot = shifts.filter { it.jobId == job.id && week.contains(it.date) }.sumOf { it.amountAgorot },
                weeklyGoalAgorot = settings.weeklyIncomeGoalAgorot,
            )
        }.sortedByDescending { it.monthAmountAgorot }

        WorkUiState(
            loading = false,
            month = month,
            calendarMonth = calMonth,
            today = today,
            earnedMonthAgorot = GoalCalculator.earnedIn(summaries, month),
            earnedWeekAgorot = GoalCalculator.incomeIn(summaries, week),
            hoursMonth = GoalCalculator.hoursIn(summaries, month),
            shiftsMonth = inMonth.size,
            workedDays = shifts.map { it.date }.toSet(),
            plannedDays = planned.map { it.date }.toSet(),
            jobs = jobs,
            jobCards = cards,
            recentShifts = shifts.take(8),
            jobsById = jobs.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUiState())

    fun previousMonth() { calendarMonth.value = calendarMonth.value.minusMonths(1) }
    fun nextMonth() { calendarMonth.value = calendarMonth.value.plusMonths(1) }

    fun togglePlanned(date: LocalDate) {
        viewModelScope.launch { graph.work.togglePlanned(date) }
    }

    fun addJob(name: String, rateShekels: Double, multiplier: Double) {
        viewModelScope.launch { graph.work.addJob(name, Money.toAgorot(rateShekels), multiplier) }
    }

    fun updateJob(job: JobEntity, name: String, rateShekels: Double, multiplier: Double) {
        viewModelScope.launch {
            graph.work.updateJob(job.copy(name = name, hourlyRateAgorot = Money.toAgorot(rateShekels), shabbatMultiplier = multiplier))
        }
    }

    fun setJobActive(id: Long, active: Boolean) {
        viewModelScope.launch { graph.work.setJobActive(id, active) }
    }

    fun deleteShift(id: Long) {
        viewModelScope.launch { graph.work.deleteShift(id) }
    }
}
