package com.tamir.drift.ui.screens.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.R
import com.tamir.drift.capture.CaptureStatus
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.domain.budget.CategorySpend
import com.tamir.drift.domain.format.BarValue
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.BudgetLine
import com.tamir.drift.ui.components.CategoryCard
import com.tamir.drift.ui.components.DriftIcons
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassIconButton
import com.tamir.drift.ui.components.HeroNumber
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.Stat
import com.tamir.drift.ui.components.StatTriplet
import com.tamir.drift.ui.components.StatusPill
import com.tamir.drift.ui.components.TxRow
import com.tamir.drift.ui.components.composeColor
import com.tamir.drift.ui.components.isNewSince
import com.tamir.drift.ui.components.rememberListSeenBefore
import com.tamir.drift.ui.components.rowMotion
import com.tamir.drift.ui.components.wholeShekels
import com.tamir.drift.ui.driftViewModel
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlin.math.abs

/** Home — the mockup in design/Final.dc.html. */
@Composable
fun HomeScreen(
    onOpenCategory: (String) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenAll: () -> Unit,
    onOpenWhere: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUnparsed: () -> Unit,
    onOpenUnsorted: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenGoal: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val vm = driftViewModel { HomeViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val summary = state.summary
    // Expenses that arrived since this list was last looked at land with a short tint.
    val recentSeenBefore = rememberListSeenBefore(SettingsStore.LIST_HOME_RECENT)

    // Capture watchdog: the two permissions automatic capture needs, re-read on every return to the app.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val listenerOn = remember(refresh) { CaptureStatus.listenerEnabled(context) }
    val smsMissing = remember(refresh, state.smsCaptureConfigured) { state.smsCaptureConfigured && !CaptureStatus.smsGranted(context) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        // Denied for good → the only way left is the app's page in the system settings.
        if (!granted) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Header: "נובמבר · יום 8/30" + search & settings glass buttons
        item(key = "header") {
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

        // Attention strip: quiet rows, only when something needs the user — what was not parsed,
        // capture that is off, merchants that still wait for a category, and the state of the backup.
        val unparsed = state.unparsedCount
        val unsorted = state.unsortedMerchants
        val backupFailed = state.backupFailed
        val backupNudge = state.backupNudge && !backupFailed
        if (unparsed > 0 || !listenerOn || smsMissing || unsorted > 0 || backupFailed || backupNudge) {
            item(key = "attention") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (unparsed > 0) {
                        AttentionRow(
                            text = if (unparsed == 1) stringResource(R.string.home_unparsed_one) else stringResource(R.string.home_unparsed_many, unparsed),
                            action = stringResource(R.string.home_unparsed_action),
                            onClick = onOpenUnparsed,
                        )
                    }
                    if (!listenerOn) {
                        AttentionRow(
                            text = stringResource(R.string.home_capture_off),
                            action = stringResource(R.string.home_capture_action),
                            onClick = { runCatching { context.startActivity(CaptureStatus.listenerSettingsIntent()) } },
                        )
                    } else if (smsMissing) {
                        AttentionRow(
                            text = stringResource(R.string.home_sms_off),
                            action = stringResource(R.string.home_capture_action),
                            onClick = { smsLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                        )
                    }
                    if (unsorted > 0) {
                        AttentionRow(
                            text = if (unsorted == 1) stringResource(R.string.home_unsorted_one) else stringResource(R.string.home_unsorted_many, unsorted),
                            action = stringResource(R.string.home_unsorted_action),
                            onClick = onOpenUnsorted,
                        )
                    }
                    if (backupFailed) {
                        // The folder is gone or its permission was revoked: settings → "גיבוי" fixes it.
                        AttentionRow(
                            text = stringResource(R.string.home_backup_failed),
                            action = stringResource(R.string.home_backup_failed_action),
                            onClick = onOpenSettings,
                        )
                    } else if (backupNudge) {
                        AttentionRow(
                            text = stringResource(R.string.home_backup_none),
                            action = stringResource(R.string.home_backup_none_action),
                            onClick = onOpenSettings,
                            onDismiss = { vm.dismissBackupNudge() },
                        )
                    }
                }
            }
        }

        // Hero: remaining + budget line
        item(key = "hero") {
            val remaining = summary?.remaining ?: 0.0
            val over = remaining < 0
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenBudget),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(if (over) R.string.home_over_label else R.string.home_remaining_label),
                    style = DriftType.Label13, color = DriftColors.TextSecondary,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // A dim "—" until the month is loaded; later changes roll from the old number.
                    HeroNumber(
                        value = summary?.let { wholeShekels(it.remaining) },
                        color = if (over) DriftColors.Orange else DriftColors.Text,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        stringResource(R.string.shekel), style = DriftType.HeroCurrency, color = DriftColors.TextSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                // The whole hero opens the budget screen; this small link says so.
                Text(stringResource(R.string.home_edit_budget), style = DriftType.Label11, color = DriftColors.Lime, maxLines = 1)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // One value for the line and for the percent next to it.
                    val bar = summary?.bar ?: BarValue.EMPTY
                    BudgetLine(
                        fraction = bar.fraction,
                        color = if (over || bar.isOver) DriftColors.Orange else DriftColors.Lime,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (summary == null) "—"
                        else "${Fmt.percent(bar)} · ${stringResource(R.string.out_of, Fmt.signedMoney(summary.spent), Fmt.money(summary.totalBudget))}",
                        style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                // "On track" with zero taps: the pace word of the budget screen, under the line.
                if (summary != null) {
                    val goodPace = summary.isGoodPace
                    StatusPill(
                        stringResource(if (goodPace) R.string.home_good_pace else R.string.home_too_fast),
                        if (goodPace) DriftColors.Teal else DriftColors.Orange,
                    )
                }
            }
        }

        // Three stats: per day · this week · vs previous month
        item(key = "stats") {
            val perDay = summary?.allowedPerDay ?: 0.0
            val perDayText = if (summary?.isOverBudget == true) stringResource(R.string.home_exceeded)
            else stringResource(R.string.amount_with_shekel, Fmt.money(perDay))
            val weekText = stringResource(R.string.amount_with_shekel, Fmt.signedMoney(summary?.weekSpent ?: 0.0))
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

        // PRD 8 Nov: "הרווחת החודש X ₪ · נחסכו Y ₪" → goal screen
        item(key = "earned") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenGoal).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).background(if (state.savedThisMonth >= 0) DriftColors.Lime else DriftColors.Orange, CircleShape))
                Text(
                    // A negative saving is said in words ("בחוסר X ₪"), not with a minus sign.
                    if (state.savedThisMonth >= 0) stringResource(R.string.home_earned_saved, Fmt.money(state.earnedThisMonth), Fmt.money(state.savedThisMonth))
                    else stringResource(R.string.home_earned_short, Fmt.money(state.earnedThisMonth), Fmt.money(abs(state.savedThisMonth))),
                    style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // The goal screen's status, readable without opening it (the row still opens the goal).
                val onTrack = state.goalOnTrack
                if (onTrack != null) {
                    StatusPill(
                        stringResource(if (onTrack) R.string.goal_on_track else R.string.goal_off_track),
                        if (onTrack) DriftColors.Lime else DriftColors.Orange,
                    )
                } else {
                    Text(stringResource(R.string.nav_goal), style = DriftType.Link12, color = DriftColors.Lime)
                }
            }
        }

        // Category grid 2×2
        item(key = "categories") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionHeader(stringResource(R.string.home_by_category), stringResource(R.string.all), onAction = onOpenWhere)
                CategoryGrid(state.topCategories, onOpenCategory = onOpenCategory, names = { state.categories[it]?.name ?: it }, colors = { state.categories[it].composeColor() })
            }
        }

        // Recent transactions
        item(key = "recent-header") {
            SectionHeader(stringResource(R.string.home_recent), stringResource(R.string.all), onAction = onOpenAll, modifier = Modifier.padding(bottom = 0.dp))
        }
        if (state.recent.isEmpty() && !state.loading) {
            item(key = "recent-empty") { EmptyNote(stringResource(R.string.empty_list)) }
        }
        items(state.recent, key = { it.id }) { tx ->
            TxRow(tx, state.categories, onClick = { onOpenTransaction(tx.id) }, modifier = rowMotion(isNewSince(tx.createdAt, recentSeenBefore)))
        }
        item(key = "end") { Spacer(Modifier.height(4.dp)) }
    }
}

