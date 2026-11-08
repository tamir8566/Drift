package com.tamir.drift.ui.screens.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.repo.Money
import com.tamir.drift.domain.budget.CategorySpend
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.BudgetLine
import com.tamir.drift.ui.components.CategoryBar
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.Stat
import com.tamir.drift.ui.components.StatTriplet
import com.tamir.drift.ui.components.SwitchRow
import com.tamir.drift.ui.components.composeColor
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlin.math.roundToInt

@Composable
fun BudgetScreen() {
    val graph = LocalAppGraph.current
    val vm = driftViewModel { BudgetViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val summary = state.summary

    var editTotal by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.budget_title), onBack = null) }

        // Total budget card (tap to edit)
        item {
            GlassCard(Modifier.fillMaxWidth(), onClick = { editTotal = true }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.budget_total), style = DriftType.Label13, color = DriftColors.TextSecondary)
                        Text(stringResource(R.string.edit), style = DriftType.Link12, color = DriftColors.Lime)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(Fmt.money(summary?.totalBudget ?: 0.0), style = DriftType.Big, color = DriftColors.Text, modifier = Modifier.alignByBaseline())
                        Text(stringResource(R.string.shekel), style = DriftType.Label13, color = DriftColors.TextSecondary, modifier = Modifier.alignByBaseline())
                    }
                    val pct = summary?.percentUsed ?: 0.0
                    BudgetLine((pct / 100).toFloat(), color = if (summary?.isOverBudget == true) DriftColors.Orange else DriftColors.Lime)
                    Text(
                        "${stringResource(R.string.budget_used)} ${Fmt.percent(pct)} · ${stringResource(R.string.budget_spent)} ${Fmt.money(summary?.spent ?: 0.0)} · ${stringResource(R.string.budget_remaining)} ${Fmt.money(summary?.remaining ?: 0.0)}",
                        style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Pace
        item {
            val good = summary?.isGoodPace ?: true
            StatTriplet(
                listOf(
                    Stat(stringResource(R.string.home_per_day), if (summary?.isOverBudget == true) stringResource(R.string.home_exceeded) else stringResource(R.string.amount_with_shekel, Fmt.money(summary?.allowedPerDay ?: 0.0)), DriftColors.Lime),
                    Stat(stringResource(R.string.budget_daily_pace), if (good) stringResource(R.string.home_good_pace) else stringResource(R.string.home_too_fast), if (good) DriftColors.Teal else DriftColors.Orange),
                    Stat(stringResource(R.string.home_this_week), stringResource(R.string.amount_with_shekel, Fmt.money(summary?.weekSpent ?: 0.0))),
                ),
            )
        }

        // Category budgets
        item {
            Column {
                SectionHeader(stringResource(R.string.budget_per_category))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        val spends = summary?.categories?.associateBy { it.categoryKey }.orEmpty()
                        state.categories.forEachIndexed { i, cat ->
                            val spend = spends[cat.id]
                            CategoryBudgetRow(cat, spend, onClick = { editCategory = cat })
                            if (i < state.categories.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }

        // Alerts
        item {
            Column {
                SectionHeader(stringResource(R.string.budget_alerts))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SwitchRow(stringResource(R.string.budget_alert_80), state.settings?.alert80Enabled ?: true, { vm.setAlert80(it) })
                        HairlineDivider()
                        SwitchRow(stringResource(R.string.budget_alert_100), state.settings?.alert100Enabled ?: true, { vm.setAlert100(it) })
                    }
                }
            }
        }

        // History
        item {
            Column {
                SectionHeader(stringResource(R.string.budget_history))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (state.history.isEmpty()) EmptyNote(stringResource(R.string.budget_no_history))
                        state.history.forEachIndexed { i, h ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.monthLabel(context, h.month, withYear = true), style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(R.string.out_of, Fmt.money(h.spent), Fmt.money(h.budget)),
                                        style = DriftType.Label11, color = DriftColors.TextSecondary,
                                    )
                                }
                                Text(
                                    if (h.met) stringResource(R.string.budget_met) else stringResource(R.string.budget_missed, Fmt.money(h.overBy)),
                                    style = DriftType.Label12, color = if (h.met) DriftColors.Teal else DriftColors.Orange, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (i < state.history.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }

    if (editTotal) {
        AmountDialog(
            title = stringResource(R.string.budget_total),
            initial = summary?.totalBudget?.let { Fmt.money(it).replace(",", "") } ?: "",
            note = stringResource(R.string.budget_change_note),
            allowRemove = false,
            onDismiss = { editTotal = false },
            onSave = { v -> if (v != null && v > 0) vm.setTotalBudget(v); editTotal = false },
        )
    }
    editCategory?.let { cat ->
        AmountDialog(
            title = stringResource(R.string.budget_set_category, cat.name),
            initial = cat.monthlyBudget?.let { Fmt.money(Money.toShekels(it)).replace(",", "") } ?: "",
            note = null,
            allowRemove = cat.monthlyBudget != null,
            onDismiss = { editCategory = null },
            onSave = { v -> vm.setCategoryBudget(cat.id, v?.takeIf { it > 0 }); editCategory = null },
        )
    }
}

@Composable
private fun CategoryBudgetRow(cat: CategoryEntity, spend: CategorySpend?, onClick: () -> Unit) {
    val spent = spend?.spent ?: 0.0
    val budget = cat.monthlyBudget?.let { Money.toShekels(it) }
    val pct = spend?.percentOfBudget
    val status = when {
        budget == null -> stringResource(R.string.budget_no_category_budget) to DriftColors.TextDim
        pct != null && pct >= 100 -> stringResource(R.string.budget_status_over) to DriftColors.Orange
        pct != null && pct > 85 -> stringResource(R.string.budget_status_warning) to DriftColors.Orange
        else -> stringResource(R.string.budget_status_ok) to DriftColors.Teal
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cat.name, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(status.first, style = DriftType.Label11, color = status.second)
            Text(
                if (budget != null) stringResource(R.string.out_of, Fmt.money(spent), Fmt.money(budget)) else stringResource(R.string.amount_with_shekel, Fmt.money(spent)),
                style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1,
            )
        }
        val fraction = if (budget != null && budget > 0) (spent / budget).toFloat() else (spend?.shareOfTotal ?: 0.0).toFloat()
        CategoryBar(fraction, if ((pct ?: 0.0) > 85) DriftColors.Orange else cat.composeColor())
        if (budget != null && pct != null) {
            Text("${pct.roundToInt()}%", style = DriftType.Tag10, color = DriftColors.TextDim)
        }
    }
}

/** Numeric amount dialog used for the total and category budgets. onSave(null) = remove. */
@Composable
fun AmountDialog(
    title: String,
    initial: String,
    note: String?,
    allowRemove: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val value = text.replace(",", "").toDoubleOrNull()
    DriftDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.save),
        onConfirm = { onSave(value) },
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = value != null && value > 0,
    ) {
        DriftTextField(text, { text = it }, stringResource(R.string.budget_edit_hint), keyboardType = KeyboardType.Decimal)
        if (note != null) Text(note, style = DriftType.Label11, color = DriftColors.TextSecondary)
        if (allowRemove) {
            Text(
                stringResource(R.string.budget_remove), style = DriftType.Link12, color = DriftColors.Orange,
                modifier = Modifier.clickable { onSave(null) }.padding(vertical = 4.dp),
            )
        }
    }
}
