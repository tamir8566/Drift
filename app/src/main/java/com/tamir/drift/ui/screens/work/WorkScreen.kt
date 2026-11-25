package com.tamir.drift.ui.screens.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.JobEntity
import com.tamir.drift.data.db.ShiftEntity
import com.tamir.drift.data.db.isDaily
import com.tamir.drift.data.db.pay
import com.tamir.drift.data.db.totalAgorot
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.domain.money.AmountInput
import com.tamir.drift.domain.work.MIN_HOURLY_WAGE_AGOROT
import com.tamir.drift.domain.work.MIN_HOURLY_WAGE_SINCE
import com.tamir.drift.domain.work.PayType
import com.tamir.drift.domain.work.ShiftCalculator
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.CheckRow
import com.tamir.drift.ui.components.Chip
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.FocusOnOpen
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.HeroNumber
import com.tamir.drift.ui.components.JobCard
import com.tamir.drift.ui.components.LegendDot
import com.tamir.drift.ui.components.MonthCalendar
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.Stat
import com.tamir.drift.ui.components.StatTriplet
import com.tamir.drift.ui.components.SwitchRow
import com.tamir.drift.ui.components.TransactionRow
import com.tamir.drift.ui.components.isNewSince
import com.tamir.drift.ui.components.rememberFeedbackActions
import com.tamir.drift.ui.components.rememberListSeenBefore
import com.tamir.drift.ui.components.rowMotion
import com.tamir.drift.ui.components.wholeShekels
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * PRD "מסך עבודה".
 *
 * The calendar: a tap on a day with shifts lists them (also when there is only one) with the
 * day total, each opening for editing, and "הוספת משמרת ליום הזה". A past day without a shift
 * opens a new shift on that date; today without one offers that and "סמן כמתוכנן"; a future day
 * is marked / unmarked as planned. A day with a shift never wears the planned ring.
 */
