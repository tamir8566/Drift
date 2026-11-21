package com.tamir.drift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamir.drift.R
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.delay

/**
 * Outlined text field in the palette (lime focus, glass border idle).
 *
 * [imeAction] / [onImeAction]: the keyboard's action key. "Next" moves to the next field by
 * itself; pass [onImeAction] for "Done" on the last field of a form (it saves when valid).
 * [focusRequester] lets the caller focus the field when a form or a dialog opens ([FocusOnOpen]).
 * [selectAllOnFocus]: the whole value is selected when the field gets focus, so typing replaces
 * the old value (amount dialogs). [onFocusLost] runs when the field loses focus (time fields
 * normalise what was typed there).
 */
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
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    selectAllOnFocus: Boolean = false,
    onFocusLost: (() -> Unit)? = null,
) {
    var hadFocus by remember { mutableStateOf(false) }
    var selectAllPending by remember { mutableStateOf(false) }
    var fieldModifier: Modifier = modifier.fillMaxWidth()
    if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
    if (selectAllOnFocus || onFocusLost != null) {
        fieldModifier = fieldModifier.onFocusChanged { state ->
            if (state.isFocused && !hadFocus && selectAllOnFocus) selectAllPending = true
            if (!state.isFocused && hadFocus) onFocusLost?.invoke()
            hadFocus = state.isFocused
        }
    }
    val imeHandler: (() -> Unit)? = onImeAction
    val keyboardActions: KeyboardActions =
        if (imeHandler != null) KeyboardActions(onDone = { imeHandler() }, onGo = { imeHandler() }, onSend = { imeHandler() }, onSearch = { imeHandler() })
        else KeyboardActions.Default
    val keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction)
    val labelSlot: @Composable () -> Unit = { Text(label, style = DriftType.Label12, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    val placeholderSlot: (@Composable () -> Unit)? =
        placeholder?.let { hint -> { Text(hint, style = DriftType.Body, color = DriftColors.TextDim) } }
    val supportingSlot: (@Composable () -> Unit)? =
        supporting?.let { note -> { Text(note, style = DriftType.Label11, color = if (isError) DriftColors.Orange else DriftColors.TextSecondary) } }
    val textStyle = DriftType.Body.copy(color = DriftColors.Text)
    val shape = RoundedCornerShape(14.dp)
    val colors = OutlinedTextFieldDefaults.colors(
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
    )
    if (!selectAllOnFocus) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            label = labelSlot,
            placeholder = placeholderSlot,
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            enabled = enabled,
            supportingText = supportingSlot,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = textStyle,
            shape = shape,
            colors = colors,
        )
    } else {
        // The selection has to be held here: the whole value starts selected (a field focused as
        // its dialog opens shows it selected at once) and is selected again on every later focus.
        var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(0, value.length))) }
        val shown: TextFieldValue = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
        LaunchedEffect(selectAllPending) {
            if (selectAllPending) {
                fieldValue = TextFieldValue(value, TextRange(0, value.length))
                selectAllPending = false
            }
        }
        OutlinedTextField(
            value = shown,
            onValueChange = { next: TextFieldValue ->
                fieldValue = next
                if (next.text != value) onValueChange(next.text)
            },
            modifier = fieldModifier,
            label = labelSlot,
            placeholder = placeholderSlot,
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            enabled = enabled,
            supportingText = supportingSlot,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = textStyle,
            shape = shape,
            colors = colors,
        )
    }
}

/** The pause before a just-opened form or dialog can take focus and raise the keyboard. */
private const val FOCUS_DELAY_MILLIS = 220L

/**
 * Focuses a field once when its form or dialog opens, and raises the keyboard: attach
 * [requester] to the field (`DriftTextField(focusRequester = …)`) and call this beside it.
 * With [enabled] false nothing happens (a form that opens already filled needs no keyboard).
 */
@Composable
fun FocusOnOpen(requester: FocusRequester, enabled: Boolean = true) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(enabled) {
        if (enabled) {
            delay(FOCUS_DELAY_MILLIS)
            runCatching { requester.requestFocus() }
            keyboard?.show()
        }
    }
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

/**
 * Themed alert dialog. The content scrolls vertically (once, here), so a long form stays reachable
 * above the keyboard — callers must not add their own vertical scroll inside.
 */
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
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
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

/**
 * A form screen: [content] scrolls, [bottom] (the primary "שמירה" button) stays pinned under it —
 * above the keyboard when it is open — so saving never needs a scroll.
 */
@Composable
fun FormScreen(
    bottom: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = bottom,
        )
    }
}

/**
 * "לצאת בלי לשמור?" — asked when the user goes back from a form that has edits:
 * שמירה (only when the form is valid) / יציאה (drop the edits) / ביטול (stay).
 */
@Composable
fun UnsavedChangesDialog(canSave: Boolean, onSave: () -> Unit, onLeave: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = DriftColors.Background,
        titleContentColor = DriftColors.Text,
        textContentColor = DriftColors.TextSecondary,
        shape = RoundedCornerShape(22.dp),
        title = { Text(stringResource(R.string.unsaved_title), style = DriftType.Title, color = DriftColors.Text) },
        text = { Text(stringResource(R.string.unsaved_text), style = DriftType.Body, color = DriftColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(stringResource(R.string.save), style = DriftType.RowTitle, color = if (canSave) DriftColors.Lime else DriftColors.TextDim)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), style = DriftType.RowTitle, color = DriftColors.TextSecondary) }
                TextButton(onClick = onLeave) { Text(stringResource(R.string.unsaved_leave), style = DriftType.RowTitle, color = DriftColors.Orange) }
            }
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
