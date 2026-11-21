package com.tamir.drift.ui.screens.goal

import android.os.SystemClock
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.data.db.SavingsGoalEntity
import com.tamir.drift.data.repo.Money
import com.tamir.drift.domain.format.BarValue
import com.tamir.drift.domain.money.AmountInput
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.BAR_ANIM_MS
import com.tamir.drift.ui.components.BudgetLine
import com.tamir.drift.ui.components.CategoryBar
import com.tamir.drift.ui.components.DateField
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.FocusOnOpen
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.HeroNumber
import com.tamir.drift.ui.components.LocalFeedback
import com.tamir.drift.ui.components.SavingHistoryBars
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.StatusPill
import com.tamir.drift.ui.components.StepperRow
import com.tamir.drift.ui.components.parseDate
import com.tamir.drift.ui.components.rememberFeedbackActions
import com.tamir.drift.ui.components.toFieldText
import com.tamir.drift.ui.components.wholeShekels
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.screens.budget.AmountDialog
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

private const val MONTHLY_STEP = 100_00L
private const val WEEKLY_STEP = 50_00L

/**
 * PRD "מסך יעד" — replaces the old savings tab and absorbs the long-term savings goals.
 *
 * Two quiet moments, each played once and remembered in the settings: when the month reaches its
 * saving goal a single lime ring opens from the dot of the goal line (with a short vibration and
 * the "goal" sound, each behind its switch); when the week moves into surplus the same ring opens
 * at the end of the week bar. The status pill cross-fades by itself when its text changes.
 */
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

    val feedback = rememberFeedbackActions()
    val animations = LocalFeedback.current.animations
    // When each moment was triggered (uptime millis; 0 = not in this visit) — see pulseProgress.
    var goalPulseAt by remember { mutableLongStateOf(0L) }
    var weekPulseAt by remember { mutableLongStateOf(0L) }
    val goalMet = status?.isGoalMet == true
    val monthKey = state.month.toString()
    LaunchedEffect(goalMet, monthKey) {
        if (goalMet) {
            // The line first finishes growing, so the ring opens from a dot that stands still.
            if (animations) delay(BAR_ANIM_MS.toLong())
            if (graph.settings.markGoalCelebrated(monthKey)) {
                goalPulseAt = SystemClock.uptimeMillis()
                feedback.goalReached()
            }
        }
    }
    val surplusWeekStart: Long? = week?.takeIf { it.isSurplus && it.goalAgorot > 0L }?.range?.start?.toEpochDay()
    LaunchedEffect(surplusWeekStart) {
        if (surplusWeekStart != null) {
            if (animations) delay(BAR_ANIM_MS.toLong())
            if (graph.settings.markWeekCelebrated(surplusWeekStart)) weekPulseAt = SystemClock.uptimeMillis()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header") { ScreenHeader(stringResource(R.string.goal_title), onBack = null) }

        // Hero: saved this month + % of goal line + status pill + shortfall
        item(key = "hero") {
            val saved = status?.savedAgorot ?: 0L
            val negative = saved < 0L
            val onTrack = status?.onTrack ?: true
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // A negative month is said in words — "בחוסר החודש" over the amount — not with a minus sign.
                    Text(stringResource(if (negative) R.string.goal_short_month else R.string.goal_saved_month), style = DriftType.Label13, color = DriftColors.TextSecondary)
                    if (status != null) {
                        StatusPill(
                            stringResource(if (onTrack) R.string.goal_on_track else R.string.goal_off_track),
                            if (onTrack) DriftColors.Lime else DriftColors.Orange,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // A dim "—" until the month is loaded; later changes roll from the old number.
                    HeroNumber(
                        value = status?.let { wholeShekels(Money.toShekels(it.savedAgorot)) },
                        color = if (negative) DriftColors.Orange else DriftColors.Text,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(stringResource(R.string.shekel), style = DriftType.HeroCurrency, color = DriftColors.TextSecondary, modifier = Modifier.alignByBaseline())
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // One value for the line and for the text next to it. Below zero the line is
                    // empty and the text says "בחוסר"; above the goal the line is full and the
                    // text keeps the real percent.
                    val bar = status?.bar ?: BarValue.EMPTY
                    BudgetLine(
                        fraction = bar.fraction,
                        color = if (onTrack) DriftColors.Lime else DriftColors.Orange,
                        modifier = Modifier.weight(1f),
                        pulseAtMillis = goalPulseAt,
                    )
                    Text(
                        if (status == null) "—"
                        else if (negative) stringResource(R.string.goal_short_of_goal, Fmt.moneyAgorot(abs(saved)), Fmt.moneyAgorot(state.monthlyGoalAgorot))
                        else "${Fmt.percent(bar)} · ${stringResource(R.string.out_of, Fmt.moneyAgorot(saved), Fmt.moneyAgorot(state.monthlyGoalAgorot))}",
                        style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (status != null) {
                    Text(
                        stringResource(R.string.goal_earned_spent, Fmt.moneyAgorot(status.earnedAgorot), Fmt.signedMoneyAgorot(status.spentAgorot)),
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
        item(key = "week") {
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
                    // The card speaks about one goal only: this week's goal after the carry-over from
                    // the earlier weeks of the month. The "מתוך", the bar, its percent and the
                    // surplus / deficit line below all refer to it.
                    val income = week?.incomeAgorot ?: 0L
                    val goal = week?.adjustedGoalAgorot ?: state.weeklyGoalAgorot
                    val bar = week?.bar ?: BarValue.EMPTY
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
                    CategoryBar(bar.fraction, if (deficit) DriftColors.Orange else DriftColors.Lime, pulseAtMillis = weekPulseAt)
                    if (week != null) {
                        Text(
                            when {
                                week.isCoveredByCarry -> stringResource(R.string.goal_week_covered)
                                week.hasCarry -> stringResource(R.string.goal_week_pct_adjusted, Fmt.percent(bar))
                                else -> stringResource(R.string.goal_week_pct_plain, Fmt.percent(bar))
                            },
                            style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (week.hasCarry) {
                            // How the goal above was reached: the weekly goal, plus what was missing / minus the surplus.
                            Text(
                                if (week.carryInAgorot > 0L) {
                                    stringResource(R.string.goal_week_carry_plus, Fmt.moneyAgorot(week.goalAgorot), Fmt.moneyAgorot(week.carryInAgorot), Fmt.moneyAgorot(week.adjustedGoalAgorot))
                                } else {
                                    stringResource(R.string.goal_week_carry_minus, Fmt.moneyAgorot(week.goalAgorot), Fmt.moneyAgorot(-week.carryInAgorot), Fmt.moneyAgorot(week.adjustedGoalAgorot))
                                },
                                style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
            item(key = "recommendation") {
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
        item(key = "targets") {
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
        item(key = "history") {
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
        item(key = "long-term-header") { SectionHeader(stringResource(R.string.goal_long_term), stringResource(R.string.savings_new_goal), onAction = { showNew = true }) }
        if (state.longTerm.isEmpty() && !state.loading) item(key = "long-term-empty") { EmptyNote(stringResource(R.string.savings_no_goals)) }
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
        item(key = "end") { Spacer(Modifier.height(4.dp)) }
    }

    if (editMonthly) {
        AmountDialog(
            title = stringResource(R.string.goal_target_monthly),
            initial = Fmt.fieldAmount(state.monthlyGoalAgorot),
            note = null,
            allowRemove = false,
            onDismiss = { editMonthly = false },
            onSave = { v -> if (v != null && v > 0) vm.setMonthlyGoal(Money.toAgorot(v)); editMonthly = false },
        )
    }
    if (editWeekly) {
        AmountDialog(
            title = stringResource(R.string.goal_target_weekly),
            initial = Fmt.fieldAmount(state.weeklyGoalAgorot),
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
        val value = AmountInput.parse(text)
        val depositFocus = remember { FocusRequester() }
        val confirmDeposit: () -> Unit = {
            if (value != null && value > 0) {
                feedback.saved()
                vm.deposit(g.id, value)
                depositFor = null
            }
        }
        DriftDialog(
            title = "${stringResource(R.string.savings_deposit)} · ${g.name}",
            onDismiss = { depositFor = null },
            confirmText = stringResource(R.string.save),
            onConfirm = confirmDeposit,
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = value != null && value > 0,
        ) {
            DriftTextField(
                text, { text = it }, stringResource(R.string.savings_deposit_hint), keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done, onImeAction = confirmDeposit, focusRequester = depositFocus,
            )
            FocusOnOpen(depositFocus)
        }
    }
    deleting?.let { g ->
        DriftDialog(
            title = g.name,
            onDismiss = { deleting = null },
            confirmText = stringResource(R.string.delete),
            onConfirm = {
                feedback.deleted()
                vm.delete(g.id)
                deleting = null
            },
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
    var amount by remember { mutableStateOf(goal?.let { Fmt.fieldAmount(it.targetAmount) } ?: "") }
    var date by remember {
        mutableStateOf(goal?.let { Instant.ofEpochMilli(it.targetDate).atZone(zone).toLocalDate().toFieldText() } ?: "")
    }
    val amountValue = AmountInput.parse(amount)
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
        DriftTextField(name, { name = it }, stringResource(R.string.savings_goal_name), imeAction = ImeAction.Next)
        DriftTextField(amount, { amount = it }, stringResource(R.string.savings_goal_amount), keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
        // A target date lies ahead: the calendar button is offered, "היום · אתמול · שלשום" are not.
        DateField(date, { date = it }, stringResource(R.string.savings_goal_date), errorText = stringResource(R.string.date_invalid), quickDays = false)
    }
}
