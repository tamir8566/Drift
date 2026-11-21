package com.tamir.drift.domain.backup

import java.time.LocalDate

/**
 * The backup file: one zip holding the Room database file ([BackupFiles.DATABASE], after a WAL
 * checkpoint), every DataStore preference ([BackupFiles.SETTINGS]) and [BackupMeta]
 * ([BackupFiles.META]). The file is not encrypted.
 *
 * This file holds the pure rules — naming, the meta format, what may be restored, which automatic
 * backups are kept and when the home screen nudges for a first backup. The Android side
 * (zip, storage access, Room) is `backup/BackupManager`.
 */
data class BackupMeta(
    /** versionCode of the app that wrote the backup. */
    val appVersionCode: Int,
    /** Room schema version of the database inside the backup. */
    val dbVersion: Int,
    /** When the backup was written, epoch millis. */
    val createdAt: Long,
)

object BackupFiles {
    const val META = "meta.json"
    const val SETTINGS = "settings.json"
    const val DATABASE = "drift.db"

    /** Present only when the write-ahead log could not be emptied before the copy; restored beside the database. */
    const val DATABASE_WAL = "drift.db-wal"

    /** How many automatic backups stay in the chosen folder. */
    const val KEEP_AUTOMATIC = 5

    private const val PREFIX = "drift-backup-"
    private const val SUFFIX = ".zip"

    /** A storage provider renames a second file of the same name to "name (1).zip"; those count too. */
    private val NAME = Regex("""drift-backup-(\d{4})-(\d{2})-(\d{2})(?: \((\d+)\))?\.zip""")

    /** `drift-backup-YYYY-MM-DD.zip`. */
    fun fileName(date: LocalDate): String =
        PREFIX + date.year.toString().padStart(4, '0') + "-" +
            date.monthValue.toString().padStart(2, '0') + "-" +
            date.dayOfMonth.toString().padStart(2, '0') + SUFFIX

    /** The day a backup file is named after; null for any other file name. */
    fun dateOf(name: String): LocalDate? {
        val match = NAME.matchEntire(name) ?: return null
        val (year, month, day) = match.destructured
        return try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } catch (e: RuntimeException) {
            null
        }
    }

    fun isBackupName(name: String): Boolean = dateOf(name) != null

    /**
     * Retention of the automatic backups: of the backup files in [names], the newest [keep] (by
     * the date in the name) stay; the rest are returned for deletion. Files that are not named
     * like a backup are never returned.
     */
    fun toDelete(names: List<String>, keep: Int = KEEP_AUTOMATIC): List<String> {
        val dated = names.distinct().mapNotNull { name -> dateOf(name)?.let { date -> date to name } }
        val newestFirst = dated.sortedWith(compareByDescending<Pair<LocalDate, String>> { it.first }.thenByDescending { it.second })
        return newestFirst.drop(keep.coerceAtLeast(0)).map { it.second }
    }
}

/** `meta.json`: {"appVersionCode":12,"dbVersion":5,"createdAt":1795000000000}. */
object BackupMetaCodec {

    fun encode(meta: BackupMeta): String =
        "{\"appVersionCode\":${meta.appVersionCode},\"dbVersion\":${meta.dbVersion},\"createdAt\":${meta.createdAt}}"

    /** Null when the text is not a meta file (not JSON, or a field is missing or not a whole number). */
    fun decode(json: String): BackupMeta? {
        val fields = MiniJson.parseOrNull(json) as? Map<*, *> ?: return null
        val app = fields["appVersionCode"] as? Long ?: return null
        val db = fields["dbVersion"] as? Long ?: return null
        val created = fields["createdAt"] as? Long ?: return null
        if (app !in 0..Int.MAX_VALUE || db !in 0..Int.MAX_VALUE) return null
        return BackupMeta(app.toInt(), db.toInt(), created)
    }
}

/** Reads what is needed from the first 100 bytes of an SQLite file. */
object SqliteHeader {
    const val SIZE = 100
    private const val MAGIC = "SQLite format 3\u0000"
    private const val USER_VERSION_OFFSET = 60

    /** The `user_version` (Room's schema version) of the file; null when [header] is not an SQLite header. */
    fun userVersion(header: ByteArray): Int? {
        if (header.size < SIZE) return null
        for (i in MAGIC.indices) {
            if (header[i] != MAGIC[i].code.toByte()) return null
        }
        var value = 0
        for (i in 0 until 4) {
            value = (value shl 8) or (header[USER_VERSION_OFFSET + i].toInt() and 0xFF)
        }
        return value
    }
}

enum class BackupCheck {
    /** The backup can be restored (an older database is migrated when it is opened). */
    OK,

    /** Not a Drift backup, or a damaged one. */
    UNREADABLE,

    /** Written by a newer version of the app: its database cannot be opened by this one. */
    NEWER_DATABASE,
}

