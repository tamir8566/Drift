package com.tamir.drift.ui.screens.add

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.repo.Money
import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.domain.money.AmountInput
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.announceExpenseSaved
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.FocusOnOpen
import com.tamir.drift.ui.components.FormScreen
import com.tamir.drift.ui.components.PrimaryButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.ShareSection
import com.tamir.drift.ui.components.UnsavedChangesDialog
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.toFieldText
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * PRD "הוספה ידנית": amount, merchant, category, date, note, "with friends" (my share).
 *
 * Built for speed: the amount field has the focus and the keyboard as the screen opens, the
 * keyboard's "Next" walks the fields and "Done" on the last one saves, "שמירה" stays pinned above
 * the keyboard, and the date is one tap (היום · אתמול · שלשום, or the calendar). Saving shows
 * "נשמרה הוצאה · X ₪" with an undo; leaving with something typed asks "לצאת בלי לשמור?".
 */
@Composable
fun ManualAddScreen(onDone: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val categories by graph.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val zone = ZoneId.systemDefault()
    val firstDate = remember { LocalDate.now(zone).toFieldText() }

    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(CategoryKeys.OTHER) }
    var date by remember { mutableStateOf(firstDate) }
    var note by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf("") }
    var autoCategory by remember { mutableStateOf(true) }
    var askLeave by remember { mutableStateOf(false) }
    // Set once the expense is being saved, so a second tap cannot add it twice.
    var closing by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    val amountValue = AmountInput.parse(amount)
    val mineValue = AmountInput.parse(mine)
    val dateValue = parseDate(date)
    val amountOk = amountValue != null && amountValue > 0
    val shareOk = !shared || (mineValue != null && mineValue >= 0 && amountValue != null && mineValue <= amountValue)
    // An empty "my share" is simply not filled in yet — no error until something was typed.
    val shareError = shared && mine.isNotBlank() && !shareOk
    val valid = amountOk && shareOk && dateValue != null
    val dirty = amount.isNotBlank() || merchant.isNotBlank() || note.isNotBlank() || shared || !autoCategory || date != firstDate

    fun save() {
        if (!valid || closing) return
        val full = Money.toAgorot(amountValue ?: return)
        val my = if (shared) Money.toAgorot(mineValue ?: return) else full
        // Keep the current clock time when the date is today, else noon of that day.
        val now = LocalTime.now(zone)
        val day = dateValue ?: return
        val ts = day.atTime(if (day == LocalDate.now(zone)) now else LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        val isShared = shared
        val merchantText = merchant
        val category = categoryId
        val noteText = note
        closing = true
        graph.scope.launch {
            val id = graph.transactions.addManual(ts, full, my, isShared, merchantText, category, noteText)
            graph.alertChecker.checkAndNotify()
            graph.announceExpenseSaved(id, my)
        }
        onDone()
    }

    val leave: () -> Unit = { if (dirty && !closing) { askLeave = true } else { onDone() } }
    BackHandler(enabled = dirty && !askLeave && !closing) { askLeave = true }

    FormScreen(
        bottom = { PrimaryButton(stringResource(R.string.save), enabled = valid && !closing, onClick = { save() }) },
    ) {
        ScreenHeader(stringResource(R.string.add_title), onBack = leave, backDescription = stringResource(R.string.nav_back))

        DriftTextField(
            amount, { amount = it }, stringResource(R.string.add_amount), keyboardType = KeyboardType.Decimal,
            isError = amount.isNotBlank() && !amountOk, supporting = if (amount.isNotBlank() && !amountOk) stringResource(R.string.add_invalid_amount) else null,
            imeAction = ImeAction.Next, focusRequester = amountFocus,
        )
        FocusOnOpen(amountFocus)
        DriftTextField(
            merchant,
            { v ->
                merchant = v
                if (autoCategory) scope.launch { categoryId = graph.categories.classifier().classify(v) }
            },
            stringResource(R.string.add_merchant),
            imeAction = ImeAction.Next,
        )

        Text(stringResource(R.string.add_category), style = DriftType.Label12, color = DriftColors.TextSecondary)
        ChoiceRow(
            options = categories.map { it.id to it.name },
            selected = categoryId,
            onSelect = { categoryId = it; autoCategory = false },
        )

        DateField(date, { date = it }, stringResource(R.string.add_date), errorText = stringResource(R.string.date_invalid), imeAction = ImeAction.Next)
        // "Done" on the last field saves (when the form is valid): the note, or the share under it.
        DriftTextField(
            note, { note = it }, stringResource(R.string.add_note), singleLine = false, minLines = 2,
            imeAction = if (shared) ImeAction.Next else ImeAction.Done, onImeAction = { save() },
        )

        ShareSection(
            shared = shared,
            onSharedChange = { shared = it },
            mine = mine,
            onMineChange = { mine = it },
            amountValue = amountValue,
            isError = shareError,
            onDone = { save() },
        )
    }

    if (askLeave) {
        UnsavedChangesDialog(
            canSave = valid,
            onSave = { askLeave = false; save() },
            onLeave = { askLeave = false; onDone() },
            onCancel = { askLeave = false },
        )
    }
}
