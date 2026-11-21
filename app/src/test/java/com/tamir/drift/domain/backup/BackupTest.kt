package com.tamir.drift.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {

    // ---- file naming ----

    @Test
    fun `file name carries the date with leading zeros`() {
        assertEquals("drift-backup-2026-11-21.zip", BackupFiles.fileName(LocalDate.of(2026, 11, 21)))
        assertEquals("drift-backup-2027-01-05.zip", BackupFiles.fileName(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun `the date is read back from a backup name`() {
        val day = LocalDate.of(2026, 12, 3)
        assertEquals(day, BackupFiles.dateOf(BackupFiles.fileName(day)))
        // A provider renames a second file of the same name.
        assertEquals(day, BackupFiles.dateOf("drift-backup-2026-12-03 (1).zip"))
    }

    @Test
    fun `other names are not backups`() {
        assertNull(BackupFiles.dateOf("drift-2026-11.csv"))
        assertNull(BackupFiles.dateOf("drift-backup-2026-13-40.zip"))
        assertNull(BackupFiles.dateOf("drift-backup-2026-11-21.zip.tmp"))
        assertNull(BackupFiles.dateOf("my-drift-backup-2026-11-21.zip"))
        assertFalse(BackupFiles.isBackupName("photo.jpg"))
        assertTrue(BackupFiles.isBackupName("drift-backup-2026-11-21.zip"))
    }

    // ---- retention ----

    private fun names(vararg days: Int): List<String> = days.map { BackupFiles.fileName(LocalDate.of(2026, 11, it)) }

    @Test
    fun `five or fewer backups are all kept`() {
        assertEquals(emptyList<String>(), BackupFiles.toDelete(names(1, 8, 15, 22, 29)))
        assertEquals(emptyList<String>(), BackupFiles.toDelete(names(1)))
        assertEquals(emptyList<String>(), BackupFiles.toDelete(emptyList()))
    }

    @Test
    fun `only the newest five stay whatever the listing order`() {
        val all = names(22, 1, 29, 8, 15, 3, 30)
        assertEquals(names(3, 1), BackupFiles.toDelete(all))
    }

    @Test
    fun `retention spans months and years by date`() {
        val all = listOf(
            "drift-backup-2026-12-27.zip", "drift-backup-2027-01-03.zip", "drift-backup-2027-01-10.zip",
            "drift-backup-2027-01-17.zip", "drift-backup-2027-01-24.zip", "drift-backup-2027-01-31.zip",
        )
        assertEquals(listOf("drift-backup-2026-12-27.zip"), BackupFiles.toDelete(all))
    }

    @Test
    fun `files that are not backups are never deleted`() {
        val all = names(1, 2, 3, 4, 5, 6) + listOf("notes.txt", "drift-2026-11.csv", "drift-backup-old.zip")
        assertEquals(names(1), BackupFiles.toDelete(all))
    }

    @Test
    fun `a renamed duplicate of a day counts as a backup of that day`() {
        val all = names(1, 8, 15, 22, 29) + "drift-backup-2026-11-29 (1).zip"
        assertEquals(names(1), BackupFiles.toDelete(all))
        assertEquals(names(8, 1), BackupFiles.toDelete(all, keep = 4))
    }

    // ---- meta ----

    @Test
    fun `meta survives a round trip`() {
        val meta = BackupMeta(appVersionCode = 57, dbVersion = 5, createdAt = 1_795_000_000_000L)
        assertEquals(meta, BackupMetaCodec.decode(BackupMetaCodec.encode(meta)))
    }

    @Test
    fun `meta is read in any field order and with spaces`() {
        val json = "{ \"createdAt\": 1795000000000,\n \"dbVersion\": 3, \"extra\": \"x\", \"appVersionCode\": 12 }"
        assertEquals(BackupMeta(12, 3, 1_795_000_000_000L), BackupMetaCodec.decode(json))
    }

    @Test
    fun `a broken or partial meta is rejected`() {
        assertNull(BackupMetaCodec.decode(""))
        assertNull(BackupMetaCodec.decode("not json"))
        assertNull(BackupMetaCodec.decode("[1,2,3]"))
        assertNull(BackupMetaCodec.decode("{\"appVersionCode\":12,\"dbVersion\":5}"))
        assertNull(BackupMetaCodec.decode("{\"appVersionCode\":12,\"dbVersion\":\"5\",\"createdAt\":1}"))
        assertNull(BackupMetaCodec.decode("{\"appVersionCode\":12,\"dbVersion\":5.5,\"createdAt\":1}"))
        assertNull(BackupMetaCodec.decode("{\"appVersionCode\":12,\"dbVersion\":5,\"createdAt\":1"))
    }

    // ---- validation ----

    private val meta = BackupMeta(appVersionCode = 40, dbVersion = 5, createdAt = 1_795_000_000_000L)

    @Test
    fun `a backup of the same database version is accepted`() {
        assertEquals(BackupCheck.OK, BackupValidator.check(meta, fileDbVersion = 5, appDbVersion = 5))
    }

    @Test
    fun `an older backup is accepted - it migrates on open`() {
        assertEquals(BackupCheck.OK, BackupValidator.check(meta.copy(dbVersion = 2), fileDbVersion = 2, appDbVersion = 5))
    }

    @Test
    fun `a backup from a newer database is refused`() {
        assertEquals(BackupCheck.NEWER_DATABASE, BackupValidator.check(meta.copy(dbVersion = 6), fileDbVersion = 6, appDbVersion = 5))
    }

    @Test
    fun `the database file itself is believed over an understated meta`() {
        assertEquals(BackupCheck.NEWER_DATABASE, BackupValidator.check(meta.copy(dbVersion = 4), fileDbVersion = 7, appDbVersion = 5))
    }

    @Test
    fun `missing meta or a file that is not a database is unreadable`() {
        assertEquals(BackupCheck.UNREADABLE, BackupValidator.check(null, fileDbVersion = 5, appDbVersion = 5))
        assertEquals(BackupCheck.UNREADABLE, BackupValidator.check(meta, fileDbVersion = null, appDbVersion = 5))
        assertEquals(BackupCheck.UNREADABLE, BackupValidator.check(meta, fileDbVersion = 0, appDbVersion = 5))
        assertEquals(BackupCheck.UNREADABLE, BackupValidator.check(meta.copy(dbVersion = 0), fileDbVersion = 5, appDbVersion = 5))
        assertEquals(BackupCheck.UNREADABLE, BackupValidator.check(meta.copy(createdAt = 0L), fileDbVersion = 5, appDbVersion = 5))
    }

    // ---- SQLite header ----

    private fun header(userVersion: Int): ByteArray {
        val bytes = ByteArray(SqliteHeader.SIZE)
        "SQLite format 3\u0000".forEachIndexed { i, c -> bytes[i] = c.code.toByte() }
        bytes[60] = (userVersion ushr 24).toByte()
        bytes[61] = (userVersion ushr 16).toByte()
        bytes[62] = (userVersion ushr 8).toByte()
        bytes[63] = userVersion.toByte()
        return bytes
    }

    @Test
    fun `the schema version is read from the database header`() {
        assertEquals(5, SqliteHeader.userVersion(header(5)))
        assertEquals(300, SqliteHeader.userVersion(header(300)))
        assertEquals(0, SqliteHeader.userVersion(header(0)))
    }

    @Test
    fun `a file that is not an SQLite database has no version`() {
        assertNull(SqliteHeader.userVersion(ByteArray(SqliteHeader.SIZE)))
        assertNull(SqliteHeader.userVersion("SQLite format 3".toByteArray()))
        assertNull(SqliteHeader.userVersion(ByteArray(0)))
    }

    // ---- settings ----

    @Test
    fun `every preference type survives a round trip`() {
        val entries = listOf(
            PrefEntry("alert_80", PrefTypes.BOOLEAN, "false"),
            PrefEntry("weekly_day", PrefTypes.INT, "7"),
            PrefEntry("monthly_saving_goal", PrefTypes.LONG, "250000"),
            PrefEntry("ratio", PrefTypes.FLOAT, "1.5"),
            PrefEntry("precise", PrefTypes.DOUBLE, "-0.25"),
            PrefEntry("update_manifest_url", PrefTypes.STRING, "https://example.org/a?b=1&c=\"q\"\\\n\tסוף"),
            PrefEntry("sms_senders", PrefTypes.STRING_SET, values = setOf("max", "MAX", "בנק, \"א\"")),
            PrefEntry("excluded_subscriptions", PrefTypes.STRING_SET, values = emptySet()),
        )
        val decoded = SettingsCodec.decode(SettingsCodec.encode(entries))
        assertEquals(entries.sortedBy { it.key }, decoded)
    }

    @Test
    fun `an empty settings file is a valid empty list`() {
        assertEquals(emptyList<PrefEntry>(), SettingsCodec.decode(SettingsCodec.encode(emptyList())))
    }

    @Test
    fun `text that is not a settings file is rejected`() {
        assertNull(SettingsCodec.decode("hello"))
        assertNull(SettingsCodec.decode("{\"version\":1}"))
        assertNull(SettingsCodec.decode("{\"preferences\":{}}"))
    }

    @Test
    fun `entries with an unknown type or a value that does not fit are skipped`() {
        val json = "{\"version\":1,\"preferences\":[" +
            "{\"key\":\"a\",\"type\":\"int\",\"value\":\"12\"}," +
            "{\"key\":\"b\",\"type\":\"int\",\"value\":\"twelve\"}," +
            "{\"key\":\"c\",\"type\":\"bytes\",\"value\":\"00\"}," +
            "{\"key\":\"d\",\"type\":\"boolean\",\"value\":\"yes\"}," +
            "{\"key\":\"e\",\"type\":\"long\"}," +
            "{\"key\":\"\",\"type\":\"long\",\"value\":\"1\"}," +
            "{\"key\":\"f\",\"type\":\"stringSet\",\"values\":[\"x\",5,\"y\"]}]}"
        assertEquals(
            listOf(PrefEntry("a", PrefTypes.INT, "12"), PrefEntry("f", PrefTypes.STRING_SET, values = setOf("x", "y"))),
            SettingsCodec.decode(json),
        )
    }

    // ---- the home nudge ----

    private val dayMillis = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val today = 20_800L

    @Test
    fun `no nudge during the first fortnight`() {
        assertFalse(BackupNudge.shouldShow(today - 13, today, lastBackupMillis = 0L, dismissedAtMillis = 0L, nowMillis = now))
    }

    @Test
    fun `nudge from day fourteen while there is no backup`() {
        assertTrue(BackupNudge.shouldShow(today - 14, today, 0L, 0L, now))
        assertTrue(BackupNudge.shouldShow(today - 200, today, 0L, 0L, now))
    }

    @Test
    fun `any backup ends the nudge`() {
        assertFalse(BackupNudge.shouldShow(today - 200, today, lastBackupMillis = now - 90 * dayMillis, dismissedAtMillis = 0L, nowMillis = now))
    }

    @Test
    fun `a dismissed nudge stays away for thirty days`() {
        assertFalse(BackupNudge.shouldShow(today - 200, today, 0L, dismissedAtMillis = now - 29 * dayMillis, nowMillis = now))
        assertTrue(BackupNudge.shouldShow(today - 200, today, 0L, dismissedAtMillis = now - 30 * dayMillis, nowMillis = now))
    }
}
