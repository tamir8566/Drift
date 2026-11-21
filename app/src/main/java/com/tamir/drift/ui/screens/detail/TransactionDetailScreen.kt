package com.tamir.drift.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.db.txnKind
import com.tamir.drift.data.repo.Money
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.money.AmountInput
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.announceRecategorized
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.FocusOnOpen
import com.tamir.drift.ui.components.FormScreen
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.GlassIconButton
import com.tamir.drift.ui.components.PrimaryButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.ShareSection
import com.tamir.drift.ui.components.UnsavedChangesDialog
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.sourceName
import com.tamir.drift.ui.components.toFieldText
import com.tamir.drift.ui.deleteTransactionWithUndo
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * PRD "פרטי הוצאה": all fields, "with friends" switch revealing "how much I actually paid",
 * raw text; changing category teaches a rule; delete.
 *
 * Kind: "הוצאה / זיכוי / לא נספר" — a refund is subtracted from the spend, "לא נספר" (a transfer)
 * is left out of every calculation. A foreign charge shows its original amount; its shekel amount
 * is an estimate ("≈") until the user types the final one or confirms it.
 * When a category change also moved other expenses of the same merchant, the user is told how many.
 *
 * An item that could not be parsed shows its raw captured text at the top — to read and type in
 * place, with the amount field focused — and "למד תבנית מההודעה הזו" opens the calibration screen
 * with that text; for a parsed item the raw text waits, folded, at the bottom.
 * "שמירה" is pinned under the form; deleting happens at once with an undo in the root message;
 * leaving with edits asks "לצאת בלי לשמור?".
 */
@Composable
fun TransactionDetailScreen(id: Long, onBack: () -> Unit, onOpenCalibration: (Long) -> Unit) {
    val graph = LocalAppGraph.current
    val txFlow = remember(id) { graph.transactions.observe(id) }
    val tx by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val categories by graph.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())

    val current = tx
    if (current == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(22.dp)) {
            ScreenHeader(stringResource(R.string.detail_title), onBack = onBack, backDescription = stringResource(R.string.nav_back))
            EmptyNote(stringResource(R.string.detail_not_found))
        }
        return
    }
    DetailForm(
        current, categories.map { it.id to it.name }, onBack = onBack,
        onDelete = {
            // Deleted at once; the root message offers "ביטול", which writes the same row back.
            graph.deleteTransactionWithUndo(current)
            onBack()
        },
        onSave = { updated, categoryChanged ->
            // On the app scope: the write finishes although the screen closes right away.
            graph.scope.launch {
                if (categoryChanged) {
                    // The same merchant's other expenses follow the new category — say so, with the number.
                    val moved = graph.transactions.recategorize(updated, updated.categoryId)
                    graph.announceRecategorized(moved, updated.merchant)
                } else {
                    graph.transactions.update(updated)
                }
                graph.alertChecker.checkAndNotify()
            }
            onBack()
        },
        onOpenCalibration = { onOpenCalibration(id) },
    )
}

