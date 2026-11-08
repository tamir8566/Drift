package com.tamir.drift.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as SysSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamir.drift.BuildConfig
import com.tamir.drift.R
import com.tamir.drift.data.db.Origin
import com.tamir.drift.data.settings.Settings
import com.tamir.drift.notify.WeeklySummaryScheduler
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.ChoiceRow
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.components.DriftTextField
import com.tamir.drift.ui.components.EmptyNote
import com.tamir.drift.ui.components.GlassCard
import com.tamir.drift.ui.components.HairlineDivider
import com.tamir.drift.ui.components.ScreenHeader
import com.tamir.drift.ui.components.SectionHeader
import com.tamir.drift.ui.components.SwitchRow
import com.tamir.drift.ui.format.Fmt
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import com.tamir.drift.update.LocalUpdateController
import com.tamir.drift.update.UpdateChecker
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** PRD "הגדרות". */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenCalibration: () -> Unit, onOpenDebug: () -> Unit, onOpenAll: () -> Unit) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val rules by graph.categories.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val updateController = LocalUpdateController.current

    // Permission states refresh when returning from system settings.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notifAccess = remember(refresh) { NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) }
    val smsGranted = remember(refresh) { ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED }
    val postGranted = remember(refresh) { Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val installGranted = remember(refresh) { graph.updateChecker.canInstall() }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        // Permanently denied → the only way is the app's system settings page.
        if (!granted) context.startActivitySafely(appDetailsIntent(context))
    }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    var editSenders by remember { mutableStateOf(false) }
    var editPackages by remember { mutableStateOf(false) }
    var editWeekly by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.settings_title), onBack = onBack, backDescription = stringResource(R.string.nav_back)) }

        // Permissions
        item {
            Column {
                SectionHeader(stringResource(R.string.settings_permissions))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        PermissionRow(
                            stringResource(R.string.settings_notification_access), stringResource(R.string.settings_notification_access_desc), notifAccess,
                        ) { context.startActivity(Intent(SysSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        HairlineDivider()
                        PermissionRow(stringResource(R.string.settings_sms_permission), stringResource(R.string.settings_sms_permission_desc), smsGranted) {
                            if (smsGranted) context.startActivitySafely(appDetailsIntent(context))
                            else smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        }
                        HairlineDivider()
                        PermissionRow(stringResource(R.string.settings_post_notifications), stringResource(R.string.settings_post_notifications_desc), postGranted) {
                            if (Build.VERSION.SDK_INT >= 33) postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else context.startActivitySafely(appDetailsIntent(context))
                        }
                        HairlineDivider()
                        PermissionRow(stringResource(R.string.settings_install_permission), stringResource(R.string.settings_install_permission_desc), installGranted) {
                            context.startActivitySafely(graph.updateChecker.unknownSourcesIntent())
                        }
                    }
                }
            }
        }

        // Capture settings
        item {
            Column {
                SectionHeader(stringResource(R.string.settings_capture))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        NavRow(stringResource(R.string.settings_sms_senders), settings?.smsSenders?.joinToString(", ") ?: "") { editSenders = true }
                        HairlineDivider()
                        NavRow(stringResource(R.string.settings_packages), settings?.notificationPackages?.joinToString(", ") ?: "") { editPackages = true }
                    }
                }
            }
        }

        // Weekly summary
        item {
            Column {
                SectionHeader(stringResource(R.string.settings_weekly))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SwitchRow(stringResource(R.string.settings_weekly_enabled), settings?.weeklySummaryEnabled ?: true, { v ->
                            scope.launch {
                                graph.settings.setWeeklyEnabled(v)
                                WeeklySummaryScheduler.schedule(context, graph.settings.current())
                            }
                        })
                        HairlineDivider()
                        val s = settings
                        val value = if (s != null) "${Fmt.dayName(context, DayOfWeek.of(s.weeklySummaryDay))} · ${Fmt.clock(s.weeklySummaryHour, s.weeklySummaryMinute)}" else ""
                        NavRow(stringResource(R.string.settings_weekly_desc), value) { editWeekly = true }
                    }
                }
            }
        }

        // Tools
        item {
            Column {
                SectionHeader(stringResource(R.string.settings_tools))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        NavRow(stringResource(R.string.settings_calibration), stringResource(R.string.settings_calibration_desc), onOpenCalibration)
                        HairlineDivider()
                        NavRow(stringResource(R.string.settings_debug_log), stringResource(R.string.settings_debug_log_desc), onOpenDebug)
                        HairlineDivider()
                        NavRow(stringResource(R.string.settings_all_transactions), stringResource(R.string.tx_export_csv), onOpenAll)
                    }
                }
            }
        }

        // Learned rules
        item {
            Column {
                SectionHeader(stringResource(R.string.settings_learned_rules))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val learned = rules.filter { it.origin == Origin.LEARNED }
                        if (learned.isEmpty()) EmptyNote(stringResource(R.string.settings_no_learned_rules))
                        learned.forEachIndexed { i, r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(r.keyword, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.settings_rule_arrow), style = DriftType.Label12, color = DriftColors.TextDim)
                                Text(categoryName(r.categoryId), style = DriftType.Label12, color = DriftColors.Lime)
                                Text(
                                    stringResource(R.string.delete), style = DriftType.Link12, color = DriftColors.Orange,
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { scope.launch { graph.db.merchantRules().delete(r.keyword) } }.padding(4.dp),
                                )
                            }
                            if (i < learned.lastIndex) HairlineDivider()
                        }
                    }
                }
            }
        }

        // Update + version
        item {
            Column {
                SectionHeader(stringResource(R.string.settings_update))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        NavRow(
                            stringResource(R.string.settings_check_update),
                            updateStatus ?: stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        ) {
                            updateStatus = context.getString(R.string.settings_checking)
                            scope.launch {
                                when (val r = graph.updateChecker.check()) {
                                    is UpdateChecker.Result.Available -> { updateStatus = null; updateController.show(r.manifest) }
                                    UpdateChecker.Result.UpToDate -> updateStatus = context.getString(R.string.settings_up_to_date)
                                    is UpdateChecker.Result.Error -> updateStatus = context.getString(R.string.settings_update_error, r.message)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    val s = settings
    if (editSenders && s != null) {
        ListEditDialog(stringResource(R.string.settings_sms_senders), stringResource(R.string.settings_sms_senders_desc), s.smsSenders, { editSenders = false }) {
            scope.launch { graph.settings.setSmsSenders(it.ifEmpty { Settings.DEFAULT_SMS_SENDERS }) }; editSenders = false
        }
    }
    if (editPackages && s != null) {
        ListEditDialog(stringResource(R.string.settings_packages), stringResource(R.string.settings_packages_desc), s.notificationPackages, { editPackages = false }) {
            scope.launch { graph.settings.setNotificationPackages(it.ifEmpty { Settings.DEFAULT_PACKAGES }) }; editPackages = false
        }
    }
    if (editWeekly && s != null) {
        WeeklyDialog(s, onDismiss = { editWeekly = false }) { day, hour, minute ->
            scope.launch {
                graph.settings.setWeeklySummary(day, hour, minute)
                WeeklySummaryScheduler.schedule(context, graph.settings.current())
            }
            editWeekly = false
        }
    }
}

@Composable
private fun categoryName(id: String): String {
    val graph = LocalAppGraph.current
    val cats by graph.categories.categoryMap.collectAsStateWithLifecycle(initialValue = emptyMap())
    return cats[id]?.name ?: id
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (granted) stringResource(R.string.settings_granted) else stringResource(R.string.settings_missing),
            style = DriftType.Percent11, color = if (granted) DriftColors.Teal else DriftColors.Orange,
        )
        Text(
            if (granted) stringResource(R.string.settings_open) else stringResource(R.string.settings_grant),
            style = DriftType.Link12, color = DriftColors.Lime,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(6.dp),
        )
    }
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DriftType.RowTitle, color = DriftColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value.isNotBlank()) Text(value, style = DriftType.Label11, color = DriftColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.settings_rule_arrow), style = DriftType.Label12, color = DriftColors.TextDim)
    }
}