object BackupValidator {
    /**
     * @param meta the decoded `meta.json`, null when missing or unreadable.
     * @param fileDbVersion the schema version read from the database file itself ([SqliteHeader]),
     *   null when the file is missing or is not an SQLite database.
     * @param appDbVersion the schema version this app works with.
     */
    fun check(meta: BackupMeta?, fileDbVersion: Int?, appDbVersion: Int): BackupCheck {
        if (meta == null || fileDbVersion == null) return BackupCheck.UNREADABLE
        if (meta.dbVersion < 1 || fileDbVersion < 1 || meta.createdAt <= 0L) return BackupCheck.UNREADABLE
        // The file itself decides too, so a meta that understates the version cannot slip through.
        if (maxOf(meta.dbVersion, fileDbVersion) > appDbVersion) return BackupCheck.NEWER_DATABASE
        return BackupCheck.OK
    }
}

/** One stored preference: its key, its type ([PrefTypes]) and the value as text (a string set in [values]). */
data class PrefEntry(val key: String, val type: String, val value: String = "", val values: Set<String> = emptySet())

object PrefTypes {
    const val BOOLEAN = "boolean"
    const val INT = "int"
    const val LONG = "long"
    const val FLOAT = "float"
    const val DOUBLE = "double"
    const val STRING = "string"
    const val STRING_SET = "stringSet"

    val all: Set<String> = setOf(BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING, STRING_SET)
}

/**
 * `settings.json`: every DataStore preference with its type, values kept as text:
 * {"version":1,"preferences":[{"key":"weekly_day","type":"int","value":"7"},
 * {"key":"sms_senders","type":"stringSet","values":["max","MAX"]}]}.
 */
object SettingsCodec {
    private const val VERSION = 1

    fun encode(entries: List<PrefEntry>): String {
        val out = StringBuilder()
        out.append("{\"version\":").append(VERSION).append(",\"preferences\":[")
        entries.sortedBy { it.key }.forEachIndexed { i, e ->
            if (i > 0) out.append(',')
            out.append("\n{\"key\":").append(MiniJson.quote(e.key)).append(",\"type\":").append(MiniJson.quote(e.type))
            if (e.type == PrefTypes.STRING_SET) {
                out.append(",\"values\":[").append(e.values.sorted().joinToString(",") { MiniJson.quote(it) }).append(']')
            } else {
                out.append(",\"value\":").append(MiniJson.quote(e.value))
            }
            out.append('}')
        }
        out.append("\n]}")
        return out.toString()
    }

    /** Null when the text is not a settings file. Entries of an unknown type or with a bad value are skipped. */
    fun decode(json: String): List<PrefEntry>? {
        val root = MiniJson.parseOrNull(json) as? Map<*, *> ?: return null
        val list = root["preferences"] as? List<*> ?: return null
        val entries = ArrayList<PrefEntry>()
        for (item in list) {
            val fields = item as? Map<*, *> ?: continue
            val key = fields["key"] as? String ?: continue
            val type = fields["type"] as? String ?: continue
            if (key.isEmpty() || type !in PrefTypes.all) continue
            if (type == PrefTypes.STRING_SET) {
                val values = (fields["values"] as? List<*> ?: continue).filterIsInstance<String>().toSet()
                entries.add(PrefEntry(key, type, values = values))
            } else {
                val value = fields["value"] as? String ?: continue
                if (!valueFits(type, value)) continue
                entries.add(PrefEntry(key, type, value = value))
            }
        }
        return entries
    }

    private fun valueFits(type: String, value: String): Boolean = when (type) {
        PrefTypes.BOOLEAN -> value == "true" || value == "false"
        PrefTypes.INT -> value.toIntOrNull() != null
        PrefTypes.LONG -> value.toLongOrNull() != null
        PrefTypes.FLOAT -> value.toFloatOrNull() != null
        PrefTypes.DOUBLE -> value.toDoubleOrNull() != null
        else -> true
    }
}

/** The quiet home row "אין גיבוי לנתונים · להגדיר". */
object BackupNudge {
    /** Days after the first launch before the row may appear. */
    const val QUIET_DAYS = 14

    /** A dismissed row stays away this long. */
    const val DISMISS_DAYS = 30

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * @param lastBackupMillis when a backup of any kind (manual or automatic) last succeeded; 0 = never.
     * @param dismissedAtMillis when the row was last dismissed; 0 = never.
     */
    fun shouldShow(firstLaunchEpochDay: Long, todayEpochDay: Long, lastBackupMillis: Long, dismissedAtMillis: Long, nowMillis: Long): Boolean {
        if (lastBackupMillis > 0L) return false
        if (todayEpochDay - firstLaunchEpochDay < QUIET_DAYS) return false
        if (dismissedAtMillis > 0L && nowMillis - dismissedAtMillis < DISMISS_DAYS * DAY_MILLIS) return false
        return true
    }
}
