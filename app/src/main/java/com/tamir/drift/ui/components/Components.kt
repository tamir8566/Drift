package com.tamir.drift.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamir.drift.domain.format.BarValue
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType

/** Bar fill animation (PRD: 0.6 s). */
const val BAR_ANIM_MS = 600

/**
 * Full-screen background: #050B08 with the two very subtle radial glows from the mockup
 * (lime 14% top-right, teal 8% bottom-left). Drawn in physical coordinates so RTL does not flip it.
 */
@Composable
fun DriftBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(DriftColors.Background)) {
        Canvas(Modifier.fillMaxSize()) {
            val limeRadius = 210.dp.toPx()
            val limeCenter = Offset(size.width + 120.dp.toPx() - limeRadius, -200.dp.toPx() + limeRadius)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to DriftColors.GlowLime, 0.7f to Color.Transparent, 1f to Color.Transparent,
                    center = limeCenter, radius = limeRadius,
                ),
                radius = limeRadius, center = limeCenter,
            )
            val tealRadius = 190.dp.toPx()
            val tealCenter = Offset(-140.dp.toPx() + tealRadius, size.height + 160.dp.toPx() - tealRadius)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to DriftColors.GlowTeal, 0.7f to Color.Transparent, 1f to Color.Transparent,
                    center = tealCenter, radius = tealRadius,
                ),
                radius = tealRadius, center = tealCenter,
            )
        }
        content()
    }
}

/** Glass card: white 3% fill, 1dp white 7% border, radius 20–22. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    borderColor: Color = DriftColors.GlassBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier
        .clip(shape)
        .background(DriftColors.GlassFill, shape)
        .border(1.dp, borderColor, shape)
    if (onClick != null) {
        m = m.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = DriftColors.Lime),
            onClick = onClick,
        )
    }
    Column(m, content = content)
}

/** Round 44dp glass icon button (header). */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, size: Dp = 44.dp, tint: Color = DriftColors.Text) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(DriftColors.GlassFill, CircleShape)
            .border(1.dp, DriftColors.GlassBorder, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = DriftColors.Lime), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * A bar's fraction made safe to draw: 0..1, and 0 for a NaN (0 / 0) so nothing downstream can
 * receive an invalid width.
 */
private fun safeFraction(fraction: Float): Float = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)

/**
 * The budget line: 2dp track (white 12%), lime fill starting at the start edge (right in RTL),
 * a 10dp dot at the end of the fill. No glow (PRD).
 *
 * Pass `bar.fraction` of the same [BarValue] whose percent is printed next to the line: a value
 * above 100% draws a full line while the text keeps the real percent, a negative one draws empty.
 */
@Composable
fun BudgetLine(fraction: Float, modifier: Modifier = Modifier, color: Color = DriftColors.Lime) {
    val target = safeFraction(fraction)
    val animated by animateFloatAsState(target, animationSpec = tween(BAR_ANIM_MS), label = "budgetLine")
    Box(modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(DriftColors.LineTrack))
        Box(Modifier.fillMaxWidth(animated).height(2.dp).background(color))
        // Dot centred on the fill end: measured against the full width.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(constraints.maxWidth, placeable.height) {
                        // placeRelative mirrors x in RTL, so the dot sits at the end of the fill.
                        val x = (constraints.maxWidth * animated).toInt() - placeable.width / 2
                        placeable.placeRelative(x.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0)), 0)
                    }
                }
                .size(10.dp)
                .background(color, CircleShape),
        )
    }
}

/** 3dp category bar with a white 10% track; animated fill. */
@Composable
fun CategoryBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(safeFraction(fraction), animationSpec = tween(BAR_ANIM_MS), label = "categoryBar")
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(DriftColors.BarTrack)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().background(color))
    }
}

/** Segmented bar (category breakdown). Segments are (fraction, color). */
@Composable
fun SegmentedBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Row(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(99.dp)).background(DriftColors.BarTrack)) {
        val shown = segments.filter { it.first > 0f }
        shown.forEachIndexed { i, (f, c) ->
            Box(Modifier.weight(f).fillMaxHeight().background(c))
            if (i < shown.lastIndex) Spacer(Modifier.width(2.dp))
        }
    }
}

/**
 * Category card for the 2×2 grid: name 12sp secondary (ellipsized), percent 11sp bold at the
 * end (orange when > 85% of its budget), amount 20sp bold, what the percent is measured against
 * ([referenceText]: "מתוך 500 ₪" / "מסך ההוצאות"), bar beneath.
 *
 * The percent and the bar come from the one [bar] value, so they cannot disagree: above 100% the
 * bar is full and the text keeps the real percent; below zero the bar is empty.
 */
