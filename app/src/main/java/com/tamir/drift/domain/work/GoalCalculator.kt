package com.tamir.drift.domain.work

import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.time.MonthPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.roundToLong

/** PRD "מסך יעד" — the monthly saving goal. All amounts in agorot. */
data class MonthGoalStatus(
    val month: YearMonth,
    val earnedAgorot: Long,
    val spentAgorot: Long,
    val goalAgorot: Long,
    /** Days of the month that already passed, including today (0 when the month is in the future). */
    val daysElapsed: Int,
    val daysInMonth: Int,
    /** Hourly rate of the main job, used to express money as working hours. */
    val mainRateAgorot: Long,
) {
    /** PRD: saved this month = shifts income − Σ amountMine of the month's transactions. May be negative. */
    val savedAgorot: Long get() = earnedAgorot - spentAgorot

    /** PRD: percent of the monthly goal. */
    val percentOfGoal: Double get() = if (goalAgorot > 0) savedAgorot.toDouble() / goalAgorot * 100.0 else 0.0

    /** PRD: goal × daysElapsed / daysInMonth. */
    val expectedByNowAgorot: Long get() =
        if (goalAgorot > 0 && daysInMonth > 0) (goalAgorot.toDouble() * daysElapsed / daysInMonth).roundToLong() else 0L

    /** PRD: "בכיוון" when saved so far ≥ the pro-rated goal. */
    val onTrack: Boolean get() = savedAgorot >= expectedByNowAgorot

    /** How far behind the pro-rated goal (0 when on track). */
    val shortfallAgorot: Long get() = (expectedByNowAgorot - savedAgorot).coerceAtLeast(0L)

    /** PRD: "עוד כ־N שעות עבודה" — N = ceil(shortfall / main rate). */
    val shortfallHours: Int get() = hoursFor(shortfallAgorot)

    /** What is still missing to reach the full monthly goal (0 when met). */
    val remainingToGoalAgorot: Long get() = (goalAgorot - savedAgorot).coerceAtLeast(0L)

    /** Hours of work that would close [remainingToGoalAgorot] (the recommendation line). */
    val remainingHours: Int get() = hoursFor(remainingToGoalAgorot)

    val isGoalMet: Boolean get() = goalAgorot > 0 && savedAgorot >= goalAgorot

    fun hoursFor(agorot: Long): Int =
        if (mainRateAgorot <= 0L || agorot <= 0L) 0 else ceil(agorot.toDouble() / mainRateAgorot).toInt()
}

/** A Sunday–Saturday week, possibly clipped to the month it belongs to. Both ends inclusive. */
data class WeekRange(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    fun contains(epochDay: Long): Boolean = epochDay in start.toEpochDay()..end.toEpochDay()
}

/** PRD week card: income this week vs the weekly goal, with the carry-over from earlier weeks of the month. */
data class WeekStatus(
    val range: WeekRange,
    val incomeAgorot: Long,
    /** The weekly goal, pro-rated when the week is clipped by the month edges. */
    val goalAgorot: Long,
    /** Σ (income − goal) over the previous weeks of the same month; positive = surplus. 0 in the first week. */
    val carryInAgorot: Long,
) {
    /** PRD: the balance carried to next week — "אתה בפלוס X" (>0) / "חסרים X" (<0). */
    val balanceAgorot: Long get() = carryInAgorot + incomeAgorot - goalAgorot

    /** What this week needs after the carry-over (never negative). */
    val adjustedGoalAgorot: Long get() = (goalAgorot - carryInAgorot).coerceAtLeast(0L)

    val isSurplus: Boolean get() = balanceAgorot > 0L
    val isDeficit: Boolean get() = balanceAgorot < 0L

    /** Bar fraction of the (unadjusted) weekly goal, 0..1. */
    val progress: Double get() = if (goalAgorot > 0) (incomeAgorot.toDouble() / goalAgorot).coerceIn(0.0, 1.0) else 0.0
}

/** One bar of the 6-month history. */
data class MonthSavingHistory(val month: YearMonth, val savedAgorot: Long, val goalAgorot: Long) {
    val met: Boolean get() = savedAgorot >= goalAgorot
}

object GoalCalculator {

    // ---- months ----

