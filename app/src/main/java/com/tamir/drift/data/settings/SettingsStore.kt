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
) {
    companion object {
        val DEFAULT_SMS_SENDERS = setOf("max", "MAX", "Max")
        val DEFAULT_PACKAGES = setOf("com.google.android.apps.walletnfcrel", "com.google.android.apps.nbu.paisa.user")
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
}
