package com.tamir.drift.ui.screens.detail

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.GlassIconButton
import com.tamir.drift.ui.components.PrimaryButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SwitchRow
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.sourceName
import com.tamir.drift.ui.components.toFieldText
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
 */
@Composable
fun TransactionDetailScreen(id: Long, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tx by graph.transactions.observe(id).collectAsStateWithLifecycle(initialValue = null)
    val categories by graph.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())

    val current = tx
    if (current == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(22.dp)) {
            ScreenHeader(stringResource(R.string.detail_title), onBack = onBack, backDescription = stringResource(R.string.nav_back))
            EmptyNote(stringResource(R.string.detail_not_found))
        }
        return
    }
    DetailForm(current, categories.map { it.id to it.name }, onBack = onBack, onDelete = {
        scope.launch { graph.transactions.delete(id); onBack() }
    }, onSave = { updated, categoryChanged ->
        scope.launch {
            if (categoryChanged) {
                // The same merchant's other expenses follow the new category — say so, with the number.
                val moved = graph.transactions.recategorize(updated, updated.categoryId)
                if (moved == 1) graph.showMessage(context.getString(R.string.recat_message_one, updated.merchant))
                else if (moved > 1) graph.showMessage(context.getString(R.string.recat_message_many, moved, updated.merchant))
            } else {
                graph.transactions.update(updated)
            }
            graph.alertChecker.checkAndNotify()
            onBack()
        }
    })
}

@Composable
private fun DetailForm(
    tx: TransactionEntity,
    categoryOptions: List<Pair<String, String>>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: (TransactionEntity, Boolean) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initialAmount = remember(tx.id) { if (tx.isUnparsed) "" else Fmt.fieldAmount(tx.amountFull) }
    var amount by remember(tx.id) { mutableStateOf(initialAmount) }
    var kind by remember(tx.id) { mutableStateOf(tx.txnKind) }
    // The shekel amount of a foreign charge stays an estimate until it is typed over or confirmed.
    var estimateConfirmed by remember(tx.id) { mutableStateOf(false) }
    val stillEstimate = tx.isEstimate && !estimateConfirmed && amount == initialAmount
    var merchant by remember(tx.id) { mutableStateOf(tx.merchant) }
    var categoryId by remember(tx.id) { mutableStateOf(tx.categoryId) }
    var date by remember(tx.id) { mutableStateOf(Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate().toFieldText()) }
    var note by remember(tx.id) { mutableStateOf(tx.note.orEmpty()) }
    var shared by remember(tx.id) { mutableStateOf(tx.isShared) }
    var mine by remember(tx.id) { mutableStateOf(if (tx.isShared) Fmt.fieldAmount(tx.amountMine) else "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val amountValue = AmountInput.parse(amount)
    val mineValue = AmountInput.parse(mine)
    val dateValue = parseDate(date)
    val amountOk = amountValue != null && amountValue > 0
    val shareOk = !shared || (mineValue != null && mineValue >= 0 && amountValue != null && mineValue <= amountValue)
    // An empty "my share" is simply not filled in yet — no error until something was typed.
    val shareError = shared && mine.isNotBlank() && !shareOk
    val valid = amountOk && shareOk && dateValue != null

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(R.string.detail_title), onBack = onBack, backDescription = stringResource(R.string.nav_back)) {
            GlassIconButton(DriftIcons.Trash, stringResource(R.string.delete), { confirmDelete = true }, tint = DriftColors.Orange)
        }

        if (tx.isUnparsed) {
            Text(stringResource(R.string.detail_complete_unparsed), style = DriftType.Label12, color = DriftColors.Orange)
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
        )
        if (stillEstimate) {
            Text(
                stringResource(R.string.detail_estimate_confirm), style = DriftType.Link12, color = DriftColors.Lime,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { estimateConfirmed = true }.padding(vertical = 4.dp),
            )
        }
        DriftTextField(merchant, { merchant = it }, stringResource(R.string.add_merchant))

        Text(stringResource(R.string.add_category), style = DriftType.Label12, color = DriftColors.TextSecondary)
        ChoiceRow(categoryOptions, categoryId, onSelect = { categoryId = it })

        DateField(date, { date = it }, stringResource(R.string.add_date), errorText = stringResource(R.string.date_invalid))
        DriftTextField(note, { note = it }, stringResource(R.string.add_note), singleLine = false, minLines = 2)

        SwitchRow(stringResource(R.string.add_shared), shared, { on ->
            shared = on
            // Turning it on suggests half of the amount as my share (still editable).
            if (on && mine.isBlank() && amountValue != null && amountValue > 0) mine = Fmt.fieldAmount(Money.toAgorot(amountValue / 2))
        })
        if (shared) {
            DriftTextField(
                mine, { mine = it }, stringResource(R.string.add_paid_actually), keyboardType = KeyboardType.Decimal,
                isError = shareError, supporting = if (shareError) stringResource(R.string.add_invalid_share) else null,
            )
        }

        if (!tx.rawText.isNullOrBlank()) {
            Text(stringResource(R.string.detail_raw_text), style = DriftType.Label12, color = DriftColors.TextSecondary)
            GlassCard(Modifier.fillMaxWidth(), radius = 16.dp) {
                Text(
                    tx.rawText, style = DriftType.Label12, color = DriftColors.TextSecondary,
                    modifier = Modifier.padding(12.dp), maxLines = 20, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton(stringResource(R.string.save), enabled = valid, onClick = {
            val full = Money.toAgorot(amountValue ?: return@PrimaryButton)
            val my = if (shared) Money.toAgorot(mineValue ?: return@PrimaryButton) else full
            val day = dateValue ?: return@PrimaryButton
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
            onSave(updated, categoryId != tx.categoryId && merchant.trim().isNotBlank())
        })
        Spacer(Modifier.height(12.dp))
    }

    if (confirmDelete) {
        DriftDialog(
            title = stringResource(R.string.delete),
            onDismiss = { confirmDelete = false },
            confirmText = stringResource(R.string.delete),
            onConfirm = { confirmDelete = false; onDelete() },
            dismissText = stringResource(R.string.cancel),
        ) { Text(stringResource(R.string.detail_delete_confirm), style = DriftType.Body, color = DriftColors.TextSecondary) }
    }
}
