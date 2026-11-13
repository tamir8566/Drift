package com.tamir.drift.ui.screens.goal

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
import androidx.compose.ui.graphics.Color
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
import com.tamir.drift.ui.components.BudgetLine
import com.tamir.drift.ui.components.CategoryBar
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.SavingHistoryBars
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.StatusPill
import com.tamir.drift.ui.components.StepperRow
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.toFieldText
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.screens.budget.AmountDialog
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

private const val MONTHLY_STEP = 100_00L
private const val WEEKLY_STEP = 50_00L

/** PRD "מסך יעד" — replaces the old savings tab and absorbs the long-term savings goals. */
@Composable
fun GoalScreen() {
    val graph = LocalAppGraph.current
    val vm = driftViewModel { GoalViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val status = state.status
    val week = state.week

    var editMonthly by remember { mutableStateOf(false) }
    var editWeekly by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var depositFor by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var deleting by remember { mutableStateOf<SavingsGoalEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.goal_title), onBack = null) }

        // Hero: saved this month + % of goal line + status pill + shortfall
        item {
            val saved = status?.savedAgorot ?: 0L
            val negative = saved < 0L
            val onTrack = status?.onTrack ?: true
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.goal_saved_month), style = DriftType.Label13, color = DriftColors.TextSecondary)
                    if (status != null) {
                        StatusPill(
                            stringResource(if (onTrack) R.string.goal_on_track else R.string.goal_off_track),
                            if (onTrack) DriftColors.Lime else DriftColors.Orange,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        (if (negative) "−" else "") + Fmt.moneyAgorot(abs(saved)),
                        style = DriftType.Hero, color = if (negative) DriftColors.Orange else DriftColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline(),
                    )
                    Text(stringResource(R.string.shekel), style = DriftType.HeroCurrency, color = DriftColors.TextSecondary, modifier = Modifier.alignByBaseline())
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pct = status?.percentOfGoal ?: 0.0
                    BudgetLine(
                        fraction = (pct / 100.0).toFloat(),
                        color = if (onTrack) DriftColors.Lime else DriftColors.Orange,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${Fmt.percent(pct.coerceAtLeast(0.0))} · ${stringResource(R.string.out_of, Fmt.moneyAgorot(saved), Fmt.moneyAgorot(state.monthlyGoalAgorot))}",
                        style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (status != null) {
                    Text(
                        stringResource(R.string.goal_earned_spent, Fmt.moneyAgorot(status.earnedAgorot), Fmt.moneyAgorot(status.spentAgorot)),
                        style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        status.isGoalMet -> Text(stringResource(R.string.goal_met), style = DriftType.Label12, color = DriftColors.Lime)
                        !status.onTrack -> Text(
                            if (status.mainJobIsDaily) stringResource(R.string.goal_shortfall_days, Fmt.moneyAgorot(status.shortfallAgorot), status.shortfallDays)
                            else stringResource(R.string.goal_shortfall, Fmt.moneyAgorot(status.shortfallAgorot), status.shortfallHours),
                            style = DriftType.Label12, color = DriftColors.Orange, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Week card
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.goal_week_income), style = DriftType.Label13, color = DriftColors.TextSecondary)
                        if (week != null) {
                            Text(
                                "${Fmt.shortDate(week.range.start)}–${Fmt.shortDate(week.range.end)}",
                                style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 1,
                            )
                        }
                    }
                    val income = week?.incomeAgorot ?: 0L
                    val goal = week?.goalAgorot ?: state.weeklyGoalAgorot
                    val surplus = week?.isSurplus ?: false
                    val deficit = week?.isDeficit ?: false
                    val weekColor = when {
                        deficit -> DriftColors.Orange
                        surplus -> DriftColors.Lime
                        else -> DriftColors.Text
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(Fmt.moneyAgorot(income), style = DriftType.Big, color = weekColor, modifier = Modifier.alignByBaseline())
                        Text(
                            stringResource(R.string.savings_of_target, Fmt.moneyAgorot(goal)),
                            style = DriftType.Label13, color = DriftColors.TextSecondary, modifier = Modifier.alignByBaseline(),
                        )
                    }
                    CategoryBar((week?.progress ?: 0.0).toFloat(), if (deficit) DriftColors.Orange else DriftColors.Lime)
                    if (week != null) {
                        if (week.carryInAgorot != 0L) {
                            Text(
                                if (week.carryInAgorot > 0L) stringResource(R.string.goal_week_carry_plus, Fmt.moneyAgorot(week.carryInAgorot))
                                else stringResource(R.string.goal_week_carry_minus, Fmt.moneyAgorot(-week.carryInAgorot)),
                                style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val balance = week.balanceAgorot
                        Text(
                            when {
                                balance > 0L -> stringResource(R.string.goal_week_surplus, Fmt.moneyAgorot(balance))
                                balance < 0L -> stringResource(R.string.goal_week_deficit, Fmt.moneyAgorot(-balance))
                                else -> stringResource(R.string.goal_week_even)
                            },
                            style = DriftType.Label12, color = weekColor, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(stringResource(R.string.goal_week_reset_note), style = DriftType.Tag10, color = DriftColors.TextDim, maxLines = 1)
                    }
                }
            }
        }

        // Recommendation line
        if (status != null && !status.isGoalMet) {
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 16.dp, borderColor = DriftColors.PlusBorder) {
                    Text(
                        if (status.mainJobIsDaily) stringResource(R.string.goal_recommendation_days, status.remainingDays, Fmt.moneyAgorot(status.remainingToGoalAgorot))
                        else stringResource(R.string.goal_recommendation, status.remainingHours, Fmt.moneyAgorot(status.remainingToGoalAgorot)),
                        style = DriftType.Label13, color = DriftColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }

        // Editable targets
        item {
            Column {
                SectionHeader(stringResource(R.string.goal_targets))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        StepperRow(
                            label = stringResource(R.string.goal_target_monthly),
                            valueText = Fmt.moneyAgorot(state.monthlyGoalAgorot),
                            onDecrease = { vm.setMonthlyGoal((state.monthlyGoalAgorot - MONTHLY_STEP).coerceAtLeast(0L)) },
                            onIncrease = { vm.setMonthlyGoal(state.monthlyGoalAgorot + MONTHLY_STEP) },
                            onEdit = { editMonthly = true },
                            canDecrease = state.monthlyGoalAgorot > 0L,
                        )
                        HairlineDivider()
                        StepperRow(
                            label = stringResource(R.string.goal_target_weekly),
                            valueText = Fmt.moneyAgorot(state.weeklyGoalAgorot),
                            onDecrease = { vm.setWeeklyGoal((state.weeklyGoalAgorot - WEEKLY_STEP).coerceAtLeast(0L)) },
                            onIncrease = { vm.setWeeklyGoal(state.weeklyGoalAgorot + WEEKLY_STEP) },
                            onEdit = { editWeekly = true },
                            canDecrease = state.weeklyGoalAgorot > 0L,
                        )
                    }
                }
            }
        }

        // 6-month history
        item {
            Column {
                SectionHeader(stringResource(R.string.goal_history))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.history.isEmpty()) EmptyNote(stringResource(R.string.budget_no_history))
                        else SavingHistoryBars(state.history, state.month)
                        Text(stringResource(R.string.goal_history_desc), style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Long-term savings goals (moved from the savings screen)
        item { SectionHeader(stringResource(R.string.goal_long_term), stringResource(R.string.savings_new_goal), onAction = { showNew = true }) }
        if (state.longTerm.isEmpty() && !state.loading) item { EmptyNote(stringResource(R.string.savings_no_goals)) }
        items(state.longTerm, key = { it.goal.id }) { item ->
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
                    val projected = item.projectedMonth
                    Text(
                        when {
                            item.status.isReached -> stringResource(R.string.savings_reached)
                            projected != null -> stringResource(R.string.goal_projection, Fmt.monthLabel(context, projected, withYear = true))
                            else -> stringResource(R.string.goal_projection_none)
                        },
                        style = DriftType.Label12,
                        color = if (projected != null || item.status.isReached) DriftColors.Lime else DriftColors.Orange,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActionText(stringResource(R.string.savings_deposit), DriftColors.Lime) { depositFor = item.goal }
                        ActionText(stringResource(R.string.edit), DriftColors.TextSecondary) { editing = item.goal }
                        ActionText(stringResource(R.string.delete), DriftColors.Orange) { deleting = item.goal }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }

    if (editMonthly) {
        AmountDialog(
            title = stringResource(R.string.goal_target_monthly),
            initial = Fmt.moneyAgorot(state.monthlyGoalAgorot).replace(",", ""),
            note = null,
            allowRemove = false,
            onDismiss = { editMonthly = false },
            onSave = { v -> if (v != null && v > 0) vm.setMonthlyGoal(Money.toAgorot(v)); editMonthly = false },
        )
    }
    if (editWeekly) {
        AmountDialog(
            title = stringResource(R.string.goal_target_weekly),
            initial = Fmt.moneyAgorot(state.weeklyGoalAgorot).replace(",", ""),
            note = null,
            allowRemove = false,
            onDismiss = { editWeekly = false },
            onSave = { v -> if (v != null && v > 0) vm.setWeeklyGoal(Money.toAgorot(v)); editWeekly = false },
        )
    }
    if (showNew) {
        LongTermGoalDialog(title = stringResource(R.string.savings_new_goal), goal = null, onDismiss = { showNew = false }) { name, target, date ->
            vm.create(name, target, date); showNew = false
        }
    }
    editing?.let { g ->
        LongTermGoalDialog(title = stringResource(R.string.edit), goal = g, onDismiss = { editing = null }) { name, target, date ->
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
private fun ActionText(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, style = DriftType.Link12, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
    )
}

@Composable
private fun LongTermGoalDialog(title: String, goal: SavingsGoalEntity?, onDismiss: () -> Unit, onSave: (String, Double, Long) -> Unit) {
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
