package com.tamir.drift.ui.screens.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.repo.Money
import com.tamir.drift.domain.model.CategoryKeys
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.PrimaryButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SwitchRow
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.toFieldText
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** PRD "הוספה ידנית": amount, merchant, category, date, note, "with friends" (my share). */
@Composable
fun ManualAddScreen(onDone: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val categories by graph.categories.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val zone = ZoneId.systemDefault()

    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(CategoryKeys.OTHER) }
    var date by remember { mutableStateOf(LocalDate.now(zone).toFieldText()) }
    var note by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf("") }
    var autoCategory by remember { mutableStateOf(true) }

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
        ScreenHeader(stringResource(R.string.add_title), onBack = onDone, backDescription = stringResource(R.string.nav_back))

        DriftTextField(
            amount, { amount = it }, stringResource(R.string.add_amount), keyboardType = KeyboardType.Decimal,
            isError = amount.isNotBlank() && !amountOk, supporting = if (amount.isNotBlank() && !amountOk) stringResource(R.string.add_invalid_amount) else null,
        )
        DriftTextField(
            merchant,
            { v ->
                merchant = v
                if (autoCategory) scope.launch { categoryId = graph.categories.classifier().classify(v) }
            },
            stringResource(R.string.add_merchant),
        )

        Text(stringResource(R.string.add_category), style = DriftType.Label12, color = DriftColors.TextSecondary)
        ChoiceRow(
            options = categories.map { it.id to it.name },
            selected = categoryId,
            onSelect = { categoryId = it; autoCategory = false },
        )

        DateField(date, { date = it }, stringResource(R.string.add_date), errorText = stringResource(R.string.date_invalid))
        DriftTextField(note, { note = it }, stringResource(R.string.add_note), singleLine = false, minLines = 2)

        SwitchRow(stringResource(R.string.add_shared), shared, { shared = it })
        if (shared) {
            DriftTextField(
                mine, { mine = it }, stringResource(R.string.add_paid_actually), keyboardType = KeyboardType.Decimal,
                isError = !shareOk, supporting = if (!shareOk) stringResource(R.string.add_invalid_share) else null,
            )
        }

        Spacer(Modifier.height(6.dp))
        PrimaryButton(stringResource(R.string.save), enabled = valid, onClick = {
            val full = Money.toAgorot(amountValue!!)
            val my = if (shared) Money.toAgorot(mineValue!!) else full
            // Keep the current clock time when the date is today, else noon of that day.
            val now = LocalTime.now(zone)
            val ts = dateValue!!.atTime(if (dateValue == LocalDate.now(zone)) now else LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
            scope.launch {
                graph.transactions.addManual(ts, full, my, shared, merchant, categoryId, note)
                graph.alertChecker.checkAndNotify()
                onDone()
            }
        })
        Spacer(Modifier.height(12.dp))
    }
}
