package com.tamir.drift.ui.screens.work

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.ShiftEntity
import com.tamir.drift.data.db.isDaily
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.currentRate
import com.tamir.drift.data.repo.storedRate
import com.tamir.drift.data.repo.toJobSummaries
import com.tamir.drift.data.repo.toShiftSummaries
import com.tamir.drift.data.repo.toSummary
import com.tamir.drift.domain.money.AmountInput
import com.tamir.drift.domain.work.JobPicker
import com.tamir.drift.domain.work.PayType
import com.tamir.drift.domain.work.QuickShift
import com.tamir.drift.domain.work.ShiftCalculator
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.announceShiftSaved
import com.tamir.drift.ui.components.Chip
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.DriftSwitch
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.FocusOnOpen
import com.tamir.drift.ui.components.FormScreen
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.GlassIconButton
import com.tamir.drift.ui.components.PrimaryButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SwitchRow
import com.tamir.drift.ui.components.UnsavedChangesDialog
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.rememberFeedbackActions
import com.tamir.drift.ui.components.toFieldText
import com.tamir.drift.ui.deleteShiftWithUndo
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private const val MODE_TIMES = "times"
private const val MODE_HOURS = "hours"

/** How long the default job and its template get to settle before the first empty field is focused. */
private const val AUTOFOCUS_DELAY_MILLIS = 300L

/**
 * PRD "רישום משמרת": job chips, date (text entry, default today), start/end time or a number of
 * hours, "תעריף שבת" toggle, the computed amount shown large and editable (→ manualAmount).
 * For a daily-paid job the hours inputs are hidden: the amount is the daily rate (× the Shabbat
 * multiplier) and the shift is stored with hours = 0.
 * A job with a travel reimbursement adds a "החזר נסיעות · X ₪" switch (a tap on it edits the
 * amount for this shift). The big number is then the day total (wage + travel) with a
 * "שכר A ₪ + נסיעות B ₪" line; the wage stays editable on its own row. Travel is a flat amount:
 * the Shabbat multiplier and a manual wage do not change it.
 * With [shiftId] the same screen edits an existing shift (and can delete it, from the header);
 * its stored travel is kept as it was saved, and its pay keeps the rate stored on the shift as
 * long as the job and the hours are not changed (a new job or new hours take the job's current rate).
 *
 * One-tap entry: a new shift starts with the job most often worked on that weekday (else the last
 * one used) and with that job's last shift copied in — hours or start/end, the entry mode and the
 * travel switch — so a shift like the last one is saved without typing. Picking another job chip
 * copies that job's last shift instead. Only when there is nothing to copy is the first empty
 * field focused with the keyboard up. Times may be typed as "1730" / "930" / "9" / "17.30" and are
 * shown as HH:mm once the field is left.
 *
 * "שמירה" is pinned under the scrolling form; saving shows "נשמרה משמרת · X ₪" with an undo, and
 * deleting happens at once with an undo. Leaving with edits asks "לצאת בלי לשמור?".
 * [initialEpochDay] opens a new shift on that day (a tap on a day of the work calendar).
 */
