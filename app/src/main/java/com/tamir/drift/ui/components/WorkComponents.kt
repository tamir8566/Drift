package com.tamir.drift.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamir.drift.R
import com.tamir.drift.domain.format.BarValue
import com.tamir.drift.domain.work.MonthSavingHistory
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Small status pill: 11sp bold text on a 12% tint with a 45% border of the same color. When its
 * text changes ("לא בכיוון" → "בכיוון") the old pill cross-fades into the new one.
 */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    if (!LocalFeedback.current.animations) {
        StatusPillBody(text, color, modifier)
        return
    }
    Crossfade(targetState = text to color, modifier = modifier, animationSpec = tween(Motion.PILL_MS), label = "statusPill") { shown ->
        StatusPillBody(shown.first, shown.second)
    }
}

@Composable
private fun StatusPillBody(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(99.dp)
    Box(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f), shape)
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = DriftType.Percent11, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round 32dp glass button with a line icon (the +/− of a stepper). */
@Composable
fun StepButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(DriftColors.GlassFill, CircleShape)
            .border(1.dp, if (enabled) DriftColors.PlusBorder else DriftColors.GlassBorder, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = DriftColors.Lime),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) DriftColors.Lime else DriftColors.TextDim, modifier = Modifier.size(14.dp))
    }
}

/**
 * Editable target row: label at the start, "− value +" at the end. Tapping the value opens the
 * caller's amount dialog ([onEdit]); the buttons step by a fixed amount, each step with a light
 * tick. [valueDim] draws the value dimmed (a "—" while the real value is still loading).
 */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    valueDim: Boolean = false,
) {
    val feedback = rememberFeedbackActions()
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        StepButton(DriftIcons.Minus, stringResource(R.string.goal_step_decrease), { feedback.tick(); onDecrease() }, enabled = canDecrease)
        Text(
            valueText, style = DriftType.Value20, color = if (valueDim) DriftColors.TextDim else DriftColors.Text, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.width(96.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onEdit).padding(vertical = 2.dp),
        )
        StepButton(DriftIcons.Plus, stringResource(R.string.goal_step_increase), { feedback.tick(); onIncrease() })
    }
}

/**
 * Job card for the 2×2 grid (same shape as the category card): name 12sp secondary, percent of
 * the weekly goal at the end, amount 20sp bold, meta line, bar beneath. [extraText] adds a second
 * small line (the job's travel reimbursement); pass "" to only reserve its height.
 *
 * The percent and the bar come from the one [bar] value (this week's income of the job against
 * the weekly goal), and [referenceText] spells that reference out ("השבוע X מתוך יעד שבועי Y").
 * Above 100% the bar is full and the text keeps the real percent — earning more than the goal is
 * good news, so the bar keeps the job's colour.
 */
