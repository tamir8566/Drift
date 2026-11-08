package com.tamir.drift.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.domain.budget.CategorySpend
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.BudgetLine
import com.tamir.drift.ui.components.CategoryCard
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassIconButton
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.Stat
import com.tamir.drift.ui.components.StatTriplet
import com.tamir.drift.ui.components.TxRow
import com.tamir.drift.ui.components.composeColor
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlin.math.roundToInt

/** Home — the mockup in design/Final.dc.html. */
@Composable
fun HomeScreen(
    onOpenCategory: (String) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm = driftViewModel { HomeViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val summary = state.summary

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Header: "נובמבר · יום 8/30" + search & settings glass buttons
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val day = summary?.dayOfMonth ?: java.time.LocalDate.now().dayOfMonth
                val days = summary?.daysInMonth ?: state.month.lengthOfMonth()
                Text(
                    stringResource(R.string.month_header, Fmt.monthName(context, state.month.monthValue), day, days),
                    style = DriftType.HeaderLabel, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassIconButton(DriftIcons.Search, stringResource(R.string.nav_search), onOpenSearch)
                    GlassIconButton(DriftIcons.Menu, stringResource(R.string.nav_settings), onOpenSettings)
                }
            }
        }

        // Hero: remaining + budget line
        item {
            val remaining = summary?.remaining ?: 0.0
            val over = remaining < 0
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (over) R.string.home_over_label else R.string.home_remaining_label),
                    style = DriftType.Label13, color = DriftColors.TextSecondary,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        Fmt.money(kotlin.math.abs(remaining)),
                        style = DriftType.Hero, color = if (over) DriftColors.Orange else DriftColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        stringResource(R.string.shekel), style = DriftType.HeroCurrency, color = DriftColors.TextSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pct = summary?.percentUsed ?: 0.0
                    BudgetLine(
                        fraction = (pct / 100.0).toFloat(),
                        color = if (over) DriftColors.Orange else DriftColors.Lime,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${Fmt.percent(pct)} · ${stringResource(R.string.out_of, Fmt.money(summary?.spent ?: 0.0), Fmt.money(summary?.totalBudget ?: 0.0))}",
                        style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Three stats: per day · this week · vs previous month
        item {
            val perDay = summary?.allowedPerDay ?: 0.0
            val perDayText = if (summary?.isOverBudget == true) stringResource(R.string.home_exceeded)
            else stringResource(R.string.amount_with_shekel, Fmt.money(perDay))
            val weekText = stringResource(R.string.amount_with_shekel, Fmt.money(summary?.weekSpent ?: 0.0))
            val monthChange = summary?.monthChangePercent
            val prevMonthName = Fmt.monthShort(context, state.month.minusMonths(1).monthValue)
            val monthText = monthChange?.let { Fmt.change(it) } ?: stringResource(R.string.home_no_data_compare)
            val monthColor = when {
                monthChange == null -> DriftColors.TextSecondary
                monthChange <= 0 -> DriftColors.Teal
                else -> DriftColors.Orange
            }
            StatTriplet(
                listOf(
                    Stat(stringResource(R.string.home_per_day), perDayText, if (summary?.isOverBudget == true) DriftColors.Orange else DriftColors.Lime),
                    Stat(stringResource(R.string.home_this_week), weekText),
                    Stat(stringResource(R.string.home_vs_month, prevMonthName), monthText, monthColor),
                ),
            )
        }

        // Category grid 2×2
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionHeader(stringResource(R.string.home_by_category), stringResource(R.string.all), onAction = onOpenAll)
                CategoryGrid(state.topCategories, onOpenCategory = onOpenCategory, names = { state.categories[it]?.name ?: it }, colors = { state.categories[it].composeColor() })
            }
        }

        // Recent transactions
        item {
            SectionHeader(stringResource(R.string.home_recent), stringResource(R.string.all), onAction = onOpenAll, modifier = Modifier.padding(bottom = 0.dp))
        }
        if (state.recent.isEmpty() && !state.loading) {
            item { EmptyNote(stringResource(R.string.empty_list)) }
        }
        items(state.recent, key = { it.id }) { tx ->
            TxRow(tx, state.categories, onClick = { onOpenTransaction(tx.id) })
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

/** Fixed 2×2 grid (no horizontal scrolling): two rows of two equal-width cards, gap 10. */
@Composable
fun CategoryGrid(
    cats: List<CategorySpend>,
    onOpenCategory: (String) -> Unit,
    names: (String) -> String,
    colors: (String) -> androidx.compose.ui.graphics.Color,
) {
    val rows = cats.take(4).chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    CategoryCard(
                        name = names(c.categoryKey),
                        amountText = stringResource(R.string.amount_with_shekel, Fmt.money(c.spent)),
                        percent = c.displayPercent.roundToInt(),
                        barFraction = (c.displayPercent / 100.0).toFloat(),
                        color = colors(c.categoryKey),
                        warning = c.isWarning,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCategory(c.categoryKey) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