@Composable
fun WorkScreen(onAddShift: () -> Unit, onAddShiftOn: (LocalDate) -> Unit, onEditShift: (Long) -> Unit, onOpenAllShifts: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm = driftViewModel { WorkViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val feedback = rememberFeedbackActions()
    // Shifts that were added since this list was last looked at land with a short tint.
    val shiftsSeenBefore = rememberListSeenBefore(SettingsStore.LIST_WORK_RECENT)

    var showNewJob by remember { mutableStateOf(false) }
    var editingJob by remember { mutableStateOf<JobEntity?>(null) }
    // The calendar day whose sheet is open: its shifts, or — for today without one — what to do with it.
    var openDay by remember { mutableStateOf<LocalDate?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                stringResource(R.string.work_title), onBack = null,
                endContent = { Chip(stringResource(R.string.work_add_job_short), selected = true, onClick = { showNewJob = true }) },
            )
        }

        // Hero: earned this month
        item(key = "hero") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.work_earned_month), style = DriftType.Label13, color = DriftColors.TextSecondary)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // A dim "—" until the shifts are loaded; a new shift rolls the number to its new value.
                    HeroNumber(
                        value = if (state.loading) null else wholeShekels(Money.toShekels(state.earnedMonthAgorot)),
                        color = DriftColors.Text,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(stringResource(R.string.shekel), style = DriftType.HeroCurrency, color = DriftColors.TextSecondary, modifier = Modifier.alignByBaseline())
                }
                // The month figure includes travel reimbursements; say how much of it they are.
                if (state.travelMonthAgorot > 0L) {
                    Text(
                        stringResource(R.string.work_travel_of_month, Fmt.moneyAgorot(state.travelMonthAgorot)),
                        style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // This week · hours · shifts
        item(key = "stats") {
            StatTriplet(
                listOf(
                    Stat(stringResource(R.string.work_this_week), stringResource(R.string.amount_with_shekel, Fmt.moneyAgorot(state.earnedWeekAgorot)), DriftColors.Lime),
                    Stat(stringResource(R.string.work_hours_month), ShiftCalculator.formatHoursShort(state.hoursMonth)),
                    Stat(stringResource(R.string.work_shifts_month), state.shiftsMonth.toString()),
                ),
            )
        }

        // Two employers: tax / National Insurance coordination, ticked by the user, per calendar year.
        if (state.showTwoEmployers) {
            item(key = "two-employers") {
                GlassCard(Modifier.fillMaxWidth(), borderColor = DriftColors.PlusBorder) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.two_employers_title_year, state.year), style = DriftType.RowTitle, color = DriftColors.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.two_employers_dismiss), style = DriftType.Link12, color = DriftColors.TextSecondary,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.dismissTwoEmployers() }.padding(6.dp),
                            )
                        }
                        CheckRow(
                            stringResource(R.string.two_employers_tax), stringResource(R.string.two_employers_tax_desc),
                            checked = state.taxCoordinationDone, onCheckedChange = { vm.setTaxCoordination(it) },
                        )
                        CheckRow(
                            stringResource(R.string.two_employers_insurance), stringResource(R.string.two_employers_insurance_desc),
                            checked = state.insuranceCoordinationDone, onCheckedChange = { vm.setInsuranceCoordination(it) },
                        )
                    }
                }
            }
        }

        // Calendar
        item(key = "calendar") {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MonthCalendar(
                        month = state.calendarMonth,
                        workedDays = state.workedDays,
                        plannedDays = state.plannedDays,
                        today = state.today,
                        onDayClick = { date ->
                            val shifts = state.shiftsByDay[date.toEpochDay()].orEmpty()
                            when {
                                // A day with shifts shows them — one or several, always the same list.
                                shifts.isNotEmpty() -> { openDay = date }
                                // A future day can only be planned.
                                date.isAfter(state.today) -> {
                                    feedback.tick()
                                    vm.togglePlanned(date)
                                }
                                // Today: add the shift, or mark the day as planned.
                                date == state.today -> { openDay = date }
                                // A past day without a shift: add it, on that date.
                                else -> onAddShiftOn(date)
                            }
                        },
                        onPrevMonth = { vm.previousMonth() },
                        onNextMonth = { vm.nextMonth() },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(stringResource(R.string.work_legend_worked), DriftColors.Lime)
                        LegendDot(stringResource(R.string.work_legend_planned), DriftColors.Lime, ring = true)
                    }
                    Text(stringResource(R.string.work_calendar_hint), style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Per-job cards 2×2
        item(key = "jobs") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionHeader(stringResource(R.string.work_by_job))
                if (state.jobCards.isEmpty() && !state.loading) EmptyNote(stringResource(R.string.work_no_jobs))
                val shownCards = state.jobCards.take(4)
                // The travel line is reserved on every card once one job has it, so the cards stay the same height.
                val anyTravel = shownCards.any { it.job.travelPerShiftAgorot > 0L }
                val rows = shownCards.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c ->
                                // Keyed by job: when the order changes, a bar animates from its own previous value.
                                key(c.job.id) {
                                    JobCard(
                                        name = c.job.name,
                                        amountText = stringResource(R.string.amount_with_shekel, Fmt.moneyAgorot(c.monthAmountAgorot)),
                                        metaText = if (c.job.isDaily) {
                                            if (c.monthDays == 1) stringResource(R.string.work_job_card_meta_daily_one) else stringResource(R.string.work_job_card_meta_daily, c.monthDays)
                                        } else {
                                            stringResource(R.string.work_job_card_meta, ShiftCalculator.formatHours(c.monthHours))
                                        },
                                        // The percent, the bar and this line are one value: the week's income of the job against the weekly goal.
                                        bar = c.weekBar,
                                        referenceText = stringResource(R.string.work_job_card_week, Fmt.moneyAgorot(c.weekAmountAgorot), Fmt.moneyAgorot(c.weeklyGoalAgorot)),
                                        color = Color(c.job.color),
                                        modifier = Modifier.weight(1f),
                                        onClick = { editingJob = c.job },
                                        extraText = when {
                                            c.job.travelPerShiftAgorot > 0L -> stringResource(R.string.work_job_travel, Fmt.moneyExact(c.job.travelPerShiftAgorot))
                                            anyTravel -> ""
                                            else -> null
                                        },
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Recent shifts
        item(key = "recent-header") { SectionHeader(stringResource(R.string.work_recent_shifts), stringResource(R.string.add_menu_shift), onAction = onAddShift, modifier = Modifier.padding(bottom = 0.dp)) }
        if (state.recentShifts.isEmpty() && !state.loading) item(key = "recent-empty") { EmptyNote(stringResource(R.string.work_no_shifts)) }
        items(state.recentShifts, key = { it.id }) { shift ->
            ShiftRow(shift, state.jobsById[shift.jobId], onClick = { onEditShift(shift.id) }, modifier = rowMotion(isNewSince(shift.createdAt, shiftsSeenBefore)))
        }
        // Older shifts are reached from here (the list above shows only the latest ones).
        if (state.totalShifts > 0) {
            item(key = "all-shifts") {
                Text(
                    stringResource(R.string.work_all_shifts_count, state.totalShifts), style = DriftType.Link12, color = DriftColors.Lime,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenAllShifts).padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }

        // Manage jobs
        item(key = "manage") {
            Column {
                SectionHeader(stringResource(R.string.work_manage_jobs), stringResource(R.string.work_add_job), onAction = { showNewJob = true })
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        if (state.jobs.isEmpty()) EmptyNote(stringResource(R.string.work_no_jobs))
                        state.jobs.forEachIndexed { i, job ->
                            JobRow(
                                job = job,
                                isMain = job.id == state.mainJobId,
                                onEdit = { editingJob = job },
                                onToggleActive = { vm.setJobActive(job.id, !job.isActive) },
                            )
                            if (i < state.jobs.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(4.dp)) }
    }

    if (showNewJob) {
        // The very first active job is the main one by itself.
        JobDialog(title = stringResource(R.string.work_add_job), job = null, isMain = state.jobs.none { it.isActive }, onDismiss = { showNewJob = false }) { name, payType, rate, mult, travel, main ->
            vm.addJob(name, payType, rate, mult, travel, main); showNewJob = false
        }
    }
    editingJob?.let { job ->
        JobDialog(title = stringResource(R.string.edit), job = job, isMain = job.id == state.mainJobId, onDismiss = { editingJob = null }) { name, payType, rate, mult, travel, main ->
            vm.updateJob(job, name, payType, rate, mult, travel, main); editingJob = null
        }
    }
    openDay?.let { day ->
        val shifts = state.shiftsByDay[day.toEpochDay()].orEmpty()
        DriftDialog(
            title = dayTitle(day),
            onDismiss = { openDay = null },
            confirmText = stringResource(R.string.close),
            onConfirm = { openDay = null },
        ) {
            shifts.forEach { shift ->
                ShiftRow(shift, state.jobsById[shift.jobId], onClick = { openDay = null; onEditShift(shift.id) }, showDate = false)
            }
            if (shifts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.shift_total_day), style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1)
                    Text(
                        stringResource(R.string.amount_with_shekel, Fmt.moneyExact(shifts.sumOf { it.totalAgorot })),
                        style = DriftType.RowAmount, color = DriftColors.Text, maxLines = 1,
                    )
                }
            }
            DayAction(stringResource(R.string.work_day_add_shift), DriftColors.Lime) { openDay = null; onAddShiftOn(day) }
            // Planning is for a day that has no shift yet; of the days that open this sheet, that is today.
            if (shifts.isEmpty() && day == state.today) {
                val planned = day.toEpochDay() in state.plannedDays
                DayAction(
                    stringResource(if (planned) R.string.work_day_unmark_planned else R.string.work_day_mark_planned),
                    DriftColors.TextSecondary,
                ) {
                    feedback.tick()
                    vm.togglePlanned(day)
                    openDay = null
                }
            }
        }
    }
}

/** "יום ה׳ · 19.11" — the title of a calendar day's sheet. */
@Composable
private fun dayTitle(date: LocalDate): String {
    val shortDate = Fmt.shortDate(date)
    if (date.dayOfWeek == DayOfWeek.SATURDAY) return stringResource(R.string.work_day_title_saturday, shortDate)
    // Sunday → 0 … Friday → 5, like the calendar's columns.
    val initials = LocalContext.current.resources.getStringArray(R.array.day_initials)
    return stringResource(R.string.work_day_title, initials[date.dayOfWeek.value % 7], shortDate)
}

/** One text action of a calendar day's sheet. */
@Composable
private fun DayAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, style = DriftType.RowTitle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
    )
}

