package com.tamir.drift.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.db.txnKind
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.money.MoneyText
import com.tamir.drift.domain.money.ShareSplit
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.announceRecategorized
import com.tamir.drift.ui.deleteTransactionWithUndo
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch

/**
 * Opens the quick-actions sheet for an expense id. Provided by the root of the UI (which draws
 * the sheet above everything, the bottom bar included); every [TxRow] calls it on a long press.
 */
val LocalTxQuickSheet = staticCompositionLocalOf<(Long) -> Unit> { { _ -> } }

private const val SHARE_NONE = "none"

/**
 * The long-press sheet of an expense row: one tap recategorises (teaching the merchant rule, with
 * the "עודכנו N הוצאות" line), sets "עם חברים" to a half / third / quarter or off, changes the
 * kind, or deletes (with undo). A glass card anchored to the bottom over a scrim — a tap outside
 * or the back button closes it; every action closes it too.
 */
@Composable
fun TxQuickSheet(txId: Long, onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val txFlow = remember(txId) { graph.transactions.observe(txId) }
    val observed by txFlow.collectAsStateWithLifecycle(initialValue = null)
    val categories by graph.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    BackHandler(onBack = onDismiss)

    fun perform(change: suspend () -> Unit) {
        graph.scope.launch {
            change()
            graph.alertChecker.checkAndNotify()
        }
        onDismiss()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val tx: TransactionEntity = observed ?: return@Box
        val sheetShape = RoundedCornerShape(22.dp)
        GlassCard(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
                .fillMaxWidth()
                .background(DriftColors.Background, sheetShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            radius = 22.dp,
            borderColor = DriftColors.PlusBorder,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // What the sheet is about: the merchant and the amount.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (tx.isUnparsed) stringResource(R.string.unparsed_title) else tx.merchant.ifBlank { stringResource(R.string.no_merchant) },
                        style = DriftType.Title, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!tx.isUnparsed) {
                        val digits = Fmt.moneyExact(tx.amountFull).let { if (tx.txnKind == TxnKind.REFUND) MoneyText.negative(it) else it }
                        Text(stringResource(R.string.amount_with_shekel, digits), style = DriftType.RowAmount, color = DriftColors.Text, maxLines = 1)
                    }
                }

                if (!tx.isUnparsed) {
                    Text(stringResource(R.string.add_category), style = DriftType.Label12, color = DriftColors.TextSecondary)
                    ChoiceRow(
                        options = categories.map { it.id to it.name },
                        selected = tx.categoryId,
                        onSelect = { picked ->
                            if (picked == tx.categoryId) onDismiss()
                            else perform {
                                val moved = graph.transactions.recategorize(tx, picked)
                                graph.announceRecategorized(moved, tx.merchant)
                            }
                        },
                    )

                    Text(stringResource(R.string.add_shared), style = DriftType.Label12, color = DriftColors.TextSecondary)
                    val parts = if (tx.isShared) ShareSplit.partsOf(tx.amountFull, tx.amountMine) else null
                    ChoiceRow(
                        options = listOf(
                            "2" to stringResource(R.string.share_half),
                            "3" to stringResource(R.string.share_third),
                            "4" to stringResource(R.string.share_quarter),
                            SHARE_NONE to stringResource(R.string.share_none),
                        ),
                        selected = when {
                            !tx.isShared -> SHARE_NONE
                            parts != null -> parts.toString()
                            else -> null
                        },
                        onSelect = { picked ->
                            val split = picked.toIntOrNull()
                            perform {
                                if (split == null) graph.transactions.update(tx.copy(isShared = false, amountMine = tx.amountFull))
                                else graph.transactions.update(tx.copy(isShared = true, amountMine = ShareSplit.share(tx.amountFull, split)))
                            }
                        },
                    )

                    Text(stringResource(R.string.quick_kind), style = DriftType.Label12, color = DriftColors.TextSecondary)
                    ChoiceRow(
                        options = listOf(
                            TxnKind.EXPENSE.name to stringResource(R.string.kind_expense),
                            TxnKind.REFUND.name to stringResource(R.string.kind_refund),
                            TxnKind.TRANSFER.name to stringResource(R.string.kind_transfer),
                        ),
                        selected = tx.txnKind.name,
                        onSelect = { picked ->
                            if (picked == tx.txnKind.name) onDismiss()
                            else perform { graph.transactions.update(tx.copy(kind = TxnKind.from(picked).name)) }
                        },
                    )
                    HairlineDivider()
                }

                Text(
                    stringResource(R.string.delete), style = DriftType.RowTitle, color = DriftColors.Orange, maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            graph.deleteTransactionWithUndo(tx)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
