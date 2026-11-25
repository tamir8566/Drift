package com.tamir.drift.domain.work

import com.tamir.drift.domain.format.BarValue
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

/**
 * PRD "מסך יעד" — the monthly saving goal. All amounts in agorot.
 *
 * A month does not stand alone: what earlier months saved above or below their goal is carried
 * in ([carryInAgorot]), so this month has to save [adjustedGoalAgorot] — its own goal plus what
 * is still missing, or minus the surplus. The line, its percent, "בכיוון" and the texts all refer
 * to that one adjusted goal.
 */
data class MonthGoalStatus(
    val month: YearMonth,
    val earnedAgorot: Long,
    val spentAgorot: Long,
    /** This month's own goal, before the carry-over from earlier months. */
    val goalAgorot: Long,
    /** Days of the month that already passed, including today (0 when the month is in the future). */
    val daysElapsed: Int,
    val daysInMonth: Int,
    /**
     * Rate of the main job, used to express money as work: agorot per hour for an hourly job,
     * agorot per day for a daily one (see [mainPayType]).
     */
    val mainRateAgorot: Long,
    /** Pay type of the main job: decides whether shortfalls are given in hours or in days. */
    val mainPayType: PayType = PayType.HOURLY,
    /**
     * Σ (saved − goal) over the earlier months since the tracking start: positive = a surplus,
     * negative = what is still missing. 0 in the first counted month.
     */
    val carryInAgorot: Long = 0L,
) {
    /** True when the main job is paid per day — the UI then shows "עוד כ־N ימי עבודה". */
    val mainJobIsDaily: Boolean get() = mainPayType == PayType.DAILY
    /** PRD: saved this month = shifts income (wage + travel) − Σ amountMine of the month's transactions. May be negative. */
    val savedAgorot: Long get() = earnedAgorot - spentAgorot

    /** True when earlier months changed what this month has to save (nothing is carried without a goal). */
    val hasCarry: Boolean get() = goalAgorot > 0L && carryInAgorot != 0L

    /**
     * What this month has to save after the carry-over. Zero or less when the surplus of earlier
     * months covers the whole month (then it is how much of that surplus may still be used up).
     */
    val needAgorot: Long get() = if (goalAgorot > 0L) goalAgorot - carryInAgorot else goalAgorot

    /** The goal the screen shows: [needAgorot], never below zero. */
    val adjustedGoalAgorot: Long get() = needAgorot.coerceAtLeast(0L)

    /** True when the surplus of earlier months already covers this whole month. */
    val isCoveredByCarry: Boolean get() = goalAgorot > 0L && carryInAgorot >= goalAgorot

    /** PRD: percent of the month's goal (after the carry-over). */
    val percentOfGoal: Double get() = if (adjustedGoalAgorot > 0) savedAgorot.toDouble() / adjustedGoalAgorot * 100.0 else 0.0

    /** The one value the goal line draws and its percent prints: saved against the adjusted goal. */
    val bar: BarValue get() =
        if (isCoveredByCarry) BarValue.ofFraction(if (savedAgorot >= needAgorot) 1.0 else 0.0)
        else BarValue.of(savedAgorot, adjustedGoalAgorot)

    /**
     * What should have been saved by today: the adjusted goal × daysElapsed / daysInMonth. When
     * the carried surplus covers the month it is [needAgorot] (≤ 0): the month stays on track as
     * long as it does not eat more than the extra surplus.
     */
    val expectedByNowAgorot: Long get() = when {
        goalAgorot <= 0L || daysInMonth <= 0 -> 0L
        needAgorot <= 0L -> needAgorot
        else -> (needAgorot.toDouble() * daysElapsed / daysInMonth).roundToLong()
    }

    /** PRD: "בכיוון" when saved so far ≥ the pro-rated goal. */
    val onTrack: Boolean get() = savedAgorot >= expectedByNowAgorot

    /** How far behind the pro-rated goal (0 when on track). */
    val shortfallAgorot: Long get() = (expectedByNowAgorot - savedAgorot).coerceAtLeast(0L)

    /** PRD: "עוד כ־N שעות עבודה" — N = ceil(shortfall / main hourly rate). 0 when the main job is daily. */
    val shortfallHours: Int get() = if (mainJobIsDaily) 0 else unitsFor(shortfallAgorot)

    /** "עוד כ־N ימי עבודה" — N = ceil(shortfall / main daily rate). 0 when the main job is hourly. */
    val shortfallDays: Int get() = if (mainJobIsDaily) unitsFor(shortfallAgorot) else 0

    /** What is still missing to reach the month's adjusted goal (0 when met or covered). */
    val remainingToGoalAgorot: Long get() = (needAgorot - savedAgorot).coerceAtLeast(0L)

    /** Hours of work that would close [remainingToGoalAgorot] (the recommendation line). 0 for a daily main job. */
    val remainingHours: Int get() = if (mainJobIsDaily) 0 else unitsFor(remainingToGoalAgorot)

    /** Days of work that would close [remainingToGoalAgorot]. 0 for an hourly main job. */
    val remainingDays: Int get() = if (mainJobIsDaily) unitsFor(remainingToGoalAgorot) else 0

    /** Reached by this month's own saving. A month that the carried surplus covers from its first day is not "met". */
    val isGoalMet: Boolean get() = goalAgorot > 0L && !isCoveredByCarry && savedAgorot >= needAgorot

    /** Work units (hours or days, per [mainPayType]) that cover [agorot] at the main rate. */
    fun unitsFor(agorot: Long): Int =
        if (mainRateAgorot <= 0L || agorot <= 0L) 0 else ceil(agorot.toDouble() / mainRateAgorot).toInt()
}