/**
 * One quiet line of the attention strip: an orange dot, what is wrong, and the lime action that
 * fixes it. With [onDismiss] the line also carries a small close mark that hides it for a while.
 */
@Composable
private fun AttentionRow(text: String, action: String, onClick: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(DriftColors.Orange, CircleShape))
        Text(text, style = DriftType.Label12, color = DriftColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(action, style = DriftType.Link12, color = DriftColors.Lime, maxLines = 1)
        if (onDismiss != null) {
            Icon(
                DriftIcons.Close, stringResource(R.string.home_attention_dismiss), tint = DriftColors.TextDim,
                modifier = Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(7.dp),
            )
        }
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
                    // Keyed by category: when the order changes, a bar animates from its own
                    // previous value and not from the category that stood in this cell before.
                    key(c.categoryKey) {
                        val budget = c.budget
                        CategoryCard(
                            name = names(c.categoryKey),
                            amountText = stringResource(R.string.amount_with_shekel, Fmt.signedMoney(c.spent)),
                            bar = c.bar,
                            referenceText = if (c.hasBudget && budget != null) stringResource(R.string.savings_of_target, Fmt.money(budget))
                            else stringResource(R.string.category_share_of_total),
                            color = colors(c.categoryKey),
                            warning = c.isWarning || (c.hasBudget && c.bar.isOver),
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCategory(c.categoryKey) },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
