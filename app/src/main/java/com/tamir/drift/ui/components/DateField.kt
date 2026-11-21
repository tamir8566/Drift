package com.tamir.drift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tamir.drift.R
import java.time.LocalDate
import java.time.YearMonth

/** Parses "d.M.yyyy" / "d/M/yyyy" / "d.M.yy"; null when invalid. */
fun parseDate(text: String): LocalDate? {
    val parts = text.trim().split('.', '/', '-').filter { it.isNotBlank() }
    if (parts.size != 3) return null
    val d = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    var y = parts[2].toIntOrNull() ?: return null
    if (y < 100) y += 2000
    return runCatching { LocalDate.of(y, m, d) }.getOrNull()
}

fun LocalDate.toFieldText(): String = "$dayOfMonth.$monthValue.$year"

/**
 * Date entry: plain text (day.month.year), and above it the one-tap ways to set it — the chips
 * "היום · אתמול · שלשום" and a small calendar button that opens the app's own month calendar.
 * Chosen over the Material date picker so that no English (system-locale) month names can appear.
 * [quickDays] hides the three chips for a date that lies in the future (a goal's target date).
 */
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    quickDays: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
) {
    val parsed = parseDate(value)
    val valid = value.isBlank() || parsed != null
    val today = LocalDate.now()
    var picking by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (quickDays) {
                    val yesterday = today.minusDays(1)
                    val dayBefore = today.minusDays(2)
                    Chip(stringResource(R.string.today), parsed == today, onClick = { onValueChange(today.toFieldText()) })
                    Chip(stringResource(R.string.yesterday), parsed == yesterday, onClick = { onValueChange(yesterday.toFieldText()) })
                    Chip(stringResource(R.string.day_before_yesterday), parsed == dayBefore, onClick = { onValueChange(dayBefore.toFieldText()) })
                }
            }
            GlassIconButton(DriftIcons.Calendar, stringResource(R.string.date_pick), { picking = true }, size = 34.dp)
        }
        DriftTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = "31.12.2027",
            keyboardType = KeyboardType.Number,
            isError = !valid,
            supporting = if (!valid) errorText else null,
            imeAction = imeAction,
        )
    }
    if (picking) {
        DatePickDialog(
            selected = parsed,
            today = today,
            onPick = { day ->
                onValueChange(day.toFieldText())
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** The app's month calendar in a dialog: a tap on a day picks it (the chosen day wears the ring). */
@Composable
private fun DatePickDialog(selected: LocalDate?, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var month: YearMonth by remember { mutableStateOf(YearMonth.from(selected ?: today)) }
    DriftDialog(
        title = stringResource(R.string.date_pick),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.cancel),
        onConfirm = onDismiss,
    ) {
        MonthCalendar(
            month = month,
            workedDays = emptySet(),
            plannedDays = if (selected != null) setOf(selected.toEpochDay()) else emptySet(),
            today = today,
            onDayClick = onPick,
            onPrevMonth = { month = month.minusMonths(1) },
            onNextMonth = { month = month.plusMonths(1) },
        )
    }
}
