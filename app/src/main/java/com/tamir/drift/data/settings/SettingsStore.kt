package com.tamir.drift.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamir.drift.BuildConfig
import com.tamir.drift.domain.backup.PrefEntry
import com.tamir.drift.domain.backup.PrefTypes
import com.tamir.drift.domain.money.FxRates
import com.tamir.drift.domain.time.TrackingWindow
import com.tamir.drift.domain.work.GoalCalculator
import com.tamir.drift.domain.work.GoalHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drift_settings")

data class Settings(
    val smsSenders: Set<String>,
    val notificationPackages: Set<String>,
    /** java.time.DayOfWeek value 1..7 (Monday=1 … Sunday=7). Default Sunday. */
    val weeklySummaryDay: Int,
    val weeklySummaryHour: Int,
    val weeklySummaryMinute: Int,
    val weeklySummaryEnabled: Boolean,
    val alert80Enabled: Boolean,
    val alert100Enabled: Boolean,
    val updateManifestUrl: String,
    val lastUpdateCheck: Long,
    /** Merchants the user marked as "not a subscription". */
    val excludedSubscriptions: Set<String>,
    /** PRD "מסך יעד": monthly saving goal (agorot), user-set. */
    val monthlySavingGoalAgorot: Long,
    /** PRD "מסך יעד": fixed weekly income goal (agorot), user-set. */
    val weeklyIncomeGoalAgorot: Long,
    /** Saturday 20:00 work summary ("סיכום שבוע: הרווחת X מתוך Y"). */
    val workSummaryEnabled: Boolean,
    /**
     * App-wide tracking start (epoch day): every statistic, comparison, history, insight and goal
     * counts records from this day on; older ones stay stored but are not counted.
     * Default 1.12.2026 (November 2026 was a test month).
     */
    val trackingStartEpochDay: Long,
    /** The day the app was first used (epoch day). Before the tracking start, statistics count from here. */
    val firstLaunchEpochDay: Long,
    /** "שערי מטבע": the editable rates that turn a foreign amount into a shekel estimate. */
    val fxRates: FxRates = FxRates.DEFAULT,
    /** Daily check that automatic capture still works ("לא נקלטו הוצאות כבר N ימים"). */
    val captureWatchdogEnabled: Boolean = true,
    /** When the capture watchdog last posted its notification (epoch millis; 0 = never). */
    val watchdogLastAlertMillis: Long = 0L,
    /** The calendar year for which the user ticked "עשיתי תיאום מס" (0 = never). */
    val taxCoordinationYear: Int = 0,
    /** The calendar year for which the user ticked "עשיתי תיאום דמי ביטוח לאומי" (0 = never). */
    val insuranceCoordinationYear: Int = 0,
    /** The calendar year in which the "שני מעסיקים" card was dismissed (0 = never); it returns every January. */
    val twoEmployersDismissedYear: Int = 0,
    /** The calendar year whose 5 January "renew the tax coordination" reminder was already sent. */
    val taxReminderYear: Int = 0,
    /** "משוב": bars, numbers and screens move (off = everything snaps). */
    val feedbackAnimations: Boolean = true,
    /** "משוב": a short vibration on save, delete, steppers and the goal moment. */
    val feedbackHaptics: Boolean = true,
    /** "משוב": the three small sounds (only while the phone's ringer is on). */
    val feedbackSound: Boolean = true,
    /** "גיבוי אוטומטי שבועי" is switched on. */
    val backupAutoEnabled: Boolean = false,
    /** The folder the user picked for the automatic backups (a persisted tree URI); "" = none. */
    val backupTreeUri: String = "",
    /** The picked folder's display name, for the settings row. */
    val backupTreeName: String = "",
    /** When a backup of any kind (manual or automatic) last succeeded (epoch millis; 0 = never). */
    val lastBackupMillis: Long = 0L,
    /** When the automatic backup last failed without a success since (epoch millis; 0 = it is fine). */
    val backupAutoFailedMillis: Long = 0L,
    /** When the home row "אין גיבוי לנתונים" was dismissed (epoch millis; 0 = never). */
    val backupNudgeDismissedMillis: Long = 0L,
    /** The stored history of the weekly income goal ([GoalHistory] text; "" = never changed). */
    val weeklyGoalHistory: String = "",
    /** The stored history of the monthly saving goal ([GoalHistory] text; "" = never changed). */
    val monthlyGoalHistory: String = "",
) {
    /**
     * The window statistics use right now. Until the tracking start arrives, everything since the
     * first launch counts (so the trial month is visible while it is the current month); from the
     * tracking start on, only records from that day count.
     */
    val trackingWindow: TrackingWindow get() =
        TrackingWindow.current(java.time.LocalDate.now().toEpochDay(), trackingStartEpochDay, firstLaunchEpochDay)

    /**
     * The weekly income goal over time. The carry-over judges every past week by the goal that
     * was in force in it, so it needs the history and not only today's amount.
     */
    val weeklyIncomeGoal: GoalHistory get() = GoalHistory.parse(weeklyGoalHistory, weeklyIncomeGoalAgorot)

    /** The monthly saving goal over time (see [weeklyIncomeGoal]). */
    val monthlySavingGoal: GoalHistory get() = GoalHistory.parse(monthlyGoalHistory, monthlySavingGoalAgorot)

    companion object {
        val DEFAULT_SMS_SENDERS = setOf("max", "MAX", "Max")
        val DEFAULT_PACKAGES = setOf("com.google.android.apps.walletnfcrel", "com.google.android.apps.nbu.paisa.user")
        const val DEFAULT_MONTHLY_SAVING_GOAL = 2_000_00L
        const val DEFAULT_WEEKLY_INCOME_GOAL = 1_200_00L
        val DEFAULT_TRACKING_START_EPOCH_DAY: Long = TrackingWindow.DEFAULT_START_EPOCH_DAY
        /** Installs that predate the first-launch key started using the app on 8.11.2026. */
        val LEGACY_FIRST_LAUNCH_EPOCH_DAY: Long = java.time.LocalDate.of(2026, 11, 8).toEpochDay()
    }
}

