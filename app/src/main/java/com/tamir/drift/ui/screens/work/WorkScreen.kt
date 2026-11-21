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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.tamir.drift.domain.work.PayType
import com.tamir.drift.domain.work.ShiftCalculator
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.Chip
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.JobCard
import com.tamir.drift.ui.components.LegendDot
import com.tamir.drift.ui.components.MonthCalendar
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.Stat
import com.tamir.drift.ui.components.StatTriplet
import com.tamir.drift.ui.components.SwitchRow
import com.tamir.drift.ui.components.TransactionRow
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import java.time.LocalDate

/** PRD "מסך עבודה". */
@Composable
fun WorkScreen(onAddShift: () -> Unit, onEditShift: (Long) -> Unit) {
    val graph = LocalAppGraph.current
    val vm = driftViewModel { WorkViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()

    var showNewJob by remember { mutableStateOf(false) }
    var editingJob by remember { mutableStateOf<JobEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.work_title), onBack = null,
                endContent = { Chip(stringResource(R.string.work_add_job_short), selected = true, onClick = { showNewJob = true }) },
            )
        }

        // Hero: earned this month
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.work_earned_month), style = DriftType.Label13, color = DriftColors.TextSecondary)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        Fmt.moneyAgorot(state.earnedMonthAgorot), style = DriftType.Hero, color = DriftColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline(),
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
        item {
            StatTriplet(
                listOf(
                    Stat(stringResource(R.string.work_this_week), stringResource(R.string.amount_with_shekel, Fmt.moneyAgorot(state.earnedWeekAgorot)), DriftColors.Lime),
                    Stat(stringResource(R.string.work_hours_month), ShiftCalculator.formatHoursShort(state.hoursMonth)),
                    Stat(stringResource(R.string.work_recent_shifts), state.shiftsMonth.toString()),
                ),
            )
        }

        // Calendar
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MonthCalendar(
                        month = state.calendarMonth,
                        workedDays = state.workedDays,
                        plannedDays = state.plannedDays,
                        today = state.today,
                        onDayClick = { vm.togglePlanned(it) },
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
        item {
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
                                JobCard(
                                    name = c.job.name,
                                    amountText = stringResource(R.string.amount_with_shekel, Fmt.moneyAgorot(c.monthAmountAgorot)),
                                    metaText = if (c.job.isDaily) {
                                        stringResource(R.string.work_job_card_meta_daily, c.monthDays, Fmt.moneyAgorot(c.weekAmountAgorot))
                                    } else {
                                        stringResource(R.string.work_job_card_meta, ShiftCalculator.formatHours(c.monthHours), Fmt.moneyAgorot(c.weekAmountAgorot))
                                    },
                                    percent = c.weekPercent,
                                    barFraction = c.weekFraction.toFloat(),
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
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Recent shifts
        item { SectionHeader(stringResource(R.string.work_recent_shifts), stringResource(R.string.add_menu_shift), onAction = onAddShift, modifier = Modifier.padding(bottom = 0.dp)) }
        if (state.recentShifts.isEmpty() && !state.loading) item { EmptyNote(stringResource(R.string.work_no_shifts)) }
        items(state.recentShifts, key = { it.id }) { shift ->
            ShiftRow(shift, state.jobsById[shift.jobId], onClick = { onEditShift(shift.id) })
        }

        // Manage jobs
        item {
            Column {
                SectionHeader(stringResource(R.string.work_manage_jobs), stringResource(R.string.work_add_job), onAction = { showNewJob = true })
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        if (state.jobs.isEmpty()) EmptyNote(stringResource(R.string.work_no_jobs))
                        state.jobs.forEachIndexed { i, job ->
                            JobRow(
                                job = job,
                                onEdit = { editingJob = job },
                                onToggleActive = { vm.setJobActive(job.id, !job.isActive) },
                            )
                            if (i < state.jobs.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }

    if (showNewJob) {
        JobDialog(title = stringResource(R.string.work_add_job), job = null, onDismiss = { showNewJob = false }) { name, payType, rate, mult, travel ->
            vm.addJob(name, payType, rate, mult, travel); showNewJob = false
        }
    }
    editingJob?.let { job ->
        JobDialog(title = stringResource(R.string.edit), job = job, onDismiss = { editingJob = null }) { name, payType, rate, mult, travel ->
            vm.updateJob(job, name, payType, rate, mult, travel); editingJob = null
        }
    }
}

/**
 * "job · date · hours · שבת" row with the shift's income (wage + travel), reusing the transaction
 * row shape. A daily job shows "יום" instead of hours; a shift with travel gets a "כולל נסיעות" tag.
 */
@Composable
fun ShiftRow(shift: ShiftEntity, job: JobEntity?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = LocalDate.ofEpochDay(shift.date)
    val daily = job?.isDaily == true
    val meta = buildList {
        add(Fmt.shortDate(date))
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
        title = job?.name ?: stringResource(R.string.work_no_jobs),
        meta = meta,
        amountText = stringResource(R.string.amount_with_shekel, Fmt.moneyExact(shift.totalAgorot)),
        dotColor = job?.let { Color(it.color) } ?: DriftColors.TextDim,
        sharedTag = if (tags.isEmpty()) null else tags.joinToString(" · "),
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun JobRow(job: JobEntity, onEdit: () -> Unit, onToggleActive: () -> Unit) {
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
private fun JobDialog(title: String, job: JobEntity?, onDismiss: () -> Unit, onSave: (String, PayType, Double, Double, Double) -> Unit) {
    var name by remember { mutableStateOf(job?.name ?: "") }
    var payType by remember { mutableStateOf(job?.pay ?: PayType.HOURLY) }
    // One field per pay type so switching chips does not lose what was typed.
    var hourlyRate by remember { mutableStateOf(job?.takeIf { it.hourlyRateAgorot > 0L }?.let { Fmt.moneyExact(it.hourlyRateAgorot).replace(",", "") } ?: "") }
    var dailyRate by remember { mutableStateOf(job?.takeIf { it.dailyRateAgorot > 0L }?.let { Fmt.moneyExact(it.dailyRateAgorot).replace(",", "") } ?: "") }
    var multiplier by remember { mutableStateOf(job?.let { ShiftCalculator.formatHours(it.shabbatMultiplier) } ?: "1.5") }
    // Travel reimbursement per shift: optional — an empty field means none (0).
    var travel by remember { mutableStateOf(job?.takeIf { it.travelPerShiftAgorot > 0L }?.let { Fmt.moneyExact(it.travelPerShiftAgorot).replace(",", "") } ?: "") }
    // Explicit choice: a job either pays travel (amount required) or is marked as paying none.
    var hasTravel by remember { mutableStateOf(job?.let { it.travelPerShiftAgorot > 0L } ?: false) }
    val rate = when (payType) {
        PayType.HOURLY -> hourlyRate
        PayType.DAILY -> dailyRate
    }
    val rateValue = rate.replace(",", "").toDoubleOrNull()
    val multValue = multiplier.replace(",", ".").toDoubleOrNull()
    val travelValue = if (!hasTravel) 0.0 else travel.replace(",", "").toDoubleOrNull()
    val rateOk = rateValue != null && rateValue > 0.0
    val multOk = multValue != null && multValue >= 1.0
    val travelOk = !hasTravel || (travelValue != null && travelValue > 0.0)
    val valid = name.isNotBlank() && rateOk && multOk && travelOk
    DriftDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.save),
        onConfirm = { if (valid) onSave(name.trim(), payType, rateValue!!, multValue!!, travelValue!!) },
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = valid,
    ) {
        // Five inputs: scroll inside the dialog so the last ones stay reachable above the keyboard.
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DriftTextField(name, { name = it }, stringResource(R.string.work_job_name))
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
        }
    }
}
