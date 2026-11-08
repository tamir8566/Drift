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
import com.tamir.drift.domain.work.ShiftCalculator
import com.tamir.drift.ui.LocalAppGraph
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
        item { ScreenHeader(stringResource(R.string.work_title), onBack = null) }

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
            }
        }

        // This week · hours · shifts
        item {
            StatTriplet(
                listOf(
                    Stat(stringResource(R.string.work_this_week), stringResource(R.string.amount_with_shekel, Fmt.moneyAgorot(state.earnedWeekAgorot)), DriftColors.Lime),
                    Stat(stringResource(R.string.work_hours_month), stringResource(R.string.work_hours_value, ShiftCalculator.formatHours(state.hoursMonth))),
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
                val rows = state.jobCards.take(4).chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c ->
                                JobCard(
                                    name = c.job.name,
                                    amountText = stringResource(R.string.amount_with_shekel, Fmt.moneyAgorot(c.monthAmountAgorot)),
                                    metaText = stringResource(R.string.work_job_card_meta, ShiftCalculator.formatHours(c.monthHours), Fmt.moneyAgorot(c.weekAmountAgorot)),
                                    percent = c.weekPercent,
                                    barFraction = c.weekFraction.toFloat(),
                                    color = Color(c.job.color),
                                    modifier = Modifier.weight(1f),
                                    onClick = { editingJob = c.job },
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
        JobDialog(title = stringResource(R.string.work_add_job), job = null, onDismiss = { showNewJob = false }) { name, rate, mult ->
            vm.addJob(name, rate, mult); showNewJob = false
        }
    }
    editingJob?.let { job ->
        JobDialog(title = stringResource(R.string.edit), job = job, onDismiss = { editingJob = null }) { name, rate, mult ->
            vm.updateJob(job, name, rate, mult); editingJob = null
        }
    }
}

/** "job · date · hours · שבת" row with the amount, reusing the transaction row shape. */
@Composable
fun ShiftRow(shift: ShiftEntity, job: JobEntity?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = LocalDate.ofEpochDay(shift.date)
    val meta = buildList {
        add(Fmt.shortDate(date))
        add(stringResource(R.string.work_hours_value, ShiftCalculator.formatHours(shift.hours)))
        if (shift.startTime != null && shift.endTime != null) {
            add("${ShiftCalculator.formatClock(shift.startTime)}–${ShiftCalculator.formatClock(shift.endTime)}")
        }
    }.joinToString(" · ")
    TransactionRow(
        title = job?.name ?: stringResource(R.string.work_no_jobs),
        meta = meta,
        amountText = stringResource(R.string.amount_with_shekel, Fmt.moneyExact(shift.amountAgorot)),
        dotColor = job?.let { Color(it.color) } ?: DriftColors.TextDim,
        sharedTag = if (shift.isShabbatRate) stringResource(R.string.work_shabbat_tag) else null,
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
                add(stringResource(R.string.work_job_rate, Fmt.moneyExact(job.hourlyRateAgorot)))
                add(stringResource(R.string.work_job_shabbat, ShiftCalculator.formatHours(job.shabbatMultiplier)))
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
private fun JobDialog(title: String, job: JobEntity?, onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf(job?.name ?: "") }
    var rate by remember { mutableStateOf(job?.let { Fmt.moneyExact(it.hourlyRateAgorot).replace(",", "") } ?: "") }
    var multiplier by remember { mutableStateOf(job?.let { ShiftCalculator.formatHours(it.shabbatMultiplier) } ?: "1.5") }
    val rateValue = rate.replace(",", "").toDoubleOrNull()
    val multValue = multiplier.replace(",", ".").toDoubleOrNull()
    val valid = name.isNotBlank() && rateValue != null && rateValue > 0 && multValue != null && multValue >= 1.0
    DriftDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.save),
        onConfirm = { if (valid) onSave(name.trim(), rateValue!!, multValue!!) },
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = valid,
    ) {
        DriftTextField(name, { name = it }, stringResource(R.string.work_job_name))
        DriftTextField(rate, { rate = it }, stringResource(R.string.work_job_hourly_rate), keyboardType = KeyboardType.Decimal)
        DriftTextField(multiplier, { multiplier = it }, stringResource(R.string.work_job_multiplier), keyboardType = KeyboardType.Decimal)
    }
}