@Composable
private fun DetailForm(
    tx: TransactionEntity,
    categoryOptions: List<Pair<String, String>>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: (TransactionEntity, Boolean) -> Unit,
    onOpenCalibration: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initialAmount = remember(tx.id) { if (tx.isUnparsed) "" else Fmt.fieldAmount(tx.amountFull) }
    val initialDate = remember(tx.id) { Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate().toFieldText() }
    val initialMine = remember(tx.id) { if (tx.isShared) Fmt.fieldAmount(tx.amountMine) else "" }
    var amount by remember(tx.id) { mutableStateOf(initialAmount) }
    var kind by remember(tx.id) { mutableStateOf(tx.txnKind) }
    // The shekel amount of a foreign charge stays an estimate until it is typed over or confirmed.
    var estimateConfirmed by remember(tx.id) { mutableStateOf(false) }
    val stillEstimate = tx.isEstimate && !estimateConfirmed && amount == initialAmount
    var merchant by remember(tx.id) { mutableStateOf(tx.merchant) }
    var categoryId by remember(tx.id) { mutableStateOf(tx.categoryId) }
    var date by remember(tx.id) { mutableStateOf(initialDate) }
    var note by remember(tx.id) { mutableStateOf(tx.note.orEmpty()) }
    var shared by remember(tx.id) { mutableStateOf(tx.isShared) }
    var mine by remember(tx.id) { mutableStateOf(initialMine) }
    var rawExpanded by remember(tx.id) { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    // Set once the form is being saved or deleted, so a second tap cannot repeat it.
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
    val dirty = amount != initialAmount || kind != tx.txnKind || estimateConfirmed || merchant != tx.merchant ||
        categoryId != tx.categoryId || date != initialDate || note != tx.note.orEmpty() || shared != tx.isShared || mine != initialMine

    fun save() {
        if (!valid || closing) return
        val full = Money.toAgorot(amountValue ?: return)
        val my = if (shared) Money.toAgorot(mineValue ?: return) else full
        val day = dateValue ?: return
        val oldDate = Instant.ofEpochMilli(tx.timestamp).atZone(zone)
        val ts = if (day == oldDate.toLocalDate()) tx.timestamp
        else day.atTime(oldDate.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        val updated = tx.copy(
            amountFull = full,
            amountMine = my,
            isShared = shared,
            merchant = merchant.trim(),
            categoryId = categoryId,
            timestamp = ts,
            note = note.takeIf { it.isNotBlank() },
            isUnparsed = false,
            kind = kind.name,
            isEstimate = stillEstimate,
        )
        closing = true
        onSave(updated, categoryId != tx.categoryId && merchant.trim().isNotBlank())
    }

    val leave: () -> Unit = { if (dirty && !closing) { askLeave = true } else { onBack() } }
    BackHandler(enabled = dirty && !askLeave && !closing) { askLeave = true }

    FormScreen(
        bottom = { PrimaryButton(stringResource(R.string.save), enabled = valid && !closing, onClick = { save() }) },
    ) {
        ScreenHeader(stringResource(R.string.detail_title), onBack = leave, backDescription = stringResource(R.string.nav_back)) {
            GlassIconButton(DriftIcons.Trash, stringResource(R.string.delete), {
                if (!closing) {
                    closing = true
                    onDelete()
                }
            }, tint = DriftColors.Orange)
        }

        val rawText = tx.rawText
        if (tx.isUnparsed) {
            Text(stringResource(R.string.detail_complete_unparsed), style = DriftType.Label12, color = DriftColors.Orange)
            // Not parsed: the message itself comes first, so it is read and typed from in place.
            if (!rawText.isNullOrBlank()) RawTextCard(rawText, onLearnPattern = onOpenCalibration)
        }

        // Read-only meta
        GlassCard(Modifier.fillMaxWidth(), radius = 20.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.detail_source), style = DriftType.Label12, color = DriftColors.TextSecondary)
                    Text(sourceName(tx.source), style = DriftType.Label12, color = DriftColors.Text)
                }
                if (tx.cardLast4 != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.detail_card), style = DriftType.Label12, color = DriftColors.TextSecondary)
                        Text(stringResource(R.string.detail_card_last4, tx.cardLast4), style = DriftType.Label12, color = DriftColors.Text)
                    }
                }
                // The date itself is the editable field below; only the clock time is shown here.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.detail_time), style = DriftType.Label12, color = DriftColors.TextSecondary)
                    Text(Fmt.time(tx.timestamp), style = DriftType.Label12, color = DriftColors.Text)
                }
                val origAmount = tx.origAmountMinor
                if (origAmount != null && tx.origCurrency != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.detail_original_amount), style = DriftType.Label12, color = DriftColors.TextSecondary)
                        Text(Fmt.foreign(origAmount, tx.origCurrency), style = DriftType.Label12, color = DriftColors.Text)
                    }
                }
            }
        }

        // Kind: an expense counts, a refund is subtracted, "לא נספר" is left out of everything.
        ChoiceRow(
            options = listOf(
                TxnKind.EXPENSE.name to stringResource(R.string.kind_expense),
                TxnKind.REFUND.name to stringResource(R.string.kind_refund),
                TxnKind.TRANSFER.name to stringResource(R.string.kind_transfer),
            ),
            selected = kind.name,
            onSelect = { kind = TxnKind.from(it) },
        )
        if (kind != TxnKind.EXPENSE) {
            Text(
                stringResource(if (kind == TxnKind.REFUND) R.string.kind_refund_desc else R.string.kind_transfer_desc),
                style = DriftType.Label11, color = DriftColors.TextSecondary,
            )
        }

        DriftTextField(
            amount, { amount = it }, stringResource(R.string.detail_full_amount), keyboardType = KeyboardType.Decimal,
            isError = amount.isNotBlank() && !amountOk,
            supporting = when {
                amount.isNotBlank() && !amountOk -> stringResource(R.string.add_invalid_amount)
                stillEstimate -> stringResource(R.string.detail_estimate_hint)
                else -> null
            },
            imeAction = ImeAction.Next, focusRequester = amountFocus,
        )
        // Only an item that still lacks its amount opens with the keyboard up.
        FocusOnOpen(amountFocus, enabled = tx.isUnparsed)
        if (stillEstimate) {
            Text(
                stringResource(R.string.detail_estimate_confirm), style = DriftType.Link12, color = DriftColors.Lime,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { estimateConfirmed = true }.padding(vertical = 4.dp),
            )
        }
        DriftTextField(merchant, { merchant = it }, stringResource(R.string.add_merchant), imeAction = ImeAction.Next)

        Text(stringResource(R.string.add_category), style = DriftType.Label12, color = DriftColors.TextSecondary)
        ChoiceRow(categoryOptions, categoryId, onSelect = { categoryId = it })

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

        // Parsed: the raw captured text stays out of the way, folded at the bottom.
        if (!tx.isUnparsed && !rawText.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { rawExpanded = !rawExpanded }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.detail_raw_text), style = DriftType.Label12, color = DriftColors.TextSecondary)
                Text(
                    stringResource(if (rawExpanded) R.string.detail_raw_hide else R.string.detail_raw_show),
                    style = DriftType.Link12, color = DriftColors.Lime,
                )
            }
            if (rawExpanded) RawTextCard(rawText, onLearnPattern = onOpenCalibration)
        }
    }

    if (askLeave) {
        UnsavedChangesDialog(
            canSave = valid,
            onSave = { askLeave = false; save() },
            onLeave = { askLeave = false; onBack() },
            onCancel = { askLeave = false },
        )
    }
}

/** The captured message as it arrived, and the link that teaches the parser a pattern from it. */
@Composable
private fun RawTextCard(text: String, onLearnPattern: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), radius = 16.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 20, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.detail_learn_pattern), style = DriftType.Link12, color = DriftColors.Lime, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onLearnPattern).padding(vertical = 4.dp),
            )
        }
    }
}