/** A Sunday–Saturday week (its first one may begin at the tracking start). Both ends inclusive. */
data class WeekRange(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    fun contains(epochDay: Long): Boolean = epochDay in start.toEpochDay()..end.toEpochDay()
}

/** PRD week card: income this week vs the weekly goal, with the carry-over from every earlier week since the tracking start. */
data class WeekStatus(
    val range: WeekRange,
    val incomeAgorot: Long,
    /** The weekly goal, pro-rated when the week begins at the tracking start. */
    val goalAgorot: Long,
    /** Σ (income − goal) over all earlier weeks since the tracking start (months do not reset it); positive = surplus. 0 in the first week. */
    val carryInAgorot: Long,
) {
    /** PRD: the balance carried to next week — "אתה בפלוס X" (>0) / "חסרים X" (<0). */
    val balanceAgorot: Long get() = carryInAgorot + incomeAgorot - goalAgorot

    /**
     * What this week needs after the carry-over (never negative). The week card's bar, its
     * "X מתוך Y" and its surplus / deficit message all refer to this one goal.
     */
    val adjustedGoalAgorot: Long get() = (goalAgorot - carryInAgorot).coerceAtLeast(0L)

    /** True when earlier weeks changed this week's goal. */
    val hasCarry: Boolean get() = carryInAgorot != 0L

    /** True when the surplus of earlier weeks already covers this whole week. */
    val isCoveredByCarry: Boolean get() = goalAgorot > 0L && carryInAgorot >= goalAgorot

    val isSurplus: Boolean get() = balanceAgorot > 0L
    val isDeficit: Boolean get() = balanceAgorot < 0L

    /** The one value the week bar draws and its percent prints: income against the adjusted goal. */
    val bar: BarValue get() = if (isCoveredByCarry) BarValue.ofFraction(1.0) else BarValue.of(incomeAgorot, adjustedGoalAgorot)

    /** Bar fraction of the adjusted weekly goal, 0..1. */
    val progress: Double get() = bar.fraction.toDouble()
}

/** One bar of the 6-month history. */
data class MonthSavingHistory(val month: YearMonth, val savedAgorot: Long, val goalAgorot: Long) {
    val met: Boolean get() = savedAgorot >= goalAgorot
}

object GoalCalculator {

    // ---- months ----

    /**
     * The month of [today] (or any [month]) against the saving goal. [goal] is the goal over time;
     * what the earlier months since [startDate] saved above or below their goal is carried in.
     * Shifts and expenses dated after [today] do not count yet.
     */
    fun monthStatus(
        shifts: List<ShiftSummary>,
        transactions: List<TxnSummary>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
        goal: GoalHistory,
        mainRateAgorot: Long,
        /** Tracking start: days before it are not expected to have saved anything, months before it carry nothing. */
        startDate: LocalDate? = null,
        /** Pay type of the main job; with [PayType.DAILY] the rate is per day and shortfalls are in days. */
        mainPayType: PayType = PayType.HOURLY,
    ): MonthGoalStatus {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        // The part of the month that counts: from the tracking start (if inside this month) to its end.
        val effectiveStart = if (startDate != null && startDate.isAfter(first)) startDate else first
        val daysInMonth = (ChronoUnit.DAYS.between(effectiveStart, last).toInt() + 1).coerceAtLeast(0)
        val daysElapsed = when {
            today.isBefore(effectiveStart) -> 0
            today.isAfter(last) -> daysInMonth
            else -> ChronoUnit.DAYS.between(effectiveStart, today).toInt() + 1
        }
        // One cut-off for both sides, the same as the budget summary: shifts and expenses through
        // the end of today (anything dated later waits for its day).
        val spentBefore = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return MonthGoalStatus(
            month = month,
            earnedAgorot = earnedIn(shifts, month, today.toEpochDay()),
            spentAgorot = spentIn(transactions, month, zone, spentBefore),
            goalAgorot = goal.at(last),
            daysElapsed = daysElapsed,
            daysInMonth = daysInMonth,
            mainRateAgorot = mainRateAgorot,
            mainPayType = mainPayType,
            carryInAgorot = monthCarry(shifts, transactions, month, zone, goal, startDate),
        )
    }

