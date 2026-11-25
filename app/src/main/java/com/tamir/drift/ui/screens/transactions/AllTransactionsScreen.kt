package com.tamir.drift.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.Chip
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassIconButton
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.TxRow
import com.tamir.drift.ui.components.isNewSince
import com.tamir.drift.ui.components.rememberListSeenBefore
import com.tamir.drift.ui.components.rowMotion
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch
import java.time.YearMonth

private const val ALL = "__all__"
private const val MORE = "__more__"

/** How many month chips are shown before the rest wait behind "עוד". */
private const val MONTHS_SHOWN = 12

/**
 * PRD "כל ההוצאות" — built so that a search shows its results: the header, the search field, one
 * filter row and the summary line are pinned at the top, and the list scrolls under them, above
 * the keyboard.
 *
 * The filter row is a "סינון" button — it opens the category and month chips as the first item of
 * the list — followed by the filters that are on (a tap on one removes it). The search matches the
 * merchant, the note, the raw text and, for a number, the amount. The summary line describes
 * whatever is listed: "23 הוצאות · 1,840 ₪ · ממוצע 80 ₪".
 * A long press on a row opens its quick-actions sheet.
 */
@Composable
fun AllTransactionsScreen(
    initialCategory: String?,
    initialMonth: String?,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    /** Open with the search field focused and the keyboard up (the home search icon). */
    focusSearch: Boolean = false,
    /** Open showing only what could not be parsed (the home attention row). */
    unparsedOnly: Boolean = false,
) {
    val graph = LocalAppGraph.current
    val vm = driftViewModel(key = "tx/${initialCategory ?: ""}/${initialMonth ?: ""}/$unparsedOnly") {
        AllTransactionsViewModel(graph, initialCategory, initialMonth, unparsedOnly)
    }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Focus the search field once — not again each time the user comes back from an expense.
    var searchFocused by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var allMonths by rememberSaveable { mutableStateOf(false) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun export(month: YearMonth) {
        scope.launch { context.startActivity(vm.exportCsv(context, month, state.categories.mapValues { it.value.name })) }
    }
    val thisYear = YearMonth.now().year
    val filterCategory = state.filter.category
    val filterMonth = state.filter.month
    // Expenses that arrived since this list was last looked at land with a short tint.
    val seenBefore = rememberListSeenBefore(SettingsStore.LIST_ALL_TRANSACTIONS)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Pinned: header, search, the one filter row and the summary of what is listed.
        Column(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader(stringResource(R.string.tx_title), onBack = onBack, backDescription = stringResource(R.string.nav_back)) {
                GlassIconButton(DriftIcons.Share, stringResource(R.string.tx_export_csv), {
                    val m = state.filter.month
                    if (m != null) export(m) else exportDialog = true
                })
            }
            DriftTextField(
                state.filter.query, { vm.setQuery(it) }, stringResource(R.string.tx_search_hint),
                imeAction = ImeAction.Search, onImeAction = { focusManager.clearFocus() },
                focusRequester = searchFocus,
            )
            if (focusSearch && !searchFocused) {
                LaunchedEffect(Unit) {
                    runCatching { searchFocus.requestFocus() }
                    searchFocused = true
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.tx_filter_button), selected = filtersOpen, onClick = { filtersOpen = !filtersOpen })
                // The filters that are on; a tap removes one. They share what is left of the row.
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.filter.unparsedOnly) {
                        Chip(stringResource(R.string.tx_unparsed_only), selected = true, onClick = { vm.setUnparsedOnly(false) })
                    }
                    if (filterCategory != null) {
                        Chip(state.categories[filterCategory]?.name ?: filterCategory, selected = true, onClick = { vm.setCategory(null) }, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (filterMonth != null) {
                        Chip(Fmt.monthLabel(context, filterMonth, withYear = filterMonth.year != thisYear), selected = true, onClick = { vm.setMonth(null) }, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
            val summary = state.summary
            val summaryText = when {
                summary.count > 1 -> stringResource(R.string.tx_summary, summary.count, Fmt.signedMoney(summary.total), Fmt.signedMoney(summary.average))
                summary.count == 1 -> stringResource(R.string.tx_summary_one, Fmt.signedMoney(summary.total))
                state.count == 1 -> stringResource(R.string.tx_count_one)
                else -> stringResource(R.string.tx_count, state.count)
            }
            Text(
                if (state.includesBeforeStart) "$summaryText · ${stringResource(R.string.tx_summary_before_start)}" else summaryText,
                style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filtersOpen) {
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.tx_filter_category), style = DriftType.Label11, color = DriftColors.TextSecondary)
                        ChoiceRow(
                            options = listOf(ALL to stringResource(R.string.tx_all_categories)) + state.categoryList.map { it.id to it.name },
                            selected = filterCategory ?: ALL,
                            onSelect = { vm.setCategory(if (it == ALL) null else it) },
                        )
                        Text(stringResource(R.string.tx_filter_month), style = DriftType.Label11, color = DriftColors.TextSecondary)
                        // Every month that has expenses; the older ones wait behind "עוד".
                        val months = state.availableMonths
                        val showAll = allMonths || months.size <= MONTHS_SHOWN || (filterMonth != null && months.indexOf(filterMonth) >= MONTHS_SHOWN)
                        val shownMonths = if (showAll) months else months.take(MONTHS_SHOWN)
                        val monthOptions = listOf(ALL to stringResource(R.string.tx_all_months)) +
                            shownMonths.map { it.toString() to Fmt.monthLabel(context, it, withYear = it.year != thisYear) } +
                            (if (showAll) emptyList() else listOf(MORE to stringResource(R.string.tx_more_months)))
                        ChoiceRow(
                            options = monthOptions,
                            selected = filterMonth?.toString() ?: ALL,
                            onSelect = {
                                when (it) {
                                    MORE -> { allMonths = true }
                                    ALL -> vm.setMonth(null)
                                    else -> vm.setMonth(YearMonth.parse(it))
                                }
                            },
                        )
                    }
                }
            }

            if (state.unparsed.isNotEmpty()) {
                item(key = "unparsed-header") { SectionHeader(stringResource(R.string.tx_unparsed_section)) }
                items(state.unparsed, key = { "u${it.id}" }) { tx ->
                    TxRow(tx, state.categories, onClick = { onOpenTransaction(tx.id) }, modifier = rowMotion(isNewSince(tx.createdAt, seenBefore)))
                }
            }

            if (state.unparsed.isEmpty() && state.groups.isEmpty()) {
                item(key = "empty") { EmptyNote(stringResource(R.string.tx_none_found)) }
            }

            state.groups.forEach { group ->
                item(key = "d${group.date}") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp, start = 2.dp, end = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.relativeDay(context, group.items.first().timestamp), style = DriftType.SectionTitle, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.amount_with_shekel, Fmt.signedMoneyAgorot(group.total)), style = DriftType.Label12, color = DriftColors.TextSecondary)
                    }
                }
                items(group.items, key = { it.id }) { tx ->
                    TxRow(tx, state.categories, onClick = { onOpenTransaction(tx.id) }, modifier = rowMotion(isNewSince(tx.createdAt, seenBefore)), showDate = false)
                }
            }
            item(key = "end") { Spacer(Modifier.height(8.dp)) }
        }
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
                options = state.availableMonths.map { it.toString() to Fmt.monthLabel(context, it, withYear = true) },
                selected = null,
                onSelect = {
                    exportDialog = false
                    export(YearMonth.parse(it))
                },
            )
        }
    }
}