@Composable
fun AddShiftScreen(shiftId: Long?, onDone: () -> Unit, initialEpochDay: Long? = null) {
    val graph = LocalAppGraph.current
    val feedback = rememberFeedbackActions()
    val zone = ZoneId.systemDefault()
    val keyboard = LocalSoftwareKeyboardController.current
    val jobs by graph.work.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    // null until the stored shifts arrived, so "no earlier shift" is told apart from "not loaded yet".
    val loadedShifts by graph.work.shifts.collectAsStateWithLifecycle(initialValue = null)
    val allShifts: List<ShiftEntity> = loadedShifts.orEmpty()
    val activeJobs = jobs.filter { it.isActive }

    var existing by remember { mutableStateOf<ShiftEntity?>(null) }
    var loaded by remember { mutableStateOf(shiftId == null) }
    var notFound by remember { mutableStateOf(false) }

    var jobId by remember { mutableStateOf<Long?>(null) }
    // Until the user picks a job chip himself, the default job follows the chosen date.
    var jobTouched by remember { mutableStateOf(false) }
    val firstDate: LocalDate = remember { initialEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(zone) }
    var date by remember { mutableStateOf(firstDate.toFieldText()) }
    var mode by remember { mutableStateOf(MODE_HOURS) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var hoursText by remember { mutableStateOf("") }
    // Hours / times / entry mode / travel were set by the user: the job's last shift no longer overwrites them.
    var entryTouched by remember { mutableStateOf(false) }
    var shabbat by remember { mutableStateOf(false) }
    var shabbatTouched by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // Travel reimbursement: on by default for a new shift. A custom amount (set from the dialog,
    // or the stored one of an edited shift) replaces the job's amount; null = use the job's.
    var travelOn by remember { mutableStateOf(true) }
    var travelCustom by remember { mutableStateOf<Long?>(null) }
    var editTravel by remember { mutableStateOf(false) }
    var travelDraft by remember { mutableStateOf("") }
    // Anything the user changed himself (a prefilled form that was not touched is not "edited").
    var dirty by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    // Set once the form is being saved or deleted, so a second tap cannot repeat it.
    var closing by remember { mutableStateOf(false) }
    val hoursFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val travelFocus = remember { FocusRequester() }

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
                if (s.manualAmount) amountText = Fmt.fieldAmount(s.amountAgorot)
                // The stored travel is kept as saved: on with its own amount, or off when the shift had none.
                travelOn = s.travelAgorot > 0L
                travelCustom = s.travelAgorot.takeIf { it > 0L }
                note = s.note ?: ""
            }
            loaded = true
        }
    }
    val dateValue = parseDate(date)
    // Default job of a new shift: the one most often worked on this weekday, else the last used.
    LaunchedEffect(jobs, loadedShifts, dateValue) {
        if (shiftId == null && !jobTouched) {
            val day = dateValue ?: LocalDate.now(zone)
            JobPicker.defaultJobId(day, jobs.toJobSummaries(), allShifts.toShiftSummaries())?.let { jobId = it }
        }
    }
    // A new shift copies the chosen job's last shift (hours or start/end, entry mode, travel)
    // until the user types those himself; picking another job chip copies that job's instead.
    LaunchedEffect(jobId, jobs, loadedShifts, entryTouched) {
        if (shiftId == null && !entryTouched) {
            val target = jobs.firstOrNull { it.id == jobId }
            val template = target?.let { QuickShift.template(it.toSummary(), allShifts.toShiftSummaries()) }
            if (target != null && template != null) {
                val from = template.startMinute
                val to = template.endMinute
                if (from != null && to != null) {
                    mode = MODE_TIMES
                    start = ShiftCalculator.formatClock(from)
                    end = ShiftCalculator.formatClock(to)
                } else if (template.hours > 0.0) {
                    mode = MODE_HOURS
                    hoursText = ShiftCalculator.formatHours(template.hours)
                }
                travelOn = template.travelOn
                travelCustom = if (template.travelOn && target.travelPerShiftAgorot <= 0L) template.lastTravelAgorot else null
            }
        }
    }
    // Saturday → Shabbat rate on by default until the user touches the switch.
    LaunchedEffect(dateValue) {
        if (!shabbatTouched) shabbat = dateValue?.dayOfWeek == DayOfWeek.SATURDAY
    }
    // Nothing to copy from → the first empty numeric field gets the focus and the keyboard.
    val readyForFocus = shiftId == null && loadedShifts != null && jobs.isNotEmpty()
    LaunchedEffect(readyForFocus) {
        if (readyForFocus) {
            delay(AUTOFOCUS_DELAY_MILLIS)
            val current = jobs.firstOrNull { it.id == jobId }
            if (current != null && !current.isDaily) {
                if (mode == MODE_TIMES && start.isBlank()) {
                    runCatching { startFocus.requestFocus() }
                    keyboard?.show()
                } else if (mode == MODE_HOURS && hoursText.isBlank()) {
                    runCatching { hoursFocus.requestFocus() }
                    keyboard?.show()
                }
            }
        }
    }

    val job = jobs.firstOrNull { it.id == jobId }
    val daily = job?.isDaily == true
    val startMin = ShiftCalculator.parseClock(start)
    val endMin = ShiftCalculator.parseClock(end)
    val hours: Double? = when {
        daily -> 0.0
        mode == MODE_TIMES -> if (startMin != null && endMin != null) ShiftCalculator.hoursBetween(startMin, endMin) else null
        else -> AmountInput.parse(hoursText)?.takeIf { it > 0.0 }
    }
    val payType = if (daily) PayType.DAILY else PayType.HOURLY
    // The rate this shift is paid at — exactly what the repository will use on save: the job's
    // current rate, or, for an edited shift whose job and hours are unchanged, the rate stored on it.
    val currentRate = job.currentRate()
    val edited = existing
    val storedRate = edited?.storedRate()
    val payRate = if (edited == null) currentRate else ShiftCalculator.rateForEdit(
        current = currentRate,
        stored = storedRate,
        sameJob = jobId == edited.jobId,
        sameHours = hours != null && ShiftCalculator.sameHours(hours, edited.hours),
    )
    // The per-hour or per-day rate (by pay type) and the Shabbat multiplier shown in the formula.
    val rate = payRate.rateAgorot
    val dailyRate = payRate.rateAgorot
    val multiplier = payRate.multiplier
    val keptStoredRate = edited != null && storedRate != null && payRate == storedRate && storedRate != currentRate
    val computedAgorot = ShiftCalculator.amount(payType, hours ?: 0.0, payRate, shabbat)
    val manualValue = AmountInput.parse(amountText)
    val manualAgorot = if (manual) manualValue?.let { Money.toAgorot(it) } else null
    val shownAmount = if (manual) amountText else Fmt.fieldAmount(computedAgorot)
    // The wage alone (what is stored as the shift amount), and the travel added on top of it.
    val wageAgorot = if (manual) manualAgorot ?: 0L else computedAgorot
    val jobTravel = job?.travelPerShiftAgorot ?: 0L
    val travelAmount = travelCustom ?: jobTravel
    val showTravel = jobTravel > 0L || travelCustom != null
    val travelEnabled = showTravel && travelOn
    val appliedTravel = ShiftCalculator.travel(travelAmount, travelEnabled)
    val totalAgorot = wageAgorot + appliedTravel
    val amountOk = if (manual) manualValue != null && manualValue >= 0.0 else computedAgorot >= 0L
    val timesOk = daily || mode != MODE_TIMES || (startMin != null && endMin != null)
    val hoursOk = daily || (hours != null && hours > 0.0)
    val valid = loaded && job != null && dateValue != null && timesOk && hoursOk && amountOk

    // Saves and leaves; the write and its "נשמרה משמרת · X ₪" message (with undo) finish on the app scope.
    fun save() {
        if (!valid || closing) return
        val j = job ?: return
        val d = dateValue ?: return
        val h = hours ?: return
        val timed = !daily && mode == MODE_TIMES
        val st: Int? = if (timed) startMin else null
        val en: Int? = if (timed) endMin else null
        val cur = existing
        val isShabbat = shabbat
        val wageOverride: Long? = manualAgorot
        val noteText = note
        val travel = appliedTravel
        closing = true
        feedback.saved()
        graph.scope.launch {
            // The wage (manual or computed) and the travel are stored apart.
            val savedId: Long = if (cur == null) {
                graph.work.addShift(j.id, d, st, en, h, isShabbat, wageOverride, noteText, travel)
            } else {
                graph.work.updateShift(cur, j.id, d, st, en, h, isShabbat, wageOverride, noteText, travel)
                cur.id
            }
            graph.announceShiftSaved(savedId, cur)
        }
        onDone()
    }

    val leave: () -> Unit = { if (dirty && !closing) { askLeave = true } else { onDone() } }
    BackHandler(enabled = dirty && !askLeave && !closing) { askLeave = true }

    FormScreen(
        bottom = {
            if (!notFound) PrimaryButton(stringResource(R.string.save), enabled = valid && !closing, onClick = { save() })
        },
    ) {
        ScreenHeader(
            stringResource(if (shiftId == null) R.string.shift_title else R.string.shift_edit_title),
            onBack = leave, backDescription = stringResource(R.string.nav_back),
            endContent = {
                val shown = existing
                if (shown != null) {
                    // Deleting happens at once; the root message offers the undo.
                    GlassIconButton(DriftIcons.Trash, stringResource(R.string.delete), {
                        if (!closing) {
                            closing = true
                            feedback.deleted()
                            graph.deleteShiftWithUndo(shown)
                            onDone()
                        }
                    }, tint = DriftColors.Orange)
                }
            },
        )

        if (notFound) {
            EmptyNote(stringResource(R.string.shift_not_found))
        } else {
            // Job chips
            Text(stringResource(R.string.shift_job), style = DriftType.Label12, color = DriftColors.TextSecondary)
            if (activeJobs.isEmpty() && existing == null) {
                EmptyNote(stringResource(R.string.shift_no_jobs))
            } else {
                val options = (activeJobs + listOfNotNull(jobs.firstOrNull { it.id == existing?.jobId && !it.isActive })).map { it.id.toString() to it.name }
                ChoiceRow(options = options, selected = jobId?.toString(), onSelect = {
                    val picked = it.toLongOrNull()
                    if (picked != jobId) {
                        // Another job brings its own travel amount; a custom amount belonged to the previous one.
                        travelCustom = null
                        // …and, for a new shift, its own last shift is copied in again.
                        if (shiftId == null) entryTouched = false
                    }
                    jobId = picked
                    jobTouched = true
                    dirty = true
                })
            }

            DateField(date, { date = it; dirty = true }, stringResource(R.string.shift_date), errorText = stringResource(R.string.date_invalid))

            // Daily job: no hours at all — one line with the day rate. Hourly job: times or hours.
            if (daily) {
                Text(
                    stringResource(R.string.shift_daily_label, Fmt.moneyExact(dailyRate)),
                    style = DriftType.Label13, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.shift_mode_hours), mode == MODE_HOURS, onClick = { mode = MODE_HOURS; entryTouched = true; dirty = true })
                    Chip(stringResource(R.string.shift_mode_times), mode == MODE_TIMES, onClick = { mode = MODE_TIMES; entryTouched = true; dirty = true })
                }
            }
            if (!daily && mode == MODE_TIMES) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DriftTextField(
                        start, { start = it; entryTouched = true; dirty = true }, stringResource(R.string.shift_start), modifier = Modifier.weight(1f),
                        placeholder = "09:00", keyboardType = KeyboardType.Number,
                        isError = start.isNotBlank() && startMin == null,
                        supporting = if (start.isNotBlank() && startMin == null) stringResource(R.string.shift_invalid_time) else null,
                        imeAction = ImeAction.Next,
                        focusRequester = startFocus,
                        // "1730" / "930" / "9" / "17.30" → "17:30" once the field is left.
                        onFocusLost = { ShiftCalculator.normalizeClock(start)?.let { start = it } },
                    )
                    DriftTextField(
                        end, { end = it; entryTouched = true; dirty = true }, stringResource(R.string.shift_end), modifier = Modifier.weight(1f),
                        placeholder = "17:00", keyboardType = KeyboardType.Number,
                        isError = end.isNotBlank() && endMin == null,
                        supporting = if (end.isNotBlank() && endMin == null) stringResource(R.string.shift_invalid_time) else null,
                        imeAction = ImeAction.Done, onImeAction = { save() },
                        onFocusLost = { ShiftCalculator.normalizeClock(end)?.let { end = it } },
                    )
                }
                if (hours != null) {
                    Text(
                        stringResource(R.string.work_hours_value, ShiftCalculator.formatHours(hours)),
                        style = DriftType.Label12, color = DriftColors.TextSecondary,
                    )
                }
            } else if (!daily) {
                DriftTextField(
                    hoursText, { hoursText = it; entryTouched = true; dirty = true }, stringResource(R.string.shift_hours), keyboardType = KeyboardType.Decimal,
                    isError = hoursText.isNotBlank() && !hoursOk,
                    supporting = if (hoursText.isNotBlank() && !hoursOk) stringResource(R.string.shift_invalid_hours) else null,
                    imeAction = ImeAction.Done, onImeAction = { save() },
                    focusRequester = hoursFocus,
                )
            }

            SwitchRow(stringResource(R.string.shift_shabbat_rate), shabbat, { shabbat = it; shabbatTouched = true; dirty = true })

            // Travel reimbursement: only for a job that pays it (or a shift that already carries one).
            if (showTravel) {
                TravelRow(
                    label = stringResource(R.string.shift_travel_switch, Fmt.moneyExact(travelAmount)),
                    hint = stringResource(R.string.shift_travel_hint),
                    checked = travelOn,
                    onCheckedChange = { travelOn = it; entryTouched = true; dirty = true },
                    onEditAmount = {
                        travelDraft = Fmt.fieldAmount(travelAmount)
                        editTravel = true
                    },
                )
            }

            // Amount: big, editable. Typing switches to a manual amount; a link returns to the computed one.
            // With travel the big number is the day total (wage + travel) and the wage is edited on its own row.
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (appliedTravel > 0L) R.string.shift_total_day else R.string.shift_amount),
                            style = DriftType.Label13, color = DriftColors.TextSecondary,
                        )
                        if (manual) {
                            Text(stringResource(R.string.shift_amount_manual), style = DriftType.Percent11, color = DriftColors.Orange)
                        }
                    }
                    if (appliedTravel > 0L) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                Fmt.moneyExact(totalAgorot), style = DriftType.Big, color = DriftColors.Text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Text(stringResource(R.string.shekel), style = DriftType.HeroCurrency, color = DriftColors.TextSecondary)
                        }
                        Text(
                            stringResource(R.string.shift_breakdown, Fmt.moneyExact(wageAgorot), Fmt.moneyExact(appliedTravel)),
                            style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.shift_wage), style = DriftType.Label13, color = DriftColors.TextSecondary)
                            WageField(
                                value = shownAmount, manual = manual, style = DriftType.Value20,
                                onValueChange = { manual = true; amountText = it; dirty = true },
                                modifier = Modifier.weight(1f),
                            )
                            Text(stringResource(R.string.shekel), style = DriftType.Label13, color = DriftColors.TextSecondary)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WageField(
                                value = shownAmount, manual = manual, style = DriftType.Big,
                                onValueChange = { manual = true; amountText = it; dirty = true },
                                modifier = Modifier.weight(1f),
                            )
                            Text(stringResource(R.string.shekel), style = DriftType.HeroCurrency, color = DriftColors.TextSecondary)
                        }
                    }
                    val formula = when {
                        daily && shabbat -> stringResource(R.string.shift_amount_formula_daily_shabbat, Fmt.moneyExact(dailyRate), ShiftCalculator.formatHours(multiplier))
                        daily -> stringResource(R.string.shift_amount_formula_daily, Fmt.moneyExact(dailyRate))
                        shabbat -> stringResource(R.string.shift_amount_formula_shabbat, ShiftCalculator.formatHours(hours ?: 0.0), Fmt.moneyExact(rate), ShiftCalculator.formatHours(multiplier))
                        else -> stringResource(R.string.shift_amount_formula, ShiftCalculator.formatHours(hours ?: 0.0), Fmt.moneyExact(rate))
                    }
                    Text(formula, style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (keptStoredRate && !manual) {
                        Text(stringResource(R.string.shift_rate_kept), style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (manual) {
                        Text(
                            stringResource(R.string.shift_amount_reset), style = DriftType.Link12, color = DriftColors.Lime,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { manual = false; amountText = ""; dirty = true }.padding(vertical = 4.dp),
                        )
                    }
                }
            }

            DriftTextField(
                note, { note = it; dirty = true }, stringResource(R.string.shift_note), singleLine = false, minLines = 2,
                imeAction = ImeAction.Done, onImeAction = { save() },
            )
        }
    }

    if (askLeave) {
        UnsavedChangesDialog(
            canSave = valid,
            onSave = { askLeave = false; save() },
            onLeave = { askLeave = false; onDone() },
            onCancel = { askLeave = false },
        )
    }

    // Travel amount for this shift only (the job keeps its own amount).
    if (editTravel) {
        val draftValue = AmountInput.parse(travelDraft)
        val draftOk = draftValue != null && draftValue >= 0.0
        val confirmTravel: () -> Unit = {
            if (draftValue != null && draftValue >= 0.0) {
                travelCustom = Money.toAgorot(draftValue)
                if (draftValue > 0.0) travelOn = true
                entryTouched = true
                dirty = true
                editTravel = false
            }
        }
        DriftDialog(
            title = stringResource(R.string.shift_travel_edit_title),
            onDismiss = { editTravel = false },
            confirmText = stringResource(R.string.save),
            onConfirm = confirmTravel,
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = draftOk,
        ) {
            DriftTextField(
                travelDraft, { travelDraft = it }, stringResource(R.string.shift_travel_amount), keyboardType = KeyboardType.Decimal,
                isError = travelDraft.isNotBlank() && !draftOk,
                supporting = if (travelDraft.isNotBlank() && !draftOk) stringResource(R.string.work_job_invalid_travel) else null,
                imeAction = ImeAction.Done, onImeAction = confirmTravel,
                focusRequester = travelFocus, selectAllOnFocus = true,
            )
            FocusOnOpen(travelFocus)
        }
    }
}

/** The editable wage: computed until the user types (→ manual, shown in orange). */
@Composable
private fun WageField(value: String, manual: Boolean, style: TextStyle, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style.copy(color = if (manual) DriftColors.Orange else DriftColors.Text),
        cursorBrush = SolidColor(DriftColors.Lime),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** "החזר נסיעות · X ₪" with a switch; a tap on the text opens the amount editor. */
@Composable
private fun TravelRow(label: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onEditAmount: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f).padding(end = 12.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onEditAmount)) {
            Text(label, style = DriftType.Body, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hint, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DriftSwitch(checked, onCheckedChange)
    }
}
