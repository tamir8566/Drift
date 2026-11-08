package com.tamir.drift.ui.screens.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.ShiftEntity
import com.tamir.drift.data.repo.Money
import com.tamir.drift.domain.work.ShiftCalculator
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.Chip
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.PrimaryButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SecondaryButton
import com.tamir.drift.ui.components.SwitchRow
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.toFieldText
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private const val MODE_TIMES = "times"
private const val MODE_HOURS = "hours"

/**
 * PRD "רישום משמרת": job chips, date (text entry, default today), start/end time or a number of
 * hours, "תעריף שבת" toggle, the computed amount shown large and editable (→ manualAmount).
 * With [shiftId] the same screen edits an existing shift (and can delete it).
 */
@Composable
fun AddShiftScreen(shiftId: Long?, onDone: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val jobs by graph.work.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeJobs = jobs.filter { it.isActive }

    var existing by remember { mutableStateOf<ShiftEntity?>(null) }
    var loaded by remember { mutableStateOf(shiftId == null) }
    var notFound by remember { mutableStateOf(false) }

    var jobId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(LocalDate.now(zone).toFieldText()) }
    var mode by remember { mutableStateOf(MODE_HOURS) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var hoursText by remember { mutableStateOf("") }
    var shabbat by remember { mutableStateOf(false) }
    var shabbatTouched by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    // Edit mode: load the shift once.
    LaunchedEffect(shiftId) {
        if (shiftId != null) {
            val s = graph.work.getShift(shiftId)
            if (s == null) {
                notFound = true
            } else {
                existing = s
                jobId = s.jobId
                date = LocalDate.ofEpochDay(s.date).toFieldText()
                if (s.startTime != null && s.endTime != null) {
                    mode = MODE_TIMES
                    start = ShiftCalculator.formatClock(s.startTime)
                    end = ShiftCalculator.formatClock(s.endTime)
                } else {
                    mode = MODE_HOURS
                    hoursText = ShiftCalculator.formatHours(s.hours)
                }
                shabbat = s.isShabbatRate
                shabbatTouched = true
                manual = s.manualAmount
                if (s.manualAmount) amountText = Fmt.moneyExact(s.amountAgorot).replace(",", "")
                note = s.note ?: ""
            }
            loaded = true
        }
    }
    // Default job: the first active one.
    LaunchedEffect(activeJobs) {
        if (jobId == null) jobId = activeJobs.firstOrNull()?.id
    }

    val dateValue = parseDate(date)
    // Saturday → Shabbat rate on by default until the user touches the switch.
    LaunchedEffect(dateValue) {
        if (!shabbatTouched) shabbat = dateValue?.dayOfWeek == DayOfWeek.SATURDAY
    }

    val job = jobs.firstOrNull { it.id == jobId }
    val startMin = ShiftCalculator.parseClock(start)
    val endMin = ShiftCalculator.parseClock(end)
    val hours: Double? = when (mode) {
        MODE_TIMES -> if (startMin != null && endMin != null) ShiftCalculator.hoursBetween(startMin, endMin) else null
        else -> hoursText.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }
    }
    val rate = job?.hourlyRateAgorot ?: 0L
    val multiplier = job?.shabbatMultiplier ?: 1.5
    val computedAgorot = ShiftCalculator.amount(hours ?: 0.0, rate, shabbat, multiplier)
    val manualValue = amountText.replace(",", "").toDoubleOrNull()
    val shownAmount = if (manual) amountText else Fmt.moneyExact(computedAgorot).replace(",", "")
    val amountOk = if (manual) manualValue != null && manualValue >= 0.0 else computedAgorot >= 0L
    val timesOk = mode != MODE_TIMES || (startMin != null && endMin != null)
    val hoursOk = hours != null && hours > 0.0
    val valid = loaded && job != null && dateValue != null && timesOk && hoursOk && amountOk

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            stringResource(if (shiftId == null) R.string.shift_title else R.string.shift_edit_title),
            onBack = onDone, backDescription = stringResource(R.string.nav_back),
        )

        if (notFound) {
            EmptyNote(stringResource(R.string.shift_not_found))
            return@Column
        }

        // Job chips
        Text(stringResource(R.string.shift_job), style = DriftType.Label12, color = DriftColors.TextSecondary)
        if (activeJobs.isEmpty() && existing == null) {
            EmptyNote(stringResource(R.string.shift_no_jobs))
        } else {
            val options = (activeJobs + listOfNotNull(jobs.firstOrNull { it.id == existing?.jobId && !it.isActive })).map { it.id.toString() to it.name }
            ChoiceRow(options = options, selected = jobId?.toString(), onSelect = { jobId = it.toLongOrNull() })
        }

        DateField(date, { date = it }, stringResource(R.string.shift_date), errorText = stringResource(R.string.date_invalid))

        // Times or hours
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.shift_mode_hours), mode == MODE_HOURS, onClick = { mode = MODE_HOURS })
            Chip(stringResource(R.string.shift_mode_times), mode == MODE_TIMES, onClick = { mode = MODE_TIMES })
        }
        if (mode == MODE_TIMES) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DriftTextField(
                    start, { start = it }, stringResource(R.string.shift_start), modifier = Modifier.weight(1f),
                    placeholder = "09:00", keyboardType = KeyboardType.Number,
                    isError = start.isNotBlank() && startMin == null,
                    supporting = if (start.isNotBlank() && startMin == null) stringResource(R.string.shift_invalid_time) else null,
                )
                DriftTextField(
                    end, { end = it }, stringResource(R.string.shift_end), modifier = Modifier.weight(1f),
                    placeholder = "17:00", keyboardType = KeyboardType.Number,
                    isError = end.isNotBlank() && endMin == null,
                    supporting = if (end.isNotBlank() && endMin == null) stringResource(R.string.shift_invalid_time) else null,
                )
            }
            if (hours != null) {
                Text(
                    stringResource(R.string.work_hours_value, ShiftCalculator.formatHours(hours)),
                    style = DriftType.Label12, color = DriftColors.TextSecondary,
                )
            }
        } else {
            DriftTextField(
                hoursText, { hoursText = it }, stringResource(R.string.shift_hours), keyboardType = KeyboardType.Decimal,
                isError = hoursText.isNotBlank() && !hoursOk,
                supporting = if (hoursText.isNotBlank() && !hoursOk) stringResource(R.string.shift_invalid_hours) else null,
            )
        }

        SwitchRow(stringResource(R.string.shift_shabbat_rate), shabbat, { shabbat = it; shabbatTouched = true })

        // Amount: big, editable. Typing switches to a manual amount; a link returns to the computed one.
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.shift_amount), style = DriftType.Label13, color = DriftColors.TextSecondary)
                    if (manual) {
                        Text(stringResource(R.string.shift_amount_manual), style = DriftType.Percent11, color = DriftColors.Orange)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = shownAmount,
                        onValueChange = { manual = true; amountText = it },
                        singleLine = true,
                        textStyle = DriftType.Big.copy(color = if (manual) DriftColors.Orange else DriftColors.Text),
                        cursorBrush = SolidColor(DriftColors.Lime),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.shekel), style = DriftType.HeroCurrency, color = DriftColors.TextSecondary)
                }
                val formula = if (shabbat) {
                    stringResource(R.string.shift_amount_formula_shabbat, ShiftCalculator.formatHours(hours ?: 0.0), Fmt.moneyExact(rate), ShiftCalculator.formatHours(multiplier))
                } else {
                    stringResource(R.string.shift_amount_formula, ShiftCalculator.formatHours(hours ?: 0.0), Fmt.moneyExact(rate))
                }
                Text(formula, style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (manual) {
                    Text(
                        stringResource(R.string.shift_amount_reset), style = DriftType.Link12, color = DriftColors.Lime,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { manual = false; amountText = "" }.padding(vertical = 4.dp),
                    )
                }
            }
        }

        DriftTextField(note, { note = it }, stringResource(R.string.shift_note), singleLine = false, minLines = 2)

        Spacer(Modifier.height(6.dp))
        PrimaryButton(stringResource(R.string.save), enabled = valid, onClick = {
            val j = job ?: return@PrimaryButton
            val d = dateValue ?: return@PrimaryButton
            val h = hours ?: return@PrimaryButton
            val manualAgorot = if (manual) manualValue?.let { Money.toAgorot(it) } else null
            val (st, en) = if (mode == MODE_TIMES) startMin to endMin else null to null
            scope.launch {
                val cur = existing
                if (cur == null) graph.work.addShift(j.id, d, st, en, h, shabbat, manualAgorot, note)
                else graph.work.updateShift(cur, j.id, d, st, en, h, shabbat, manualAgorot, note)
                onDone()
            }
        })
        if (existing != null) {
            SecondaryButton(stringResource(R.string.delete), onClick = { confirmDelete = true }, color = DriftColors.Orange)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmDelete) {
        DriftDialog(
            title = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                val id = existing?.id ?: return@DriftDialog
                scope.launch { graph.work.deleteShift(id); onDone() }
            },
            dismissText = stringResource(R.string.cancel),
        ) { Text(stringResource(R.string.work_delete_shift_confirm), style = DriftType.Body, color = DriftColors.TextSecondary) }
    }
}