@Composable
fun JobCard(
    name: String,
    amountText: String,
    metaText: String,
    bar: BarValue,
    referenceText: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    extraText: String? = null,
) {
    GlassCard(modifier, radius = 20.dp, onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(Fmt.percent(bar), style = DriftType.Percent11, color = DriftColors.TextSecondary, maxLines = 1)
            }
            Text(amountText, style = DriftType.Value20, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metaText, style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (extraText != null) {
                Text(extraText, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(referenceText, style = DriftType.Tag10, color = DriftColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CategoryBar(bar.fraction, color)
        }
    }
}

/**
 * Month grid, 7 columns, Sunday first (at the start edge — the right side in RTL), Hebrew day
 * initials. Worked day = lime dot, planned = thin lime ring, today = lime number.
 * No horizontal scrolling: the cells share the width equally.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    workedDays: Set<Long>,
    plannedDays: Set<Long>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val initials = context.resources.getStringArray(R.array.day_initials)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            // In RTL "previous" sits at the start (right) and points right, "next" at the end points left.
            StepButton(DriftIcons.ChevronRight, stringResource(R.string.work_prev_month), onPrevMonth)
            Text(
                Fmt.monthLabel(context, month, withYear = month.year != today.year),
                style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            StepButton(DriftIcons.ChevronLeft, stringResource(R.string.work_next_month), onNextMonth)
        }
        Row(Modifier.fillMaxWidth()) {
            initials.forEach { d ->
                Text(d, style = DriftType.Label11, color = DriftColors.TextDim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
        // Sunday → offset 0 … Saturday → offset 6 (java.time: Monday=1 … Sunday=7).
        val offset = month.atDay(1).dayOfWeek.value % 7
        val length = month.lengthOfMonth()
        val rows = (offset + length + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNumber = r * 7 + c - offset + 1
                    if (dayNumber < 1 || dayNumber > length) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        val epoch = date.toEpochDay()
                        DayCell(
                            day = dayNumber,
                            worked = epoch in workedDays,
                            planned = epoch in plannedDays,
                            isToday = date == today,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, worked: Boolean, planned: Boolean, isToday: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
        var m = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .clip(CircleShape)
        if (planned) m = m.border(1.dp, DriftColors.Lime, CircleShape)
        m = m.clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = DriftColors.Lime), onClick = onClick)
        Box(m, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    day.toString(), style = DriftType.Label12,
                    color = when {
                        isToday -> DriftColors.Lime
                        worked || planned -> DriftColors.Text
                        else -> DriftColors.TextSecondary
                    },
                    maxLines = 1,
                )
                Box(Modifier.size(5.dp).background(if (worked) DriftColors.Lime else Color.Transparent, CircleShape))
            }
        }
    }
}

/** Legend item: small marker + 11sp label. */
@Composable
fun LegendDot(label: String, color: Color, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ring) Box(Modifier.size(10.dp).border(1.dp, color, CircleShape))
        else Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1)
    }
}

/**
 * 6-month saved-vs-goal bars: lime when the month met the goal, orange otherwise, with a hairline
 * at the goal level. The label above each bar is the same value the bar is drawn from; a month
 * with nothing saved — or a negative one — has no bar and its label says so ("0" / "−350").
 * The columns grow from zero the first time they appear, one shortly after the other.
 */
@Composable
fun SavingHistoryBars(items: List<MonthSavingHistory>, currentMonth: YearMonth, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val barArea = 96
    val max = (items.maxOfOrNull { maxOf(it.savedAgorot, it.goalAgorot) } ?: 1L).coerceAtLeast(1L).toDouble()
    val goalFraction = ((items.firstOrNull()?.goalAgorot ?: 0L).toDouble() / max).coerceIn(0.0, 1.0)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height((barArea + 18).dp)) {
            if (goalFraction > 0.0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.BottomStart)
                        .offset(y = -(barArea * goalFraction.toFloat()).dp)
                        .background(DriftColors.LineTrack),
                )
            }
            Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                items.forEachIndexed { index, m ->
                    // Keyed by month: each bar animates from its own previous height, also when
                    // the list shifts by a month.
                    key(m.month) {
                        val target = BarValue(m.savedAgorot.toDouble(), max).fraction
                        val animated = animatedBarFraction(target, delayMillis = index * Motion.COLUMN_STAGGER_MS)
                        val color = if (m.met) DriftColors.Lime else DriftColors.Orange
                        val isCurrent = m.month == currentMonth
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(
                                Fmt.signedMoneyAgorot(m.savedAgorot), style = DriftType.Tag10,
                                color = if (m.savedAgorot < 0L) DriftColors.Orange else DriftColors.TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((barArea * animated).dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(if (isCurrent) color else color.copy(alpha = 0.45f)),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { m ->
                Text(
                    Fmt.monthShort(context, m.month.monthValue),
                    style = DriftType.Label11,
                    color = if (m.month == currentMonth) DriftColors.Lime else DriftColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A tick box the user marks himself, with a title and one explanatory sentence: a 20dp rounded
 * square (lime border; lime fill with a check when ticked). The whole row is the touch target.
 */
@Composable
fun CheckRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .clip(shape)
                .background(if (checked) DriftColors.Lime else DriftColors.GlassFill, shape)
                .border(1.dp, if (checked) DriftColors.Lime else DriftColors.PlusBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(DriftIcons.Check, null, tint = DriftColors.Background, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}
