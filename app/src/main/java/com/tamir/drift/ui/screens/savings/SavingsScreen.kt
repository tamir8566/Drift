package com.tamir.drift.ui.screens.savings

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
import androidx.compose.foundation.lazy.items
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
import com.tamir.drift.data.db.SavingsGoalEntity
import com.tamir.drift.data.repo.Money
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.CategoryBar
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.toFieldText
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import java.time.Instant
import java.time.ZoneId

@Composable
fun SavingsScreen() {
    val graph = LocalAppGraph.current
    val vm = driftViewModel { SavingsViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var depositFor by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var deleting by remember { mutableStateOf<SavingsGoalEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.savings_title), onBack = null) }

        // Monthly potential
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.savings_potential), style = DriftType.Label13, color = DriftColors.TextSecondary)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            Fmt.money(state.potential), style = DriftType.Big,
                            color = if (state.potential < 0) DriftColors.Orange else DriftColors.Lime, modifier = Modifier.alignByBaseline(),
                        )
                        Text(stringResource(R.string.shekel), style = DriftType.Label13, color = DriftColors.TextSecondary, modifier = Modifier.alignByBaseline())
                    }
                    Text(
                        "${stringResource(R.string.savings_potential_desc)} · ${Fmt.money(state.budget)} − ${Fmt.money(state.spent)}",
                        style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Goals
        item { SectionHeader(stringResource(R.string.savings_goals), stringResource(R.string.savings_new_goal), onAction = { showNew = true }) }
        if (state.goals.isEmpty() && !state.loading) item { EmptyNote(stringResource(R.string.savings_no_goals)) }
        items(state.goals, key = { it.goal.id }) { item ->
            GlassCard(Modifier.fillMaxWidth(), radius = 20.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.goal.name, style = DriftType.Title, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val (label, color) = when {
                            item.status.isReached -> stringResource(R.string.savings_reached) to DriftColors.Lime
                            item.status.onTrack -> stringResource(R.string.savings_on_track) to DriftColors.Teal
                            else -> stringResource(R.string.savings_off_track) to DriftColors.Orange
                        }
                        Text(label, style = DriftType.Percent11, color = color)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(Fmt.money(item.status.saved), style = DriftType.Value20, color = DriftColors.Text, modifier = Modifier.alignByBaseline())
                        Text(
                            stringResource(R.string.savings_of_target, Fmt.money(item.status.targetAmount)),
                            style = DriftType.Label12, color = DriftColors.TextSecondary, modifier = Modifier.alignByBaseline(),
                        )
                    }
                    CategoryBar(item.status.progress.toFloat(), DriftColors.Lime)
                    val targetDate = Instant.ofEpochMilli(item.goal.targetDate).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text(
                        "${stringResource(R.string.savings_needed_per_month)}: ${Fmt.money(item.status.neededPerMonth)} ₪ · " +
                            "${stringResource(R.string.savings_months_left, item.status.monthsLeft)} · ${stringResource(R.string.savings_until, Fmt.fullDate(targetDate))}",
                        style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActionText(stringResource(R.string.savings_deposit), DriftColors.Lime) { depositFor = item.goal }
                        ActionText(stringResource(R.string.edit), DriftColors.TextSecondary) { editing = item.goal }
                        ActionText(stringResource(R.string.delete), DriftColors.Orange) { deleting = item.goal }
                    }
                }
            }
        }

        // Past savings
        item {
            Column {
                SectionHeader(stringResource(R.string.savings_history))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (state.past.isEmpty()) EmptyNote(stringResource(R.string.budget_no_history))
                        state.past.forEachIndexed { i, p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Fmt.monthLabel(context, p.month, withYear = true), style = DriftType.RowTitle, color = DriftColors.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.amount_with_shekel, Fmt.money(p.saved)),
                                    style = DriftType.RowAmount, color = if (p.saved >= 0) DriftColors.Teal else DriftColors.Orange,
                                )
                            }
                            if (i < state.past.lastIndex) HairlineDivider()
                        }
                        if (state.past.isNotEmpty()) {
                            Text(stringResource(R.string.savings_history_desc), style = DriftType.Label11, color = DriftColors.TextDim, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }

    if (showNew) {
        GoalDialog(title = stringResource(R.string.savings_new_goal), goal = null, onDismiss = { showNew = false }) { name, target, date ->
            vm.create(name, target, date); showNew = false
        }
    }
    editing?.let { g ->
        GoalDialog(title = stringResource(R.string.edit), goal = g, onDismiss = { editing = null }) { name, target, date ->
            vm.update(g, name, target, date); editing = null
        }
    }
    depositFor?.let { g ->
        var text by remember { mutableStateOf("") }
        val value = text.replace(",", "").toDoubleOrNull()
        DriftDialog(
            title = "${stringResource(R.string.savings_deposit)} · ${g.name}",
            onDismiss = { depositFor = null },
            confirmText = stringResource(R.string.save),
            onConfirm = { if (value != null && value > 0) vm.deposit(g.id, value); depositFor = null },
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = value != null && value > 0,
        ) {
            DriftTextField(text, { text = it }, stringResource(R.string.savings_deposit_hint), keyboardType = KeyboardType.Decimal)
        }
    }
    deleting?.let { g ->
        DriftDialog(
            title = g.name,
            onDismiss = { deleting = null },
            confirmText = stringResource(R.string.delete),
            onConfirm = { vm.delete(g.id); deleting = null },
            dismissText = stringResource(R.string.cancel),
        ) { Text(stringResource(R.string.savings_delete_confirm), style = DriftType.Body, color = DriftColors.TextSecondary) }
    }
}

@Composable
private fun ActionText(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text, style = DriftType.Link12, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
    )
}

@Composable
private fun GoalDialog(title: String, goal: SavingsGoalEntity?, onDismiss: () -> Unit, onSave: (String, Double, Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    var name by remember { mutableStateOf(goal?.name ?: "") }
    var amount by remember { mutableStateOf(goal?.let { Fmt.money(Money.toShekels(it.targetAmount)).replace(",", "") } ?: "") }
    var date by remember {
        mutableStateOf(goal?.let { Instant.ofEpochMilli(it.targetDate).atZone(zone).toLocalDate().toFieldText() } ?: "")
    }
    val amountValue = amount.replace(",", "").toDoubleOrNull()
    val dateValue = parseDate(date)
    val valid = name.isNotBlank() && amountValue != null && amountValue > 0 && dateValue != null
    DriftDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.save),
        onConfirm = {
            if (valid) onSave(name, amountValue!!, dateValue!!.atStartOfDay(zone).toInstant().toEpochMilli())
        },
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = valid,
    ) {
        DriftTextField(name, { name = it }, stringResource(R.string.savings_goal_name))
        DriftTextField(amount, { amount = it }, stringResource(R.string.savings_goal_amount), keyboardType = KeyboardType.Decimal)
        DateField(date, { date = it }, stringResource(R.string.savings_goal_date), errorText = stringResource(R.string.date_invalid))
    }
}