@Composable
fun CategoryCard(
    name: String,
    amountText: String,
    bar: BarValue,
    referenceText: String,
    color: Color,
    warning: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(modifier, radius = 20.dp, onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(Fmt.percent(bar), style = DriftType.Percent11, color = if (warning) DriftColors.Orange else DriftColors.TextSecondary, maxLines = 1)
            }
            Text(amountText, style = DriftType.Value20, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(referenceText, style = DriftType.Tag10, color = DriftColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CategoryBar(bar.fraction, if (warning) DriftColors.Orange else color)
        }
    }
}

/**
 * Transaction row: 10dp colored dot, name + meta at the start, amount at the end, white 7%
 * divider. Optional lime 10sp tag "עם חברים · שילמתי X". With [onLongClick] a long press on the
 * row runs it (the quick-actions sheet of an expense).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    title: String,
    meta: String,
    amountText: String,
    dotColor: Color,
    sharedTag: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = DriftColors.Text,
    amountColor: Color = DriftColors.Text,
    tagColor: Color = DriftColors.Lime,
    /** A row that does not count ("לא נספר") is drawn dimmed. */
    dimmed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val rowBase: Modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
    val rowModifier: Modifier =
        if (onLongClick != null) rowBase.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        else rowBase.clickable(onClick = onClick)
    Column(rowModifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 10.dp).alpha(if (dimmed) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(10.dp).background(dotColor, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = DriftType.RowTitle, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(amountText, style = DriftType.RowAmount, color = amountColor, maxLines = 1)
                if (sharedTag != null) {
                    Text(sharedTag, style = DriftType.Tag10, color = tagColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DriftColors.Divider))
    }
}

/**
 * One stat: 11sp secondary label above an 18sp bold value. Keep the value short (a bare number,
 * at most one decimal) and put the unit in the label — three of them share one card.
 */
data class Stat(val label: String, val value: String, val color: Color = DriftColors.Text)

/**
 * One line of text that always fits: when it is wider than the space it has, the font size steps
 * down (1sp at a time) to [minFontSize]; only if it still does not fit is it cut with "…".
 * Nothing is drawn until the size is settled, so the steps are never visible.
 */
@Composable
fun AutoShrinkText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 12.sp,
) {
    var fontSize by remember(text, style) { mutableStateOf(style.fontSize) }
    var settled by remember(text, style) { mutableStateOf(false) }
    val atMinimum = fontSize.value <= minFontSize.value
    Text(
        text,
        style = style.copy(fontSize = fontSize),
        color = color,
        maxLines = 1,
        softWrap = false,
        // While measuring, the text is laid out unconstrained so an overflow is detected; at the
        // minimum size whatever is left over is ellipsized.
        overflow = if (atMinimum) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { result ->
            if (result.hasVisualOverflow && !atMinimum) {
                fontSize = (fontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
            } else {
                settled = true
            }
        },
    )
}

/**
 * Glass card with three equal columns separated by white 10% hairlines. A value that is too wide
 * for its column shrinks to fit ([AutoShrinkText]) instead of being clipped.
 */
@Composable
fun StatTriplet(stats: List<Stat>, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), radius = 22.dp) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).height(IntrinsicSizeMin)) {
            stats.forEachIndexed { i, s ->
                Column(Modifier.weight(1f).padding(horizontal = if (i == 0) 0.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(s.label, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AutoShrinkText(s.value, style = DriftType.Value18, color = s.color, modifier = Modifier.fillMaxWidth())
                }
                if (i < stats.lastIndex) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DriftColors.StatDivider))
                }
            }
        }
    }
}

private val IntrinsicSizeMin = androidx.compose.foundation.layout.IntrinsicSize.Min

/** Section header: 13sp/500/letter-spacing 1 secondary title + optional lime 12sp action. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = DriftType.SectionTitle, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) {
            Text(
                action, style = DriftType.Link12, color = DriftColors.Lime,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** Simple top bar for inner screens: back button at the start, title, optional end action. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backDescription: String = "",
    endContent: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) GlassIconButton(DriftIcons.Back, backDescription, onBack)
        Text(title, style = DriftType.Title, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        endContent?.invoke()
    }
}

/** A small pill chip for period / filter selectors. Active = lime border + lime text. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(99.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) DriftColors.Lime.copy(alpha = 0.10f) else DriftColors.GlassFill, shape)
            .border(1.dp, if (selected) DriftColors.PlusBorder else DriftColors.GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DriftType.Label12, color = if (selected) DriftColors.Lime else DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Centered secondary text for empty states. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, style = DriftType.Label13, color = DriftColors.TextDim, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** A 1dp white 7% divider. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(DriftColors.Divider))
}
