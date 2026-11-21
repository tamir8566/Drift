package com.tamir.drift.ui.screens.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.JobEntity
import com.tamir.drift.data.db.ShiftEntity
import com.tamir.drift.data.db.isDaily
import com.tamir.drift.data.db.totalAgorot
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toJobSummaries
import com.tamir.drift.data.repo.toShiftSummaries
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.format.BarValue
import com.tamir.drift.domain.work.GoalCalculator
import com.tamir.drift.domain.work.JobPicker
import com.tamir.drift.domain.work.PayType
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
    /** Hours this month (hourly jobs only; 0 for a daily job). */
    val monthHours: Double,
    /** Worked days (shifts) this month — the figure shown for a daily job. */
    val monthDays: Int,
    /** Month income of the job: wage + travel reimbursement. */
    val monthAmountAgorot: Long,
    /** Week income of the job: wage + travel reimbursement. */
    val weekAmountAgorot: Long,
    val weeklyGoalAgorot: Long,
) {
    /**
     * The one value the card's bar draws and its percent prints: this job's income of the
     * Sunday–Saturday week against the weekly income goal (both printed on the card).
     */
    val weekBar: BarValue get() = BarValue.of(weekAmountAgorot, weeklyGoalAgorot)
}

data class WorkUiState(
    val loading: Boolean = true,
    /** The month of the big numbers (always the current one). */
    val month: YearMonth = YearMonth.now(),
    /** The month shown in the calendar (pageable). */
    val calendarMonth: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    /** Month income: wage + travel reimbursement. */
    val earnedMonthAgorot: Long = 0L,
    /** The travel-reimbursement part of [earnedMonthAgorot] ("מתוכם נסיעות"). */
    val travelMonthAgorot: Long = 0L,
    val earnedWeekAgorot: Long = 0L,
    /** Hours of the month's shifts of hourly jobs (days of daily jobs carry no hours). */
    val hoursMonth: Double = 0.0,
    val shiftsMonth: Int = 0,
    val workedDays: Set<Long> = emptySet(),
    val plannedDays: Set<Long> = emptySet(),
    val jobs: List<JobEntity> = emptyList(),
    val jobCards: List<JobCardItem> = emptyList(),
    val recentShifts: List<ShiftEntity> = emptyList(),
    /** How many shifts exist in all (the "כל המשמרות" list shows them). */
    val totalShifts: Int = 0,
    /** Every shift by its day (epoch day) — a tap on a calendar day with a shift opens it. */
    val shiftsByDay: Map<Long, List<ShiftEntity>> = emptyMap(),
    val jobsById: Map<Long, JobEntity> = emptyMap(),
    /** The main job right now (marked by the user, or picked automatically). */
    val mainJobId: Long? = null,
    /** The calendar year the "שני מעסיקים" card speaks about. */
    val year: Int = LocalDate.now().year,
    /** Two or more active jobs and the card was not dismissed this year. */
    val showTwoEmployers: Boolean = false,
    /** "עשיתי תיאום מס" is ticked for [year]. */
    val taxCoordinationDone: Boolean = false,
    /** "עשיתי תיאום דמי ביטוח לאומי" is ticked for [year]. */
    val insuranceCoordinationDone: Boolean = false,
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
        // Tracking start: every total (month, week, hours, job cards) only counts shifts from it on.
        // The calendar dots and the recent-shifts list still show all shifts.
        val window = settings.trackingWindow
        val counted = shifts.filter { window.includes(it.date) }
        val summaries = counted.toShiftSummaries()
        val week = GoalCalculator.weekOf(today)
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        val inMonth = counted.filter { it.date in first..last }
        val workedDays: Set<Long> = shifts.map { it.date }.toSet()

        val cards = jobs.filter { it.isActive }.map { job ->
            val mine = inMonth.filter { it.jobId == job.id }
            JobCardItem(
                job = job,
                monthHours = if (job.isDaily) 0.0 else mine.sumOf { it.hours },
                monthDays = mine.size,
                monthAmountAgorot = mine.sumOf { it.totalAgorot },
                weekAmountAgorot = counted.filter { it.jobId == job.id && week.contains(it.date) }.sumOf { it.totalAgorot },
                weeklyGoalAgorot = settings.weeklyIncomeGoalAgorot,
            )
        }.sortedByDescending { it.monthAmountAgorot }

        WorkUiState(
            loading = false,
            month = month,
            calendarMonth = calMonth,
            today = today,
            earnedMonthAgorot = GoalCalculator.earnedIn(summaries, month),
            travelMonthAgorot = GoalCalculator.travelIn(summaries, month),
            earnedWeekAgorot = GoalCalculator.incomeIn(summaries, week),
            hoursMonth = GoalCalculator.hourlyHoursIn(summaries, jobs.toJobSummaries(), month),
            shiftsMonth = inMonth.size,
            workedDays = workedDays,
            // The planned ring is for days without a shift: a worked day shows its dot only.
            plannedDays = planned.map { it.date }.toSet() - workedDays,
            jobs = jobs,
            jobCards = cards,
            recentShifts = shifts.take(RECENT_SHIFTS),
            totalShifts = shifts.size,
            shiftsByDay = shifts.groupBy { it.date },
            jobsById = jobs.associateBy { it.id },
            mainJobId = JobPicker.mainJob(jobs.toJobSummaries(), shifts.toShiftSummaries(), today.toEpochDay())?.id,
            year = today.year,
            // The state is per calendar year, so the card comes back by itself every January.
            showTwoEmployers = jobs.count { it.isActive } >= 2 && settings.twoEmployersDismissedYear != today.year,
            taxCoordinationDone = settings.taxCoordinationYear == today.year,
            insuranceCoordinationDone = settings.insuranceCoordinationYear == today.year,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUiState())

    fun previousMonth() { calendarMonth.value = calendarMonth.value.minusMonths(1) }
    fun nextMonth() { calendarMonth.value = calendarMonth.value.plusMonths(1) }

    fun togglePlanned(date: LocalDate) {
        viewModelScope.launch { graph.work.togglePlanned(date) }
    }

    fun setTaxCoordination(done: Boolean) {
        viewModelScope.launch { graph.settings.setTaxCoordination(LocalDate.now(zone).year, done) }
    }

    fun setInsuranceCoordination(done: Boolean) {
        viewModelScope.launch { graph.settings.setInsuranceCoordination(LocalDate.now(zone).year, done) }
    }

    fun dismissTwoEmployers() {
        viewModelScope.launch { graph.settings.setTwoEmployersDismissed(LocalDate.now(zone).year) }
    }

    /**
     * [rateShekels] is per hour or per day according to [payType]; [travelShekels] is the reimbursement
     * per shift (0 = none). [main] marks the new job as the main one (and unmarks every other).
     */
    fun addJob(name: String, payType: PayType, rateShekels: Double, multiplier: Double, travelShekels: Double, main: Boolean) {
        viewModelScope.launch {
            val id = graph.work.addJob(name, payType, Money.toAgorot(rateShekels), multiplier, Money.toAgorot(travelShekels))
            if (main) graph.work.setMainJob(id)
        }
    }

    /**
     * Changing the job's rate or travel amount affects new shifts only; saved shifts keep the rate
     * and travel stored on them. [main] on marks this job as the main one (exactly one main);
     * off removes its mark, and the automatic choice applies again.
     */
    fun updateJob(job: JobEntity, name: String, payType: PayType, rateShekels: Double, multiplier: Double, travelShekels: Double, main: Boolean) {
        val rate = Money.toAgorot(rateShekels)
        viewModelScope.launch {
            graph.work.updateJob(
                job.copy(
                    name = name,
                    payType = payType.name,
                    hourlyRateAgorot = if (payType == PayType.HOURLY) rate else 0L,
                    dailyRateAgorot = if (payType == PayType.DAILY) rate else 0L,
                    shabbatMultiplier = multiplier,
                    travelPerShiftAgorot = Money.toAgorot(travelShekels).coerceAtLeast(0L),
                ),
            )
            if (main) graph.work.setMainJob(job.id) else graph.work.clearMainJob(job.id)
        }
    }

    private companion object {
        const val RECENT_SHIFTS = 8
    }

    fun setJobActive(id: Long, active: Boolean) {
        viewModelScope.launch { graph.work.setJobActive(id, active) }
    }

    fun deleteShift(id: Long) {
        viewModelScope.launch { graph.work.deleteShift(id) }
    }
}