    /** [monthStatus] with a goal that never changed. */
    fun monthStatus(
        shifts: List<ShiftSummary>,
        transactions: List<TxnSummary>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
        goalAgorot: Long,
        mainRateAgorot: Long,
        startDate: LocalDate? = null,
        mainPayType: PayType = PayType.HOURLY,
    ): MonthGoalStatus =
        monthStatus(shifts, transactions, month, today, zone, GoalHistory.fixed(goalAgorot), mainRateAgorot, startDate, mainPayType)

    /**
     * The balance carried into [month]: Σ (saved − goal) over the months from the tracking start
     * (without one: from the first record) up to the month before. Each month is judged by the
     * goal that was in force in it; a month without a goal carries nothing.
     */
    fun monthCarry(
        shifts: List<ShiftSummary>,
        transactions: List<TxnSummary>,
        month: YearMonth,
        zone: ZoneId,
        goal: GoalHistory,
        startDate: LocalDate? = null,
    ): Long {
        val countedShifts = if (startDate == null) shifts else shifts.filter { it.epochDay >= startDate.toEpochDay() }
        val countedTxs = if (startDate == null) transactions else {
            val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            transactions.filter { it.timestamp >= startMillis }
        }
        val firstMonth: YearMonth = startDate?.let { YearMonth.from(it) }
            ?: listOfNotNull(
                countedShifts.minOfOrNull { it.epochDay }?.let { YearMonth.from(LocalDate.ofEpochDay(it)) },
                countedTxs.minOfOrNull { it.timestamp }?.let { MonthPeriod.of(it, zone).yearMonth },
            ).minOrNull()
            ?: return 0L
        var carry = 0L
        var m = firstMonth
        while (m.isBefore(month)) {
            val monthGoal = goal.at(m.atEndOfMonth())
            if (monthGoal > 0L) carry += earnedIn(countedShifts, m) - spentIn(countedTxs, m, zone) - monthGoal
            m = m.plusMonths(1)
        }
        return carry
    }

    /** Σ hours of the month's shifts that belong to hourly jobs (days of daily jobs carry no hours). */
    fun hourlyHoursIn(shifts: List<ShiftSummary>, jobs: List<JobSummary>, month: YearMonth, throughEpochDay: Long = Long.MAX_VALUE): Double {
        val hourlyIds = jobs.filter { it.payType == PayType.HOURLY }.map { it.id }.toSet()
        return hoursIn(shifts.filter { it.jobId in hourlyIds }, month, throughEpochDay)
    }

    /**
     * Σ shift income (wage + travel reimbursement) of the shifts whose day falls in [month].
     * With [throughEpochDay] (today), shifts dated later are not earned yet and do not count.
     */
    fun earnedIn(shifts: List<ShiftSummary>, month: YearMonth, throughEpochDay: Long = Long.MAX_VALUE): Long =
        inMonth(shifts, month, throughEpochDay).sumOf { it.totalAgorot }

    /** The travel-reimbursement part of [earnedIn] ("מתוכם נסיעות"). */
    fun travelIn(shifts: List<ShiftSummary>, month: YearMonth, throughEpochDay: Long = Long.MAX_VALUE): Long =
        inMonth(shifts, month, throughEpochDay).sumOf { it.travelAgorot }

    /** Σ hours of shifts in [month]. */
    fun hoursIn(shifts: List<ShiftSummary>, month: YearMonth, throughEpochDay: Long = Long.MAX_VALUE): Double =
        inMonth(shifts, month, throughEpochDay).sumOf { it.hours }

    /** The shifts of [month], up to and including [throughEpochDay]. */
    fun inMonth(shifts: List<ShiftSummary>, month: YearMonth, throughEpochDay: Long = Long.MAX_VALUE): List<ShiftSummary> {
        val first = month.atDay(1).toEpochDay()
        val last = minOf(month.atEndOfMonth().toEpochDay(), throughEpochDay)
        return shifts.filter { it.epochDay in first..last }
    }

    /**
     * Σ my share (agorot) of the month's expenses minus its refunds; transfers are not counted.
     * Only transactions before [beforeMillis] count (default: the whole month).
     */
    fun spentIn(transactions: List<TxnSummary>, month: YearMonth, zone: ZoneId, beforeMillis: Long = Long.MAX_VALUE): Long {
        val period = MonthPeriod(month, zone)
        return transactions
            .filter { period.contains(it.timestamp) && it.timestamp < beforeMillis }
            .sumOf { (it.net * 100).roundToLong() }
    }

