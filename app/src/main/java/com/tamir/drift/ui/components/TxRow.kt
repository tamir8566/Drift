package com.tamir.drift.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tamir.drift.R
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.domain.model.TransactionSource
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors

@Composable
fun sourceName(source: String): String = when (source) {
    TransactionSource.GPAY.name -> stringResource(R.string.source_gpay)
    TransactionSource.SMS.name -> stringResource(R.string.source_sms)
    else -> stringResource(R.string.source_manual)
}

/** Maps a stored transaction to the design's row: "merchant" / "today · Google Pay · category" / amount. */
@Composable
fun TxRow(
    tx: TransactionEntity,
    categories: Map<String, CategoryEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    val context = LocalContext.current
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
            onClick = onClick,
        )
        return
    }
    val metaParts = buildList {
        if (showDate) add(Fmt.relativeDay(context, tx.timestamp)) else add(Fmt.time(tx.timestamp))
        add(sourceName(tx.source))
        add(categoryName)
    }
    TransactionRow(
        title = tx.merchant.ifBlank { stringResource(R.string.no_merchant) },
        meta = metaParts.joinToString(" · "),
        amountText = stringResource(R.string.amount_with_shekel, Fmt.moneyExact(tx.amountFull)),
        dotColor = dot,
        sharedTag = if (tx.isShared) stringResource(R.string.shared_tag, Fmt.moneyExact(tx.amountMine)) else null,
        modifier = modifier,
        onClick = onClick,
    )
}

/** Category color helper. */
fun CategoryEntity?.composeColor(): Color = this?.let { Color(it.color) } ?: DriftColors.TextDim