/**
 * "job · date · hours · שבת" row with the shift's income (wage + travel), reusing the transaction
 * row shape. A daily job shows "יום" instead of hours; a shift with travel gets a "כולל נסיעות" tag.
 * [showDate] off leaves the date out (a list that is already about one day).
 */
@Composable
fun ShiftRow(shift: ShiftEntity, job: JobEntity?, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = true) {
    val date = LocalDate.ofEpochDay(shift.date)
    val daily = job?.isDaily == true
    val meta = buildList {
        if (showDate) add(Fmt.shortDate(date))
        if (daily) {
            add(stringResource(R.string.work_shift_day))
        } else {
            add(stringResource(R.string.work_hours_value, ShiftCalculator.formatHours(shift.hours)))
            if (shift.startTime != null && shift.endTime != null) {
                add("${ShiftCalculator.formatClock(shift.startTime)}–${ShiftCalculator.formatClock(shift.endTime)}")
            }
        }
    }.joinToString(" · ")
    val tags = buildList {
        if (shift.isShabbatRate) add(stringResource(R.string.work_shabbat_tag))
        if (shift.travelAgorot > 0L) add(stringResource(R.string.work_travel_tag))
    }
    TransactionRow(
        // The job row is gone (a shift can outlive its job): say so instead of "no jobs yet".
        title = job?.name ?: stringResource(R.string.work_job_deleted),
        meta = meta,
        amountText = stringResource(R.string.amount_with_shekel, Fmt.moneyExact(shift.totalAgorot)),
        dotColor = job?.let { Color(it.color) } ?: DriftColors.TextDim,
        sharedTag = if (tags.isEmpty()) null else tags.joinToString(" · "),
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun JobRow(job: JobEntity, isMain: Boolean, onEdit: () -> Unit, onToggleActive: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                job.name, style = DriftType.RowTitle, color = if (job.isActive) DriftColors.Text else DriftColors.TextDim,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (isMain) add(stringResource(R.string.work_job_main_tag))
                if (job.isDaily) add(stringResource(R.string.work_job_rate_daily, Fmt.moneyExact(job.dailyRateAgorot)))
                else add(stringResource(R.string.work_job_rate, Fmt.moneyExact(job.hourlyRateAgorot)))
                add(stringResource(R.string.work_job_shabbat, ShiftCalculator.formatHours(job.shabbatMultiplier)))
                if (job.travelPerShiftAgorot > 0L) add(stringResource(R.string.work_job_travel, Fmt.moneyExact(job.travelPerShiftAgorot)))
                else add(stringResource(R.string.work_job_no_travel))
                if (!job.isActive) add(stringResource(R.string.work_job_inactive))
            }.joinToString(" · ")
            Text(meta, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (job.isActive) stringResource(R.string.work_job_deactivate) else stringResource(R.string.work_job_activate),
            style = DriftType.Link12, color = if (job.isActive) DriftColors.Orange else DriftColors.Lime,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onToggleActive).padding(6.dp),
        )
    }
}