    /**
     * PRD: last [months] months ending at [endMonth] (oldest first), saved vs the goal that was in
     * force in each. With [today], records dated after it are left out (the current month so far).
     */
    fun history(
        shifts: List<ShiftSummary>,
        transactions: List<TxnSummary>,
        endMonth: YearMonth,
        goal: GoalHistory,
        zone: ZoneId,
        months: Int = 6,
        startDate: LocalDate? = null,
        today: LocalDate? = null,
    ): List<MonthSavingHistory> {
        val throughDay = today?.toEpochDay() ?: Long.MAX_VALUE
        val beforeMillis = today?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        return (months - 1 downTo 0).map { back ->
            val m = endMonth.minusMonths(back.toLong())
            MonthSavingHistory(m, earnedIn(shifts, m, throughDay) - spentIn(transactions, m, zone, beforeMillis), goal.at(m.atEndOfMonth()))
        }.filter { startDate == null || !it.month.isBefore(YearMonth.from(startDate)) }
    }

    /** [history] with a goal that never changed. */
    fun history(
        shifts: List<ShiftSummary>,
        transactions: List<TxnSummary>,
        endMonth: YearMonth,
        goalAgorot: Long,
        zone: ZoneId,
        months: Int = 6,
        startDate: LocalDate? = null,
        today: LocalDate? = null,
    ): List<MonthSavingHistory> = history(shifts, transactions, endMonth, GoalHistory.fixed(goalAgorot), zone, months, startDate, today)

    // ---- weeks ----

    /** The Sunday–Saturday week containing [date]. */
    fun weekOf(date: LocalDate): WeekRange {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return WeekRange(start, start.plusDays(6))
    }

    /** A full week gets the goal; a week that begins at the tracking start gets goal × days / 7. */
    fun prorate(weeklyGoalAgorot: Long, days: Int): Long =
        if (days >= 7) weeklyGoalAgorot else (weeklyGoalAgorot.toDouble() * days / 7.0).roundToLong()

    /**
     * Σ shift income (wage + travel reimbursement) inside [range]. With [throughEpochDay]
     * (today), shifts dated later are not earned yet and do not count.
     */
    fun incomeIn(shifts: List<ShiftSummary>, range: WeekRange, throughEpochDay: Long = Long.MAX_VALUE): Long =
        shifts.filter { range.contains(it.epochDay) && it.epochDay <= throughEpochDay }.sumOf { it.totalAgorot }

    /**
     * PRD: the week of [today] against the weekly goal. The surplus or deficit of every earlier
     * week since the tracking start is carried in — the balance runs on from week to week and
     * does not reset when a month ends. Each week is judged by the goal that was in force in it.
     *
     * The count begins at [startDate]: the week it falls in starts on that day with a pro-rated
     * goal, and nothing before it counts. Without a start date the count begins with the week of
     * the first shift. Shifts dated after [today] are not earned yet.
     */
    fun weekStatus(
        shifts: List<ShiftSummary>,
        today: LocalDate,
        weeklyGoal: GoalHistory,
        startDate: LocalDate? = null,
    ): WeekStatus {
        val todayDay = today.toEpochDay()
        val counted = shifts.filter { it.epochDay <= todayDay && (startDate == null || it.epochDay >= startDate.toEpochDay()) }
        val current = weekOf(today)
        val begin: LocalDate = startDate ?: counted.minOfOrNull { it.epochDay }?.let { LocalDate.ofEpochDay(it) } ?: today

        /** The part of week [w] that counts: from the tracking start when it falls inside the week. */
        fun countedPart(w: WeekRange): WeekRange =
            if (startDate != null && startDate.isAfter(w.start) && !startDate.isAfter(w.end)) WeekRange(startDate, w.end) else w
        fun goalFor(part: WeekRange): Long = prorate(weeklyGoal.at(part.end), part.days)

        var carry = 0L
        var week = weekOf(begin)
        while (week.start.isBefore(current.start)) {
            val part = countedPart(week)
            carry += incomeIn(counted, part) - goalFor(part)
            week = WeekRange(week.start.plusWeeks(1), week.end.plusWeeks(1))
        }
        val range = countedPart(current)
        return WeekStatus(
            range = range,
            incomeAgorot = incomeIn(counted, range),
            goalAgorot = goalFor(range),
            carryInAgorot = carry,
        )
    }

    /** [weekStatus] with a goal that never changed. */
    fun weekStatus(
        shifts: List<ShiftSummary>,
        today: LocalDate,
        weeklyGoalAgorot: Long,
        startDate: LocalDate? = null,
    ): WeekStatus = weekStatus(shifts, today, GoalHistory.fixed(weeklyGoalAgorot), startDate)

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
