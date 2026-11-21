package com.tamir.drift.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tamir.drift.R
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.db.txnKind
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.domain.model.TxnKind
import com.tamir.drift.domain.money.MoneyText
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors

@Composable
fun sourceName(source: String): String = when (source) {
    TransactionSource.GPAY.name -> stringResource(R.string.source_gpay)
    TransactionSource.SMS.name -> stringResource(R.string.source_sms)
    else -> stringResource(R.string.source_manual)
}

/**
 * Maps a stored transaction to the design's row: "merchant" / "today · Google Pay · category" / amount.
 * A refund is shown in lime with a minus and a "זיכוי" tag; a transfer is dimmed with "לא נספר";
 * a foreign charge shows "≈" before its estimated shekel amount and the original amount as a tag.
 * A long press opens the quick-actions sheet of the expense ([LocalTxQuickSheet]) unless
 * [quickActions] is off.
 */
@Composable
fun TxRow(
    tx: TransactionEntity,
    categories: Map<String, CategoryEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    quickActions: Boolean = true,
) {
    val context = LocalContext.current
    val openQuickSheet = LocalTxQuickSheet.current
    val onLongClick: (() -> Unit)? = if (quickActions) { { openQuickSheet(tx.id) } } else null
    val category = categories[tx.categoryId]
    val categoryName = category?.name ?: stringResource(R.string.cat_other)
    val dot = category?.let { Color(it.color) } ?: DriftColors.TextDim
    if (tx.isUnparsed) {
        TransactionRow(
            title = stringResource(R.string.unparsed_title),
            meta = listOf(Fmt.relativeDay(context, tx.timestamp), sourceName(tx.source), stringResource(R.string.unparsed_subtitle)).joinToString(" · "),
            amountText = "—",
            dotColor = DriftColors.Orange,
            sharedTag = null,
            titleColor = DriftColors.Orange,
            modifier = modifier,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        return
    }
    val metaParts = buildList {
        if (showDate) add(Fmt.relativeDay(context, tx.timestamp)) else add(Fmt.time(tx.timestamp))
        add(sourceName(tx.source))
        add(categoryName)
    }
    val kind = tx.txnKind
    // A refund reads as money coming back: lime, with a minus in front of the digits.
    val digits = Fmt.moneyExact(tx.amountFull).let { if (kind == TxnKind.REFUND) MoneyText.negative(it) else it }
    // "≈" marks a shekel amount that is only an estimate of a foreign charge.
    val amount = (if (tx.isEstimate) "≈ " else "") + stringResource(R.string.amount_with_shekel, digits)
    val origAmount = tx.origAmountMinor
    val tags = buildList {
        when (kind) {
            TxnKind.REFUND -> add(stringResource(R.string.kind_refund))
            TxnKind.TRANSFER -> add(stringResource(R.string.kind_transfer))
            TxnKind.EXPENSE -> Unit
        }
        if (origAmount != null && tx.origCurrency != null) add(Fmt.foreign(origAmount, tx.origCurrency))
        if (tx.isShared) add(stringResource(R.string.shared_tag, Fmt.moneyExact(tx.amountMine)))
    }
    TransactionRow(
        title = tx.merchant.ifBlank { stringResource(R.string.no_merchant) },
        meta = metaParts.joinToString(" · "),
        amountText = amount,
        dotColor = dot,
        sharedTag = if (tags.isEmpty()) null else tags.joinToString(" · "),
        modifier = modifier,
        amountColor = if (kind == TxnKind.REFUND) DriftColors.Lime else DriftColors.Text,
        tagColor = if (kind == TxnKind.TRANSFER) DriftColors.TextSecondary else DriftColors.Lime,
        dimmed = kind == TxnKind.TRANSFER,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

/** Category color helper. */
fun CategoryEntity?.composeColor(): Color = this?.let { Color(it.color) } ?: DriftColors.TextDim
