package com.tamir.drift.ui.screens.where

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.BAR_ANIM_MS
import com.tamir.drift.ui.components.Chip
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.SegmentedBar
import com.tamir.drift.ui.components.composeColor
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType

@Composable
fun WhereScreen(
    initialCategory: String?,
    onOpenCategoryList: (category: String, month: String?) -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm = driftViewModel(key = "where/${initialCategory ?: ""}") { WhereViewModel(graph, initialCategory) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.where_title), onBack = null) }

        // Period selector
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.period_month), state.period == Period.MONTH, { vm.setPeriod(Period.MONTH) })
                Chip(stringResource(R.string.period_3_months), state.period == Period.THREE_MONTHS, { vm.setPeriod(Period.THREE_MONTHS) })
                Chip(stringResource(R.string.period_year), state.period == Period.YEAR, { vm.setPeriod(Period.YEAR) })
            }
        }

        // Category breakdown
        item {
            Column {
                SectionHeader(stringResource(R.string.where_breakdown))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(Fmt.money(state.total), style = DriftType.Big, color = DriftColors.Text, modifier = Modifier.alignByBaseline())
                            Text(stringResource(R.string.shekel), style = DriftType.Label13, color = DriftColors.TextSecondary, modifier = Modifier.alignByBaseline())
                        }
                        if (state.slices.isEmpty()) {
                            EmptyNote(stringResource(R.string.where_no_data))
                        } else {
                            SegmentedBar(state.slices.map { it.fraction.toFloat() to state.categories[it.categoryId].composeColor() })
                            state.slices.forEachIndexed { i, s ->
                                val cat = state.categories[s.categoryId]
                                val highlighted = state.highlightCategory == s.categoryId
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (highlighted) DriftColors.Lime.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickable {
                                            vm.clearHighlight()
                                            onOpenCategoryList(s.categoryId, if (state.period == Period.MONTH) state.currentMonth.toString() else null)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(Modifier.size(10.dp).background(cat.composeColor(), CircleShape))
                                    Text(cat?.name ?: s.categoryId, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(Fmt.percent(s.fraction * 100), style = DriftType.Label12, color = DriftColors.TextSecondary)
                                    Text(stringResource(R.string.amount_with_shekel, Fmt.money(s.amount)), style = DriftType.RowAmount, color = DriftColors.Text)
                                }
                                if (i < state.slices.lastIndex) HairlineDivider()
                            }
                        }
                    }
                }
            }
        }

        // Month vs month — 6 columns
        item {
            Column {
                SectionHeader(stringResource(R.string.where_month_vs_month))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val max = state.months.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0
                        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                            state.months.forEach { m ->
                                val isCurrent = m.yearMonth == state.currentMonth
                                val target = (m.total / max).toFloat().coerceIn(0.02f, 1f)
                                val animated by animateFloatAsState(target, tween(BAR_ANIM_MS), label = "monthBar")
                                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                    Text(Fmt.money(m.total), style = DriftType.Tag10, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(animated * 0.78f)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(if (isCurrent) DriftColors.Lime else DriftColors.Lime.copy(alpha = 0.35f)),
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.months.forEach { m ->
                                Text(
                                    Fmt.monthShort(context, m.yearMonth.monthValue),
                                    style = DriftType.Label11,
                                    color = if (m.yearMonth == state.currentMonth) DriftColors.Lime else DriftColors.TextSecondary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top merchants
        item {
            Column {
                SectionHeader(stringResource(R.string.where_top_merchants))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (state.topMerchants.isEmpty()) EmptyNote(stringResource(R.string.where_no_data))
                        state.topMerchants.forEachIndexed { i, m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${i + 1}", style = DriftType.Percent11, color = DriftColors.Lime, modifier = Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.merchant, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.purchases_count, m.count), style = DriftType.Label11, color = DriftColors.TextSecondary)
                                }
                                Text(stringResource(R.string.amount_with_shekel, Fmt.money(m.total)), style = DriftType.RowAmount, color = DriftColors.Text)
                            }
                            if (i < state.topMerchants.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }

        // Where to save
        item {
            Column {
                SectionHeader(stringResource(R.string.where_save))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (state.recurring.isEmpty()) EmptyNote(stringResource(R.string.where_no_savings))
                        state.recurring.forEachIndexed { i, r ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.merchant, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(stringResource(R.string.purchases_count, r.count), style = DriftType.Label11, color = DriftColors.TextSecondary)
                                }
                                Text(stringResource(R.string.save_monthly, Fmt.money(r.monthlyTotal), Fmt.money(r.yearlyProjection)), style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (r.onceAWeekMonthlySaving > 0) {
                                    Text(stringResource(R.string.save_once_a_week, Fmt.money(r.onceAWeekMonthlySaving)), style = DriftType.Label12, color = DriftColors.Lime, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (i < state.recurring.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }

        // Subscriptions
        item {
            Column {
                SectionHeader(stringResource(R.string.where_subscriptions))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (state.subscriptions.isEmpty()) EmptyNote(stringResource(R.string.where_no_subscriptions))
                        state.subscriptions.forEachIndexed { i, s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(10.dp).background(if (s.isActive) DriftColors.Teal else DriftColors.TextDim, CircleShape))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(s.merchant, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (!s.isActive) Text(stringResource(R.string.sub_inactive), style = DriftType.Tag10, color = DriftColors.Orange)
                                    }
                                    Text(stringResource(R.string.sub_monthly, Fmt.money(s.amount), Fmt.money(s.yearlyCost)), style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${stringResource(R.string.sub_charges, s.charges)} · ${stringResource(R.string.sub_last_charge, Fmt.shortDate(s.lastCharge))}",
                                        style = DriftType.Label11, color = DriftColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    stringResource(R.string.sub_not_subscription), style = DriftType.Link12, color = DriftColors.Lime,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.notASubscription(s.merchant) }.padding(6.dp),
                                )
                            }
                            if (i < state.subscriptions.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