/** DataStore-backed settings (PRD "Settings (DataStore)"). */
class SettingsStore(private val context: Context) {

    companion object {
        /** The lists whose new rows land with a tint ([swapListSeen]). */
        const val LIST_HOME_RECENT = "home_recent"
        const val LIST_ALL_TRANSACTIONS = "all_transactions"
        const val LIST_WORK_RECENT = "work_recent"
        const val LIST_ALL_SHIFTS = "all_shifts"

        /** Preferences that describe this device and install, not the data: a restore leaves them alone. */
        val DEVICE_KEYS: Set<String> = setOf(
            "backup_auto_enabled", "backup_tree_uri", "backup_tree_name", "backup_last_millis",
            "backup_auto_failed_millis", "backup_nudge_dismissed_millis",
            "last_update_check", "whats_new_version", "whats_new_fingerprint",
        )
    }

    private object Keys {
        val smsSenders = stringSetPreferencesKey("sms_senders")
        val packages = stringSetPreferencesKey("notification_packages")
        val weeklyDay = intPreferencesKey("weekly_day")
        val weeklyHour = intPreferencesKey("weekly_hour")
        val weeklyMinute = intPreferencesKey("weekly_minute")
        val weeklyEnabled = booleanPreferencesKey("weekly_enabled")
        val alert80 = booleanPreferencesKey("alert_80")
        val alert100 = booleanPreferencesKey("alert_100")
        val manifestUrl = stringPreferencesKey("update_manifest_url")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val excludedSubs = stringSetPreferencesKey("excluded_subscriptions")
        val monthlySavingGoal = longPreferencesKey("monthly_saving_goal")
        val weeklyIncomeGoal = longPreferencesKey("weekly_income_goal")
        /** [GoalHistory] texts: which goal was in force from which day. */
        val monthlyGoalHistory = stringPreferencesKey("monthly_saving_goal_history")
        val weeklyGoalHistory = stringPreferencesKey("weekly_income_goal_history")
        val workSummaryEnabled = booleanPreferencesKey("work_summary_enabled")
        val trackingStart = longPreferencesKey("tracking_start_epoch_day")
        /** One-time migration: installs from before the 1.12.2026 default get it applied once. */
        val trackingStartV2Applied = booleanPreferencesKey("tracking_start_v2_applied")
        val firstLaunch = longPreferencesKey("first_launch_epoch_day")
        val fxUsd = longPreferencesKey("fx_usd_agorot")
        val fxEur = longPreferencesKey("fx_eur_agorot")
        val fxGbp = longPreferencesKey("fx_gbp_agorot")
        val watchdogEnabled = booleanPreferencesKey("capture_watchdog_enabled")
        val watchdogLastAlert = longPreferencesKey("capture_watchdog_last_alert")
        val taxCoordinationYear = intPreferencesKey("tax_coordination_year")
        val insuranceCoordinationYear = intPreferencesKey("insurance_coordination_year")
        val twoEmployersDismissedYear = intPreferencesKey("two_employers_dismissed_year")
        val taxReminderYear = intPreferencesKey("tax_reminder_year")
        val feedbackAnimations = booleanPreferencesKey("feedback_animations")
        val feedbackHaptics = booleanPreferencesKey("feedback_haptics")
        val feedbackSound = booleanPreferencesKey("feedback_sound")
        val backupAutoEnabled = booleanPreferencesKey("backup_auto_enabled")
        val backupTreeUri = stringPreferencesKey("backup_tree_uri")
        val backupTreeName = stringPreferencesKey("backup_tree_name")
        val lastBackup = longPreferencesKey("backup_last_millis")
        val backupAutoFailed = longPreferencesKey("backup_auto_failed_millis")
        val backupNudgeDismissed = longPreferencesKey("backup_nudge_dismissed_millis")
        /** The month ("yyyy-MM") whose "goal reached" moment was already played. */
        val celebratedGoalMonth = stringPreferencesKey("celebrated_goal_month")
        /** The week (epoch day of its Sunday) whose "week in surplus" moment was already played. */
        val celebratedWeekStart = longPreferencesKey("celebrated_week_start")
        /** "מה חדש": the versionCode seen at the last start and the fingerprint of the notes shown last. */
        val whatsNewVersion = intPreferencesKey("whats_new_version")
        val whatsNewFingerprint = stringPreferencesKey("whats_new_fingerprint")

