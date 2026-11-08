package com.tamir.drift.ui.screens.detail

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.repo.Money
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
 */
@Composable
fun TransactionDetailScreen(id: Long, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
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
            if (categoryChanged) graph.transactions.recategorize(updated, updated.categoryId)
            else graph.transactions.update(updated)
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
    var amount by remember(tx.id) { mutableStateOf(if (tx.isUnparsed) "" else Fmt.moneyExact(tx.amountFull).replace(",", "")) }
    var merchant by remember(tx.id) { mutableStateOf(tx.merchant) }
    var categoryId by remember(tx.id) { mutableStateOf(tx.categoryId) }
    var date by remember(tx.id) { mutableStateOf(Instant.ofEpochMilli(tx.timestamp).atZone(zone).toLocalDate().toFieldText()) }
    var note by remember(tx.id) { mutableStateOf(tx.note.orEmpty()) }
    var shared by remember(tx.id) { mutableStateOf(tx.isShared) }
    var mine by remember(tx.id) { mutableStateOf(if (tx.isShared) Fmt.moneyExact(tx.amountMine).replace(",", "") else "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val amountValue = amount.replace(",", "").toDoubleOrNull()
    val mineValue = mine.replace(",", "").toDoubleOrNull()
    val dateValue = parseDate(date)
    val amountOk = amountValue != null && amountValue > 0
    val shareOk = !shared || (mineValue != null && mineValue >= 0 && amountValue != null && mineValue <= amountValue)
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.add_date), style = DriftType.Label12, color = DriftColors.TextSecondary)
                    Text("${Fmt.shortDate(tx.timestamp)} · ${Fmt.time(tx.timestamp)}", style = DriftType.Label12, color = DriftColors.Text)
                }
            }
        }

        DriftTextField(
            amount, { amount = it }, stringResource(R.string.detail_full_amount), keyboardType = KeyboardType.Decimal,
            isError = amount.isNotBlank() && !amountOk, supporting = if (amount.isNotBlank() && !amountOk) stringResource(R.string.add_invalid_amount) else null,
        )
        DriftTextField(merchant, { merchant = it }, stringResource(R.string.add_merchant))

        Text(stringResource(R.string.add_category), style = DriftType.Label12, color = DriftColors.TextSecondary)
        ChoiceRow(categoryOptions, categoryId, onSelect = { categoryId = it })

        DateField(date, { date = it }, stringResource(R.string.add_date), errorText = stringResource(R.string.date_invalid))
        DriftTextField(note, { note = it }, stringResource(R.string.add_note), singleLine = false, minLines = 2)

        SwitchRow(stringResource(R.string.add_shared), shared, { shared = it })
        if (shared) {
            DriftTextField(
                mine, { mine = it }, stringResource(R.string.add_paid_actually), keyboardType = KeyboardType.Decimal,
                isError = !shareOk, supporting = if (!shareOk) stringResource(R.string.add_invalid_share) else null,
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
            val full = Money.toAgorot(amountValue!!)
            val my = if (shared) Money.toAgorot(mineValue!!) else full
            val oldDate = Instant.ofEpochMilli(tx.timestamp).atZone(zone)
            val ts = if (dateValue == oldDate.toLocalDate()) tx.timestamp
            else dateValue!!.atTime(oldDate.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
            val updated = tx.copy(
                amountFull = full,
                amountMine = my,
                isShared = shared,
                merchant = merchant.trim(),
                categoryId = categoryId,
                timestamp = ts,
                note = note.takeIf { it.isNotBlank() },
                isUnparsed = false,
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