    fun monthStatus(
        shifts: List<ShiftSummary>,
        transactions: List<TxnSummary>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
        goalAgorot: Long,
        mainRateAgorot: Long,
    ): MonthGoalStatus {
        val daysInMonth = month.lengthOfMonth()
        val daysElapsed = when {
            today.isBefore(month.atDay(1)) -> 0
            today.isAfter(month.atEndOfMonth()) -> daysInMonth
            else -> today.dayOfMonth
        }
        return MonthGoalStatus(
            month = month,
            earnedAgorot = earnedIn(shifts, month),
            spentAgorot = spentIn(transactions, month, zone),
            goalAgorot = goalAgorot,
            daysElapsed = daysElapsed,
            daysInMonth = daysInMonth,
            mainRateAgorot = mainRateAgorot,
        )
    }

    /** Σ shift amounts whose day falls in [month]. */
    fun earnedIn(shifts: List<ShiftSummary>, month: YearMonth): Long {
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        return shifts.filter { it.epochDay in first..last }.sumOf { it.amountAgorot }
    }

    /** Σ hours of shifts in [month]. */
    fun hoursIn(shifts: List<ShiftSummary>, month: YearMonth): Double {
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        return shifts.filter { it.epochDay in first..last }.sumOf { it.hours }
    }

    /** Σ my share (agorot) of the month's transactions. */
    fun spentIn(transactions: List<TxnSummary>, month: YearMonth, zone: ZoneId): Long {
        val period = MonthPeriod(month, zone)
        return transactions.filter { period.contains(it.timestamp) }.sumOf { (it.myShare * 100).roundToLong() }
    }

    /** PRD: last [months] months ending at [endMonth] (oldest first), saved vs goal. */
    fun history(
        shifts: List<ShiftSummary>,
        transactions: List<TxnSummary>,
        endMonth: YearMonth,
        goalAgorot: Long,
        zone: ZoneId,
        months: Int = 6,
    ): List<MonthSavingHistory> = (months - 1 downTo 0).map { back ->
        val m = endMonth.minusMonths(back.toLong())
        MonthSavingHistory(m, earnedIn(shifts, m) - spentIn(transactions, m, zone), goalAgorot)
    }

    // ---- weeks ----

    /** The unclipped Sunday–Saturday week containing [date]. */
    fun weekOf(date: LocalDate): WeekRange {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return WeekRange(start, start.plusDays(6))
    }

    /** Sunday–Saturday weeks that touch [month], each clipped to the month (oldest first). */
    fun weeksOfMonth(month: YearMonth): List<WeekRange> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val result = mutableListOf<WeekRange>()
        var start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        while (!start.isAfter(last)) {
            val end = start.plusDays(6)
            result += WeekRange(maxOf(start, first), minOf(end, last))
            start = start.plusWeeks(1)
        }
        return result
    }

    /** A full week gets the goal; a clipped week gets goal × days / 7. */
    fun prorate(weeklyGoalAgorot: Long, days: Int): Long =
        if (days >= 7) weeklyGoalAgorot else (weeklyGoalAgorot.toDouble() * days / 7.0).roundToLong()

    fun incomeIn(shifts: List<ShiftSummary>, range: WeekRange): Long =
        shifts.filter { range.contains(it.epochDay) }.sumOf { it.amountAgorot }

    /**
     * PRD: the week of [today] against the weekly goal. Surplus/deficit of the earlier weeks of
     * the same month is carried in; nothing crosses a month boundary (the balance resets on the 1st).
     */
    fun weekStatus(shifts: List<ShiftSummary>, today: LocalDate, weeklyGoalAgorot: Long): WeekStatus {
        val month = YearMonth.from(today)
        val weeks = weeksOfMonth(month)
        val current = weeks.first { it.contains(today) }
        var carry = 0L
        for (w in weeks) {
            if (w == current) break
            carry += incomeIn(shifts, w) - prorate(weeklyGoalAgorot, w.days)
        }
        return WeekStatus(
            range = current,
            incomeAgorot = incomeIn(shifts, current),
            goalAgorot = prorate(weeklyGoalAgorot, current.days),
            carryInAgorot = carry,
        )
    }

    // ---- long-term savings goals ----

    /**
     * PRD: "בקצב של החודש תגיע ליעד ב־<חודש>". The month in which the goal is reached when
     * [monthlySavingAgorot] keeps being saved from [currentMonth] on; null when nothing is saved
     * (or the pace is negative), the current month when it is already reached.
     */
    fun projectedGoalMonth(
        targetAgorot: Long,
        savedAgorot: Long,
        monthlySavingAgorot: Long,
        currentMonth: YearMonth,
    ): YearMonth? {
        val remaining = targetAgorot - savedAgorot
        if (remaining <= 0L) return currentMonth
        if (monthlySavingAgorot <= 0L) return null
        val months = ceil(remaining.toDouble() / monthlySavingAgorot).toLong()
        return currentMonth.plusMonths(months - 1)
    }
}