@Composable
private fun ListEditDialog(title: String, hint: String, current: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var text by remember { mutableStateOf(current.joinToString(", ")) }
    DriftDialog(title, onDismiss, stringResource(R.string.save), onConfirm = {
        onSave(text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }, dismissText = stringResource(R.string.cancel)) {
        DriftTextField(text, { text = it }, hint, singleLine = false, minLines = 2)
    }
}

@Composable
private fun WeeklyDialog(s: Settings, onDismiss: () -> Unit, onSave: (Int, Int, Int) -> Unit) {
    val context = LocalContext.current
    var day by remember { mutableIntStateOf(s.weeklySummaryDay) }
    var time by remember { mutableStateOf(Fmt.clock(s.weeklySummaryHour, s.weeklySummaryMinute)) }
    val parsed = time.split(':').takeIf { it.size == 2 }?.let { p ->
        val h = p[0].trim().toIntOrNull(); val m = p[1].trim().toIntOrNull()
        if (h != null && m != null && h in 0..23 && m in 0..59) h to m else null
    }
    DriftDialog(
        stringResource(R.string.settings_weekly), onDismiss, stringResource(R.string.save),
        onConfirm = { parsed?.let { onSave(day, it.first, it.second) } },
        dismissText = stringResource(R.string.cancel), confirmEnabled = parsed != null,
    ) {
        // Sunday first (Israeli week).
        val days = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        ChoiceRow(days.map { it.value.toString() to Fmt.dayName(context, it) }, day.toString(), onSelect = { day = it.toInt() })
        DriftTextField(time, { time = it }, stringResource(R.string.settings_time), keyboardType = KeyboardType.Number, placeholder = "09:00")
    }
}

private fun appDetailsIntent(context: Context) =
    Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))

private fun Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
