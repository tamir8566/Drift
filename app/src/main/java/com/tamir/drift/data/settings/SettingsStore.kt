package com.tamir.drift.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamir.drift.BuildConfig
import com.tamir.drift.domain.time.TrackingWindow
import kotlinx.coroutines.flow.Flow
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
) {
    /**
     * The window statistics use right now. Until the tracking start arrives, everything since the
     * first launch counts (so the trial month is visible while it is the current month); from the
     * tracking start on, only records from that day count.
     */
    val trackingWindow: TrackingWindow get() {
        val today = java.time.LocalDate.now().toEpochDay()
        return if (today < trackingStartEpochDay) TrackingWindow(firstLaunchEpochDay) else TrackingWindow(trackingStartEpochDay)
    }

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
        val workSummaryEnabled = booleanPreferencesKey("work_summary_enabled")
        val trackingStart = longPreferencesKey("tracking_start_epoch_day")
        /** One-time migration: installs from before the 1.12.2026 default get it applied once. */
        val trackingStartV2Applied = booleanPreferencesKey("tracking_start_v2_applied")
        val firstLaunch = longPreferencesKey("first_launch_epoch_day")
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
        )
    }

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
    suspend fun setMonthlySavingGoal(agorot: Long) = context.dataStore.edit { it[Keys.monthlySavingGoal] = agorot.coerceAtLeast(0L) }
    suspend fun setWeeklyIncomeGoal(agorot: Long) = context.dataStore.edit { it[Keys.weeklyIncomeGoal] = agorot.coerceAtLeast(0L) }
    suspend fun setWorkSummaryEnabled(v: Boolean) = context.dataStore.edit { it[Keys.workSummaryEnabled] = v }
    suspend fun setTrackingStart(epochDay: Long) = context.dataStore.edit { it[Keys.trackingStart] = epochDay }

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