        /** When a list was last looked at (rows created after it land with a tint); one key per list. */
        fun listSeen(list: String) = longPreferencesKey("list_seen_$list")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            smsSenders = p[Keys.smsSenders] ?: Settings.DEFAULT_SMS_SENDERS,
            notificationPackages = p[Keys.packages] ?: Settings.DEFAULT_PACKAGES,
            weeklySummaryDay = p[Keys.weeklyDay] ?: 7,
            weeklySummaryHour = p[Keys.weeklyHour] ?: 9,
            weeklySummaryMinute = p[Keys.weeklyMinute] ?: 0,
            weeklySummaryEnabled = p[Keys.weeklyEnabled] ?: true,
            alert80Enabled = p[Keys.alert80] ?: true,
            alert100Enabled = p[Keys.alert100] ?: true,
            updateManifestUrl = p[Keys.manifestUrl] ?: BuildConfig.UPDATE_MANIFEST_URL,
            lastUpdateCheck = p[Keys.lastUpdateCheck] ?: 0L,
            excludedSubscriptions = p[Keys.excludedSubs] ?: emptySet(),
            monthlySavingGoalAgorot = p[Keys.monthlySavingGoal] ?: Settings.DEFAULT_MONTHLY_SAVING_GOAL,
            weeklyIncomeGoalAgorot = p[Keys.weeklyIncomeGoal] ?: Settings.DEFAULT_WEEKLY_INCOME_GOAL,
            workSummaryEnabled = p[Keys.workSummaryEnabled] ?: true,
            trackingStartEpochDay = p[Keys.trackingStart] ?: Settings.DEFAULT_TRACKING_START_EPOCH_DAY,
            firstLaunchEpochDay = p[Keys.firstLaunch] ?: Settings.LEGACY_FIRST_LAUNCH_EPOCH_DAY,
            fxRates = FxRates(
                usdAgorot = p[Keys.fxUsd] ?: FxRates.DEFAULT.usdAgorot,
                eurAgorot = p[Keys.fxEur] ?: FxRates.DEFAULT.eurAgorot,
                gbpAgorot = p[Keys.fxGbp] ?: FxRates.DEFAULT.gbpAgorot,
            ),
            captureWatchdogEnabled = p[Keys.watchdogEnabled] ?: true,
            watchdogLastAlertMillis = p[Keys.watchdogLastAlert] ?: 0L,
            taxCoordinationYear = p[Keys.taxCoordinationYear] ?: 0,
            insuranceCoordinationYear = p[Keys.insuranceCoordinationYear] ?: 0,
            twoEmployersDismissedYear = p[Keys.twoEmployersDismissedYear] ?: 0,
            taxReminderYear = p[Keys.taxReminderYear] ?: 0,
            feedbackAnimations = p[Keys.feedbackAnimations] ?: true,
            feedbackHaptics = p[Keys.feedbackHaptics] ?: true,
            feedbackSound = p[Keys.feedbackSound] ?: true,
            backupAutoEnabled = p[Keys.backupAutoEnabled] ?: false,
            backupTreeUri = p[Keys.backupTreeUri] ?: "",
            backupTreeName = p[Keys.backupTreeName] ?: "",
            lastBackupMillis = p[Keys.lastBackup] ?: 0L,
            backupAutoFailedMillis = p[Keys.backupAutoFailed] ?: 0L,
            backupNudgeDismissedMillis = p[Keys.backupNudgeDismissed] ?: 0L,
            weeklyGoalHistory = p[Keys.weeklyGoalHistory] ?: "",
            monthlyGoalHistory = p[Keys.monthlyGoalHistory] ?: "",
        )
    // Keys that are not part of [Settings] (a list's "last seen" time, …) are written often and
    // must not make every screen recompute.
    }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun setSmsSenders(v: Set<String>) = context.dataStore.edit { it[Keys.smsSenders] = v }
    suspend fun setNotificationPackages(v: Set<String>) = context.dataStore.edit { it[Keys.packages] = v }
    suspend fun setWeeklySummary(day: Int, hour: Int, minute: Int) = context.dataStore.edit {
        it[Keys.weeklyDay] = day; it[Keys.weeklyHour] = hour; it[Keys.weeklyMinute] = minute
    }
    suspend fun setWeeklyEnabled(v: Boolean) = context.dataStore.edit { it[Keys.weeklyEnabled] = v }
    suspend fun setAlert80(v: Boolean) = context.dataStore.edit { it[Keys.alert80] = v }
    suspend fun setAlert100(v: Boolean) = context.dataStore.edit { it[Keys.alert100] = v }
    suspend fun setLastUpdateCheck(v: Long) = context.dataStore.edit { it[Keys.lastUpdateCheck] = v }
    suspend fun setExcludedSubscriptions(v: Set<String>) = context.dataStore.edit { it[Keys.excludedSubs] = v }

    /**
     * Sets the monthly saving goal from the current month on. Earlier months keep the goal they
     * were judged by, so the balance carried from them does not change.
     */
    suspend fun setMonthlySavingGoal(agorot: Long, today: java.time.LocalDate = java.time.LocalDate.now()) = context.dataStore.edit {
        val new = agorot.coerceAtLeast(0L)
        val old = it[Keys.monthlySavingGoal] ?: Settings.DEFAULT_MONTHLY_SAVING_GOAL
        it[Keys.monthlyGoalHistory] = GoalHistory.change(it[Keys.monthlyGoalHistory], old, new, today.withDayOfMonth(1).toEpochDay())
        it[Keys.monthlySavingGoal] = new
    }

    /**
     * Sets the weekly income goal from the current (Sunday–Saturday) week on. Earlier weeks keep
     * the goal they were judged by, so the balance carried from them does not change.
     */
    suspend fun setWeeklyIncomeGoal(agorot: Long, today: java.time.LocalDate = java.time.LocalDate.now()) = context.dataStore.edit {
        val new = agorot.coerceAtLeast(0L)
        val old = it[Keys.weeklyIncomeGoal] ?: Settings.DEFAULT_WEEKLY_INCOME_GOAL
        it[Keys.weeklyGoalHistory] = GoalHistory.change(it[Keys.weeklyGoalHistory], old, new, GoalCalculator.weekOf(today).start.toEpochDay())
        it[Keys.weeklyIncomeGoal] = new
    }

    suspend fun setWorkSummaryEnabled(v: Boolean) = context.dataStore.edit { it[Keys.workSummaryEnabled] = v }
    suspend fun setTrackingStart(epochDay: Long) = context.dataStore.edit { it[Keys.trackingStart] = epochDay }

    suspend fun setFxRates(rates: FxRates) = context.dataStore.edit {
        it[Keys.fxUsd] = rates.usdAgorot.coerceAtLeast(0L)
        it[Keys.fxEur] = rates.eurAgorot.coerceAtLeast(0L)
        it[Keys.fxGbp] = rates.gbpAgorot.coerceAtLeast(0L)
    }
    suspend fun setCaptureWatchdogEnabled(v: Boolean) = context.dataStore.edit { it[Keys.watchdogEnabled] = v }
    suspend fun setWatchdogLastAlert(millis: Long) = context.dataStore.edit { it[Keys.watchdogLastAlert] = millis }

    /** Ticks / unticks "עשיתי תיאום מס" for [year] (the state is per calendar year). */
    suspend fun setTaxCoordination(year: Int, done: Boolean) = context.dataStore.edit { it[Keys.taxCoordinationYear] = if (done) year else 0 }

    /** Ticks / unticks "עשיתי תיאום דמי ביטוח לאומי" for [year]. */
    suspend fun setInsuranceCoordination(year: Int, done: Boolean) = context.dataStore.edit { it[Keys.insuranceCoordinationYear] = if (done) year else 0 }

    suspend fun setTwoEmployersDismissed(year: Int) = context.dataStore.edit { it[Keys.twoEmployersDismissedYear] = year }
    suspend fun setTaxReminderYear(year: Int) = context.dataStore.edit { it[Keys.taxReminderYear] = year }

    suspend fun setFeedbackAnimations(v: Boolean) = context.dataStore.edit { it[Keys.feedbackAnimations] = v }
    suspend fun setFeedbackHaptics(v: Boolean) = context.dataStore.edit { it[Keys.feedbackHaptics] = v }
    suspend fun setFeedbackSound(v: Boolean) = context.dataStore.edit { it[Keys.feedbackSound] = v }

    suspend fun setBackupAutoEnabled(v: Boolean) = context.dataStore.edit {
        it[Keys.backupAutoEnabled] = v
        // Switched off: an old failure is no longer something to look at.
        if (!v) it[Keys.backupAutoFailed] = 0L
    }

    /** The folder for the automatic backups was picked (its persisted tree URI and display name). */
    suspend fun setBackupFolder(treeUri: String, name: String) = context.dataStore.edit {
        it[Keys.backupTreeUri] = treeUri
        it[Keys.backupTreeName] = name
        it[Keys.backupAutoFailed] = 0L
    }

    /** A backup was written. An automatic one also clears the failure mark of the automatic backup. */
    suspend fun recordBackupSuccess(nowMillis: Long, automatic: Boolean) = context.dataStore.edit {
        it[Keys.lastBackup] = nowMillis
        if (automatic) it[Keys.backupAutoFailed] = 0L
    }

    suspend fun recordAutoBackupFailure(nowMillis: Long) = context.dataStore.edit { it[Keys.backupAutoFailed] = nowMillis }
    suspend fun dismissBackupNudge(nowMillis: Long) = context.dataStore.edit { it[Keys.backupNudgeDismissed] = nowMillis }

    /**
     * Marks [list] as looked at now and returns when it was looked at before (0 = never). Rows
     * created after the returned time are the ones that are new to the user.
     */
    suspend fun swapListSeen(list: String, nowMillis: Long): Long {
        var previous = 0L
        context.dataStore.edit {
            previous = it[Keys.listSeen(list)] ?: 0L
            it[Keys.listSeen(list)] = nowMillis
        }
        return previous
    }

    /** True the first time [month] ("yyyy-MM") reaches its saving goal — the moment is played once. */
    suspend fun markGoalCelebrated(month: String): Boolean {
        var first = false
        context.dataStore.edit {
            if (it[Keys.celebratedGoalMonth] != month) {
                it[Keys.celebratedGoalMonth] = month
                first = true
            }
        }
        return first
    }

    /** True the first time the week that starts on [weekStartEpochDay] is in surplus. */
    suspend fun markWeekCelebrated(weekStartEpochDay: Long): Boolean {
        var first = false
        context.dataStore.edit {
            if (it[Keys.celebratedWeekStart] != weekStartEpochDay) {
                it[Keys.celebratedWeekStart] = weekStartEpochDay
                first = true
            }
        }
        return first
    }

    /** "מה חדש": the versionCode recorded at the last start (0 = none) and the fingerprint of the notes shown last. */
    suspend fun whatsNewSeen(): Pair<Int, String> {
        val p = context.dataStore.data.first()
        return (p[Keys.whatsNewVersion] ?: 0) to (p[Keys.whatsNewFingerprint] ?: "")
    }

    /** Records the running version; with [fingerprint] also the notes that were shown (or skipped as already known). */
    suspend fun setWhatsNewSeen(version: Int, fingerprint: String?) = context.dataStore.edit {
        it[Keys.whatsNewVersion] = version
        if (fingerprint != null) it[Keys.whatsNewFingerprint] = fingerprint
    }

    /** Backup: every stored preference, with its type. */
    suspend fun exportAll(): List<PrefEntry> =
        context.dataStore.data.first().asMap().mapNotNull { (key, value) -> toEntry(key.name, value) }

    /**
     * Restore: the stored preferences are replaced by [entries]. What describes this device rather
     * than the data ([DEVICE_KEYS]: the backup folder and its state, the update check, the "מה חדש"
     * record) is kept as it is here. [backupCreatedAt] becomes the "last backup" time when it is
     * newer than the one known here — a backup from that moment evidently exists.
     */
    suspend fun importAll(entries: List<PrefEntry>, backupCreatedAt: Long) = context.dataStore.edit { prefs ->
        val kept: List<Pair<String, Any>> = prefs.asMap().filter { it.key.name in DEVICE_KEYS }.map { it.key.name to it.value }
        prefs.clear()
        for (entry in entries) {
            if (entry.key !in DEVICE_KEYS) putEntry(prefs, entry)
        }
        for ((name, value) in kept) {
            toEntry(name, value)?.let { putEntry(prefs, it) }
        }
        if (backupCreatedAt > (prefs[Keys.lastBackup] ?: 0L)) prefs[Keys.lastBackup] = backupCreatedAt
    }

    private fun toEntry(name: String, value: Any?): PrefEntry? = when (value) {
        is Boolean -> PrefEntry(name, PrefTypes.BOOLEAN, value.toString())
        is Int -> PrefEntry(name, PrefTypes.INT, value.toString())
        is Long -> PrefEntry(name, PrefTypes.LONG, value.toString())
        is Float -> PrefEntry(name, PrefTypes.FLOAT, value.toString())
        is Double -> PrefEntry(name, PrefTypes.DOUBLE, value.toString())
        is String -> PrefEntry(name, PrefTypes.STRING, value)
        is Set<*> -> PrefEntry(name, PrefTypes.STRING_SET, values = value.filterIsInstance<String>().toSet())
        else -> null
    }

    private fun putEntry(prefs: MutablePreferences, entry: PrefEntry) {
        when (entry.type) {
            PrefTypes.BOOLEAN -> prefs[booleanPreferencesKey(entry.key)] = entry.value == "true"
            PrefTypes.INT -> entry.value.toIntOrNull()?.let { v -> prefs[intPreferencesKey(entry.key)] = v }
            PrefTypes.LONG -> entry.value.toLongOrNull()?.let { v -> prefs[longPreferencesKey(entry.key)] = v }
            PrefTypes.FLOAT -> entry.value.toFloatOrNull()?.let { v -> prefs[floatPreferencesKey(entry.key)] = v }
            PrefTypes.DOUBLE -> entry.value.toDoubleOrNull()?.let { v -> prefs[doublePreferencesKey(entry.key)] = v }
            PrefTypes.STRING -> prefs[stringPreferencesKey(entry.key)] = entry.value
            PrefTypes.STRING_SET -> prefs[stringSetPreferencesKey(entry.key)] = entry.values
        }
    }

    /**
     * Called at startup. Makes sure the tracking start exists and, once per install, moves it to
     * the 1.12.2026 default (earlier builds set it to the install day, which fell in the November
     * test month). After the migration flag is set the user's own edits are never overridden.
     */
    suspend fun ensureTrackingStart() = context.dataStore.edit {
        if (it[Keys.firstLaunch] == null) {
            // Installs from before this key existed were first used on 8.11.2026 (the v2 flag was
            // already set for them); anything newer starts counting today.
            val today = java.time.LocalDate.now().toEpochDay()
            it[Keys.firstLaunch] = if (it[Keys.trackingStartV2Applied] == true || today <= Settings.DEFAULT_TRACKING_START_EPOCH_DAY) Settings.LEGACY_FIRST_LAUNCH_EPOCH_DAY else today
        }
        if (it[Keys.trackingStartV2Applied] != true) {
            it[Keys.trackingStart] = Settings.DEFAULT_TRACKING_START_EPOCH_DAY
            it[Keys.trackingStartV2Applied] = true
        } else if (it[Keys.trackingStart] == null) {
            it[Keys.trackingStart] = Settings.DEFAULT_TRACKING_START_EPOCH_DAY
        }
    }
}