@Composable
private fun JobDialog(
    title: String,
    job: JobEntity?,
    /** Whether this job is the main one right now (marked, or picked automatically). */
    isMain: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, PayType, Double, Double, Double, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(job?.name ?: "") }
    var payType by remember { mutableStateOf(job?.pay ?: PayType.HOURLY) }
    // One field per pay type so switching chips does not lose what was typed.
    var hourlyRate by remember { mutableStateOf(job?.takeIf { it.hourlyRateAgorot > 0L }?.let { Fmt.fieldAmount(it.hourlyRateAgorot) } ?: "") }
    var dailyRate by remember { mutableStateOf(job?.takeIf { it.dailyRateAgorot > 0L }?.let { Fmt.fieldAmount(it.dailyRateAgorot) } ?: "") }
    var multiplier by remember { mutableStateOf(job?.let { ShiftCalculator.formatHours(it.shabbatMultiplier) } ?: "1.5") }
    // Travel reimbursement per shift: optional — an empty field means none (0).
    var travel by remember { mutableStateOf(job?.takeIf { it.travelPerShiftAgorot > 0L }?.let { Fmt.fieldAmount(it.travelPerShiftAgorot) } ?: "") }
    // Explicit choice: a job either pays travel (amount required) or is marked as paying none.
    var hasTravel by remember { mutableStateOf(job?.let { it.travelPerShiftAgorot > 0L } ?: false) }
    var main by remember { mutableStateOf(isMain) }
    val nameFocus = remember { FocusRequester() }
    val rate = when (payType) {
        PayType.HOURLY -> hourlyRate
        PayType.DAILY -> dailyRate
    }
    val rateValue = AmountInput.parse(rate)
    val multValue = AmountInput.parse(multiplier)
    val travelValue = if (!hasTravel) 0.0 else AmountInput.parse(travel)
    val rateOk = rateValue != null && rateValue > 0.0
    val multOk = multValue != null && multValue >= 1.0
    val travelOk = !hasTravel || (travelValue != null && travelValue > 0.0)
    val valid = name.isNotBlank() && rateOk && multOk && travelOk
    // A warning only (the job can still be saved): an hourly rate under the legal minimum.
    val belowMinimum = payType == PayType.HOURLY && rateValue != null && rateValue > 0.0 && Money.toAgorot(rateValue) < MIN_HOURLY_WAGE_AGOROT
    DriftDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.save),
        onConfirm = { if (valid && rateValue != null && multValue != null && travelValue != null) onSave(name.trim(), payType, rateValue, multValue, travelValue, main) },
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = valid,
    ) {
        // The dialog itself scrolls (DriftDialog), so the last inputs stay reachable above the keyboard.
        DriftTextField(name, { name = it }, stringResource(R.string.work_job_name), focusRequester = nameFocus)
        // A new job opens ready for typing in its first empty field; an existing one is left as it is.
        FocusOnOpen(nameFocus, enabled = job == null)
        Text(stringResource(R.string.work_pay_type), style = DriftType.Label12, color = DriftColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.work_pay_hourly), payType == PayType.HOURLY, onClick = { payType = PayType.HOURLY })
            Chip(stringResource(R.string.work_pay_daily), payType == PayType.DAILY, onClick = { payType = PayType.DAILY })
        }
        when (payType) {
            PayType.HOURLY -> DriftTextField(
                hourlyRate, { hourlyRate = it }, stringResource(R.string.work_job_hourly_rate), keyboardType = KeyboardType.Decimal,
                isError = hourlyRate.isNotBlank() && !rateOk,
                supporting = if (hourlyRate.isNotBlank() && !rateOk) stringResource(R.string.work_job_invalid_rate) else null,
            )
            PayType.DAILY -> DriftTextField(
                dailyRate, { dailyRate = it }, stringResource(R.string.work_job_daily_rate), keyboardType = KeyboardType.Decimal,
                isError = dailyRate.isNotBlank() && !rateOk,
                supporting = if (dailyRate.isNotBlank() && !rateOk) stringResource(R.string.work_job_invalid_rate) else null,
            )
        }
        if (belowMinimum) {
            Text(
                stringResource(R.string.work_job_below_minimum, Fmt.moneyExact(MIN_HOURLY_WAGE_AGOROT), Fmt.monthLabel(context, MIN_HOURLY_WAGE_SINCE, withYear = true)),
                style = DriftType.Label11, color = DriftColors.Orange, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        DriftTextField(
            multiplier, { multiplier = it }, stringResource(R.string.work_job_multiplier), keyboardType = KeyboardType.Decimal,
            isError = multiplier.isNotBlank() && !multOk,
            supporting = if (multiplier.isNotBlank() && !multOk) stringResource(R.string.work_job_invalid_multiplier) else null,
        )
        // Same for hourly and daily jobs: the switch says whether the job pays travel at all;
        // when on, the amount is saved on the job and added to every new shift by itself.
        SwitchRow(
            stringResource(R.string.work_job_has_travel), hasTravel, { hasTravel = it },
            subtitle = stringResource(if (hasTravel) R.string.work_job_has_travel_on else R.string.work_job_has_travel_off),
        )
        if (hasTravel) {
            DriftTextField(
                travel, { travel = it }, stringResource(R.string.work_job_travel_field), keyboardType = KeyboardType.Decimal,
                isError = travel.isNotBlank() && !travelOk,
                supporting = if (travel.isNotBlank() && !travelOk) stringResource(R.string.work_job_invalid_travel) else null,
            )
        }
        // "עבודה ראשית": exactly one job is the main one — turning it on here turns it off elsewhere.
        SwitchRow(
            stringResource(R.string.work_job_main), main, { main = it },
            subtitle = stringResource(R.string.work_job_main_desc),
        )
    }
}
