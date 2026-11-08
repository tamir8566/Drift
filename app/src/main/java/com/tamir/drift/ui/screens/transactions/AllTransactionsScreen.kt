package com.tamir.drift.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassIconButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.TxRow
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch
import java.time.YearMonth

private const val ALL = "__all__"

@Composable
fun AllTransactionsScreen(
    initialCategory: String?,
    initialMonth: String?,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm = driftViewModel(key = "tx/${initialCategory ?: ""}/${initialMonth ?: ""}") { AllTransactionsViewModel(graph, initialCategory, initialMonth) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun export(month: YearMonth) {
        scope.launch { context.startActivity(vm.exportCsv(context, month, state.categories.mapValues { it.value.name })) }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(stringResource(R.string.tx_title), onBack = onBack, backDescription = stringResource(R.string.nav_back)) {
                GlassIconButton(DriftIcons.Share, stringResource(R.string.tx_export_csv), {
                    val m = state.filter.month
                    if (m != null) export(m) else exportDialog = true
                })
            }
        }
        item {
            DriftTextField(state.filter.query, { vm.setQuery(it) }, stringResource(R.string.tx_search_hint))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tx_filter_category), style = DriftType.Label11, color = DriftColors.TextSecondary)
                ChoiceRow(
                    options = listOf(ALL to stringResource(R.string.tx_all_categories)) + state.categoryList.map { it.id to it.name },
                    selected = state.filter.category ?: ALL,
                    onSelect = { vm.setCategory(if (it == ALL) null else it) },
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tx_filter_month), style = DriftType.Label11, color = DriftColors.TextSecondary)
                val monthOptions = listOf(ALL to stringResource(R.string.tx_all_months)) +
                    state.availableMonths.take(12).map { it.toString() to Fmt.monthLabel(context, it, withYear = it.year != YearMonth.now().year) }
                ChoiceRow(
                    options = monthOptions,
                    selected = state.filter.month?.toString() ?: ALL,
                    onSelect = { vm.setMonth(if (it == ALL) null else YearMonth.parse(it)) },
                )
            }
        }
        item {
            Text(stringResource(R.string.tx_count, state.count), style = DriftType.Label11, color = DriftColors.TextDim)
        }

        if (state.unparsed.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.tx_unparsed_section)) }
            items(state.unparsed, key = { "u${it.id}" }) { tx -> TxRow(tx, state.categories, onClick = { onOpenTransaction(tx.id) }) }
        }

        if (state.unparsed.isEmpty() && state.groups.isEmpty()) {
            item { EmptyNote(stringResource(R.string.tx_none_found)) }
        }

        state.groups.forEach { group ->
            item(key = "d${group.date}") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp, start = 2.dp, end = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.relativeDay(context, group.items.first().timestamp), style = DriftType.SectionTitle, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.amount_with_shekel, Fmt.moneyAgorot(group.total)), style = DriftType.Label12, color = DriftColors.TextSecondary)
                }
            }
            items(group.items, key = { it.id }) { tx -> TxRow(tx, state.categories, onClick = { onOpenTransaction(tx.id) }, showDate = false) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (exportDialog) {
        DriftDialog(
            title = stringResource(R.string.tx_export_title),
            onDismiss = { exportDialog = false },
            confirmText = stringResource(R.string.ok),
            onConfirm = { exportDialog = false },
        ) {
            Text(stringResource(R.string.tx_export_choose_month), style = DriftType.Body, color = DriftColors.TextSecondary)
            ChoiceRow(
                options = state.availableMonths.take(12).map { it.toString() to Fmt.monthLabel(context, it, withYear = true) },
                selected = null,
                onSelect = {
                    exportDialog = false
                    export(YearMonth.parse(it))
                },
            )
        }
    }
}
