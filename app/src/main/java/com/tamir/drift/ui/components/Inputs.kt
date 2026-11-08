package com.tamir.drift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType

/** Outlined text field in the palette (lime focus, glass border idle). */
@Composable
fun DriftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = DriftType.Label12, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = placeholder?.let { { Text(it, style = DriftType.Body, color = DriftColors.TextDim) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        enabled = enabled,
        supportingText = supporting?.let { { Text(it, style = DriftType.Label11, color = if (isError) DriftColors.Orange else DriftColors.TextSecondary) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = DriftType.Body.copy(color = DriftColors.Text),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DriftColors.PlusBorder,
            unfocusedBorderColor = DriftColors.GlassBorder,
            errorBorderColor = DriftColors.Orange,
            focusedLabelColor = DriftColors.Lime,
            unfocusedLabelColor = DriftColors.TextSecondary,
            errorLabelColor = DriftColors.Orange,
            cursorColor = DriftColors.Lime,
            focusedTextColor = DriftColors.Text,
            unfocusedTextColor = DriftColors.Text,
            focusedContainerColor = DriftColors.GlassFill,
            unfocusedContainerColor = DriftColors.GlassFill,
            errorContainerColor = DriftColors.GlassFill,
            disabledContainerColor = DriftColors.GlassFill,
            disabledTextColor = DriftColors.TextSecondary,
            disabledBorderColor = DriftColors.GlassBorder,
            disabledLabelColor = DriftColors.TextDim,
        ),
    )
}

/** Lime switch. */
@Composable
fun DriftSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = DriftColors.Background,
            checkedTrackColor = DriftColors.Lime,
            checkedBorderColor = DriftColors.Lime,
            uncheckedThumbColor = DriftColors.TextSecondary,
            uncheckedTrackColor = DriftColors.GlassFill,
            uncheckedBorderColor = DriftColors.GlassBorder,
        ),
    )
}

/** Label + switch on one row. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = DriftType.Body, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DriftSwitch(checked, onCheckedChange)
    }
}

/** Primary (lime) full-width button. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(if (enabled) DriftColors.Lime else DriftColors.Lime.copy(alpha = 0.35f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DriftType.RowTitle, color = DriftColors.Background, maxLines = 1)
    }
}

/** Secondary (glass) full-width button. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = DriftColors.Text) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(DriftColors.GlassFill, shape)
            .border(1.dp, DriftColors.GlassBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DriftType.RowTitle, color = color, maxLines = 1)
    }
}

/** Themed alert dialog. */
@Composable
fun DriftDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DriftColors.Background,
        titleContentColor = DriftColors.Text,
        textContentColor = DriftColors.TextSecondary,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, style = DriftType.Title, color = DriftColors.Text) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmText, style = DriftType.RowTitle, color = if (confirmEnabled) DriftColors.Lime else DriftColors.TextDim)
            }
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = onDismiss) { Text(it, style = DriftType.RowTitle, color = DriftColors.TextSecondary) } }
        },
    )
}

/** Choice list used in place of dropdowns (no horizontal scrolling; chips wrap). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) -> Chip(label, selected == key, onClick = { onSelect(key) }) }
    }
}
