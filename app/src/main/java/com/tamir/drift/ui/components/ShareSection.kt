package com.tamir.drift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tamir.drift.R
import com.tamir.drift.data.repo.Money
import com.tamir.drift.domain.money.AmountInput
import com.tamir.drift.domain.money.ShareSplit
import com.tamir.drift.ui.format.Fmt

/**
 * "עם חברים" in a form: the switch and, when it is on, the chips "חצי · שליש · רבע" (one tap sets
 * my share to the amount / 2, / 3, / 4, rounded to the agora) over the "כמה שילמתי בפועל" field.
 * Turning the switch on suggests half, which stays editable.
 *
 * [amountValue] is the full amount typed so far (shekels; null when it is not a number);
 * [onDone] runs for the keyboard's "Done" on the share field — the last field of the form.
 */
@Composable
fun ShareSection(
    shared: Boolean,
    onSharedChange: (Boolean) -> Unit,
    mine: String,
    onMineChange: (String) -> Unit,
    amountValue: Double?,
    isError: Boolean,
    onDone: () -> Unit,
) {
    val amountAgorot: Long? = amountValue?.takeIf { it > 0.0 }?.let { Money.toAgorot(it) }
    val mineAgorot: Long? = AmountInput.parse(mine)?.let { Money.toAgorot(it) }
    SwitchRow(stringResource(R.string.add_shared), shared, { on ->
        onSharedChange(on)
        if (on && mine.isBlank() && amountAgorot != null) onMineChange(Fmt.fieldAmount(ShareSplit.share(amountAgorot, 2)))
    })
    if (shared) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareSplit.PARTS.forEach { parts ->
                val share: Long? = amountAgorot?.let { ShareSplit.share(it, parts) }
                Chip(
                    stringResource(
                        when (parts) {
                            2 -> R.string.share_half
                            3 -> R.string.share_third
                            else -> R.string.share_quarter
                        },
                    ),
                    selected = share != null && mineAgorot == share,
                    onClick = { if (share != null) onMineChange(Fmt.fieldAmount(share)) },
                )
            }
        }
        DriftTextField(
            mine, onMineChange, stringResource(R.string.add_paid_actually), keyboardType = KeyboardType.Decimal,
            isError = isError, supporting = if (isError) stringResource(R.string.add_invalid_share) else null,
            imeAction = ImeAction.Done, onImeAction = onDone,
        )
    }
}
