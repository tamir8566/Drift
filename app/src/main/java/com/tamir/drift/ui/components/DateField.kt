package com.tamir.drift.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate

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
 * Plain text date entry (day.month.year). Chosen over the Material date picker so that no
 * English (system-locale) month names can appear in the UI.
 */
@Composable
fun DateField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, errorText: String? = null) {
    val valid = value.isBlank() || parseDate(value) != null
    DriftTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        placeholder = "31.12.2027",
        keyboardType = KeyboardType.Number,
        isError = !valid,
        supporting = if (!valid) errorText else null,
    )
}
