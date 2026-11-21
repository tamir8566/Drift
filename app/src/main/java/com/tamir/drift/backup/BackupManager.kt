package com.tamir.drift.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tamir.drift.BuildConfig
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.domain.backup.BackupCheck
import com.tamir.drift.domain.backup.BackupFiles
import com.tamir.drift.domain.backup.BackupMeta
import com.tamir.drift.domain.backup.BackupMetaCodec
import com.tamir.drift.domain.backup.BackupValidator
import com.tamir.drift.domain.backup.PrefEntry
import com.tamir.drift.domain.backup.SettingsCodec
import com.tamir.drift.domain.backup.SqliteHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup and restore of everything the app stores: the Room database and the DataStore settings,
 * in one zip (see [BackupFiles]). The file is NOT encrypted — the settings screen says so.
 *
 * Nothing here needs a storage permission: a manual backup is written to a document the user
 * creates (ACTION_CREATE_DOCUMENT), a restore reads a document the user opens
 * (ACTION_OPEN_DOCUMENT), and the weekly automatic backup writes into a folder the user picked
 * once (ACTION_OPEN_DOCUMENT_TREE, persisted permission).
 *
 * Backup: the write-ahead log is checkpointed into the database file, then the file is copied
 * while a write transaction is held, so nothing can change it during the copy. If the log could
 * not be emptied (a reader was busy) it is copied too and restored beside the database.
 *
 * Restore, in two steps so that the confirmation shows the backup's date:
 *  1. [prepareRestore] unpacks the picked file into the cache, checks it ([BackupValidator]) and
 *     opens its database with Room on a side file — migrations and the schema check run there.
 *     Whatever fails, the real data has not been touched.
 *  2. [restore] replaces the settings and swaps the checked database in for the real one, then
 *     restarts the app process. If the swap fails, the previous database and settings are put back.
 */
class BackupManager(
    private val context: Context,
    private val database: () -> DriftDatabase,
    private val settings: SettingsStore,
) {

    /** What [prepareRestore] found. */
    sealed class RestoreCheck {
        /** A valid backup, checked and ready; [meta] tells the user what he is about to restore. */
        class Ready(val meta: BackupMeta, internal val entries: List<PrefEntry>) : RestoreCheck()

        /** Not a Drift backup, or a damaged one. */
        data object Unreadable : RestoreCheck()

        /** Written by a newer version of the app. */
        data object NewerVersion : RestoreCheck()
    }

    /** One backup or restore at a time (the weekly job may run while the settings screen is open). */
    private val mutex = Mutex()

    // ------------------------------------------------------------------ backup

    /** Manual backup into the document the user just created. False when it could not be written. */
    suspend fun backupTo(target: Uri): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            try {
                val zip = buildArchive(now)
                writeDocument(target, zip)
                zip.delete()
                settings.recordBackupSuccess(now, automatic = false)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "manual backup failed", e)
                // Do not leave an empty or partial document behind.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
                false
            }
        }
    }

    /**
     * Automatic backup into the picked folder: writes today's dated file (replacing one of the
     * same day) and keeps the newest [BackupFiles.KEEP_AUTOMATIC]. A failure — no folder, the
     * folder is gone, the permission was revoked — is recorded for the home screen.
     */
    suspend fun backupToFolder(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            try {
                val tree = folderUri() ?: throw IOException("no backup folder")
                if (!hasFolderAccess(tree)) throw IOException("no access to the backup folder")
                val zip = buildArchive(now)
                val resolver = context.contentResolver
                val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                // Today's file is written again rather than becoming "name (1).zip".
                listFolder(tree).filter { it.first == zip.name }.forEach { old ->
                    runCatching { DocumentsContract.deleteDocument(resolver, old.second) }
                }
                val created = DocumentsContract.createDocument(resolver, folder, ZIP_MIME, zip.name) ?: throw IOException("cannot create the backup file")
                try {
                    writeDocument(created, zip)
                } catch (e: Exception) {
                    // A partial file must not count as one of the kept backups.
                    runCatching { DocumentsContract.deleteDocument(resolver, created) }
                    throw e
                }
                zip.delete()
                val files = listFolder(tree)
                val doomed = BackupFiles.toDelete(files.map { it.first }).toSet()
                files.filter { it.first in doomed }.forEach { old ->
                    runCatching { DocumentsContract.deleteDocument(resolver, old.second) }
                }
                settings.recordBackupSuccess(now, automatic = true)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "automatic backup failed", e)
                settings.recordAutoBackupFailure(now)
                false
            }
        }
    }

    /**
     * Remembers the folder for the automatic backups: takes the persistable read + write
     * permission on [tree] (released for a previously picked folder) and stores it with its name.
     * False when the permission could not be taken.
     */
    suspend fun useFolder(tree: Uri): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(tree, flags)
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot keep the folder permission", e)
            return@withContext false
        }
        val previous = folderUri()
        if (previous != null && previous != tree) {
            runCatching { resolver.releasePersistableUriPermission(previous, flags) }
        }
        settings.setBackupFolder(tree.toString(), folderName(tree))
        true
    }

    private suspend fun folderUri(): Uri? =
        settings.current().backupTreeUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }

    private fun hasFolderAccess(tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun folderName(tree: Uri): String {
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name: String? = runCatching {
            context.contentResolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return name.orEmpty()
    }

    /** The files directly inside the picked folder: display name and document URI. */
    private fun listFolder(tree: Uri): List<Pair<String, Uri>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val files = ArrayList<Pair<String, Uri>>()
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id: String = c.getString(0) ?: continue
                val name: String = c.getString(1) ?: continue
                files.add(name to DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
        }
        return files
    }

    private fun writeDocument(target: Uri, source: File) {
        val out = context.contentResolver.openOutputStream(target, "w") ?: throw IOException("cannot open the target for writing")
        out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
    }

    /** Builds the backup zip in the cache and returns it (named `drift-backup-YYYY-MM-DD.zip`). */
    private suspend fun buildArchive(nowMillis: Long): File {
        val entries = settings.exportAll()
        val dir = File(context.cacheDir, BACKUP_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val zip = File(dir, BackupFiles.fileName(LocalDate.now()))
        val db: SupportSQLiteDatabase = database().openHelper.writableDatabase
        val meta = BackupMeta(appVersionCode = BuildConfig.VERSION_CODE, dbVersion = db.version, createdAt = nowMillis)
        val dbFile = context.getDatabasePath(DriftDatabase.NAME)
        val walFile = File(dbFile.path + WAL_SUFFIX)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { out ->
            putBytes(out, BackupFiles.META, BackupMetaCodec.encode(meta).toByteArray(Charsets.UTF_8))
            putBytes(out, BackupFiles.SETTINGS, SettingsCodec.encode(entries).toByteArray(Charsets.UTF_8))
            // Everything committed so far moves from the write-ahead log into the database file…
            checkpoint(db)
            // …and a held write transaction keeps both files still while they are copied.
            db.beginTransactionNonExclusive()
            try {
                putFile(out, BackupFiles.DATABASE, dbFile)
                if (walFile.exists() && walFile.length() > 0L) putFile(out, BackupFiles.DATABASE_WAL, walFile)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return zip
    }

    /** `PRAGMA wal_checkpoint(TRUNCATE)`: a full checkpoint that also empties the log file. */
    private fun checkpoint(db: SupportSQLiteDatabase) {
        runCatching { db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
    }

    private fun putBytes(out: ZipOutputStream, name: String, bytes: ByteArray) {
        out.putNextEntry(ZipEntry(name))
        out.write(bytes)
        out.closeEntry()
    }

    private fun putFile(out: ZipOutputStream, name: String, file: File) {
        out.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(out) }
        out.closeEntry()
    }

    // ----------------------------------------------------------------- restore

    /**
     * Step 1 of a restore: reads and checks the picked file without touching the current data.
     * On [RestoreCheck.Ready] the backup's database waits, already opened once by Room (so already
     * migrated), on a side file for [restore]; call [discardPreparedRestore] if the user cancels.
     */
    suspend fun prepareRestore(source: Uri): RestoreCheck = mutex.withLock {
        withContext(Dispatchers.IO) {
            clearPrepared()
            val dir = File(context.cacheDir, RESTORE_DIR).apply { mkdirs() }
            val dbFile = File(dir, BackupFiles.DATABASE)
            val walFile = File(dir, BackupFiles.DATABASE_WAL)
            var metaText: String? = null
            var settingsText: String? = null
            try {
                val input = context.contentResolver.openInputStream(source) ?: throw IOException("cannot open the backup")
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        // Only the known names are read; nothing else in the archive reaches the disk.
                        when (entry.name) {
                            BackupFiles.META -> metaText = readText(zip)
                            BackupFiles.SETTINGS -> settingsText = readText(zip)
                            BackupFiles.DATABASE -> copyLimited(zip, dbFile)
                            BackupFiles.DATABASE_WAL -> copyLimited(zip, walFile)
                            else -> Unit
                        }
                        zip.closeEntry()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "cannot read the backup", e)
                clearPrepared()
                return@withContext RestoreCheck.Unreadable
            }

            val meta: BackupMeta? = metaText?.let { BackupMetaCodec.decode(it) }
            val fileVersion: Int? = if (dbFile.exists()) SqliteHeader.userVersion(readHeader(dbFile)) else null
            val appVersion: Int = database().openHelper.writableDatabase.version
            when (BackupValidator.check(meta, fileVersion, appVersion)) {
                BackupCheck.UNREADABLE -> {
                    clearPrepared()
                    return@withContext RestoreCheck.Unreadable
                }
                BackupCheck.NEWER_DATABASE -> {
                    clearPrepared()
                    return@withContext RestoreCheck.NewerVersion
                }
                BackupCheck.OK -> Unit
            }
            val entries: List<PrefEntry>? = settingsText?.let { SettingsCodec.decode(it) }
            if (meta == null || entries == null) {
                clearPrepared()
                return@withContext RestoreCheck.Unreadable
            }

            // The real test: Room opens the backup's database on a side file — migrations from an
            // older version and the schema check run here, not on the user's data.
            val check = context.getDatabasePath(CHECK_NAME)
            try {
                check.parentFile?.mkdirs()
                moveFile(dbFile, check)
                if (walFile.exists() && walFile.length() > 0L) moveFile(walFile, File(check.path + WAL_SUFFIX))
                val probe = DriftDatabase.buildForCheck(context, CHECK_NAME)
                try {
                    val opened = probe.openHelper.writableDatabase
                    val sound = opened.query("PRAGMA quick_check").use { c -> c.moveToFirst() && c.getString(0) == "ok" }
                    if (!sound) throw IOException("the backup database is damaged")
                    opened.query("SELECT COUNT(*) FROM transactions").use { it.moveToFirst() }
                    opened.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                } finally {
                    probe.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "the backup database cannot be opened", e)
                clearPrepared()
                return@withContext RestoreCheck.Unreadable
            }
            File(dir, BackupFiles.DATABASE).delete()
            RestoreCheck.Ready(meta, entries)
        }
    }

    /** The user cancelled the confirmation: drop what [prepareRestore] unpacked. */
    suspend fun discardPreparedRestore() = mutex.withLock {
        withContext(Dispatchers.IO) { clearPrepared() }
    }

    /**
     * Step 2 of a restore: the settings and the database are replaced by the prepared backup and
     * the app process is restarted — on success this function does not return. It returns false
     * when the replacement failed; the previous database and settings are then back in place.
     */
    suspend fun restore(ready: RestoreCheck.Ready): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val check = context.getDatabasePath(CHECK_NAME)
            if (!check.exists()) return@withContext false
            val previousSettings: List<PrefEntry> = try {
                settings.exportAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "cannot read the current settings", e)
                return@withContext false
            }
            val swapped = try {
                settings.importAll(ready.entries, ready.meta.createdAt)
                swapDatabase(check)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "restore failed", e)
                false
            }
            if (!swapped) {
                runCatching { settings.importAll(previousSettings, 0L) }
                clearPrepared()
                return@withContext false
            }
            clearPrepared()
            restartApp()
            true
        }
    }

    /**
     * Puts the checked database in place of the live one. The live files are first moved aside and
     * only deleted once the new file is in place; if that fails they are moved back.
     */
    private fun swapDatabase(check: File): Boolean {
        val live = context.getDatabasePath(DriftDatabase.NAME)
        val liveWal = File(live.path + WAL_SUFFIX)
        val liveShm = File(live.path + SHM_SUFFIX)
        val asideDb = File(live.path + ASIDE_SUFFIX)
        val asideWal = File(live.path + ASIDE_SUFFIX + WAL_SUFFIX)
        val checkWal = File(check.path + WAL_SUFFIX)
        // Closing checkpoints the live log into its file and releases the connections.
        runCatching { database().close() }
        asideDb.delete()
        asideWal.delete()
        if (live.exists() && !live.renameTo(asideDb)) return false
        if (liveWal.exists()) liveWal.renameTo(asideWal)
        liveShm.delete()
        if (!check.renameTo(live)) {
            // Put the previous database back exactly as it was.
            live.delete()
            if (asideDb.exists()) asideDb.renameTo(live)
            if (asideWal.exists()) asideWal.renameTo(liveWal)
            return false
        }
        // A log file left at the live name would belong to another database: replace or remove it.
        liveWal.delete()
        if (checkWal.exists() && checkWal.length() > 0L) checkWal.renameTo(liveWal)
        liveShm.delete()
        asideDb.delete()
        asideWal.delete()
        return true
    }

    /** Starts the launcher activity in a fresh task and ends this process, so everything reopens on the restored data. */
    private fun restartApp() {
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        if (component != null) {
            runCatching { context.startActivity(Intent.makeRestartActivityTask(component)) }
        }
        Runtime.getRuntime().exit(0)
    }

    private fun clearPrepared() {
        File(context.cacheDir, RESTORE_DIR).listFiles()?.forEach { it.delete() }
        val check = context.getDatabasePath(CHECK_NAME)
        check.delete()
        File(check.path + WAL_SUFFIX).delete()
        File(check.path + SHM_SUFFIX).delete()
        File(check.path + "-journal").delete()
    }

    private fun moveFile(from: File, to: File) {
        to.delete()
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    private fun readHeader(file: File): ByteArray {
        val header = ByteArray(SqliteHeader.SIZE)
        var filled = 0
        file.inputStream().use { input ->
            while (filled < header.size) {
                val read = input.read(header, filled, header.size - filled)
                if (read < 0) break
                filled += read
            }
        }
        return if (filled == header.size) header else ByteArray(0)
    }

    /** Reads a small text entry; an entry larger than [MAX_TEXT_BYTES] is not a Drift file. */
    private fun readText(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_TEXT_BYTES) throw IOException("text entry too large")
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Unpacks one entry to [target], refusing anything beyond [MAX_DATABASE_BYTES]. */
    private fun copyLimited(input: InputStream, target: File) {
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        FileOutputStream(target).use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_DATABASE_BYTES) throw IOException("database entry too large")
                out.write(buffer, 0, read)
            }
        }
    }

    companion object {
        private const val TAG = "Drift.Backup"

        /** The type asked of the system file picker for a new backup document. */
        const val ZIP_MIME = "application/zip"

        /** The types a backup may be listed under by a storage provider (the restore picker). */
        val RESTORE_MIME_TYPES: Array<String> = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

        private const val BACKUP_DIR = "backup"
        private const val RESTORE_DIR = "restore"

        /** The side file on which a backup's database is opened before it may replace the real one. */
        private const val CHECK_NAME = "drift-restore-check.db"
        private const val WAL_SUFFIX = "-wal"
        private const val SHM_SUFFIX = "-shm"
        private const val ASIDE_SUFFIX = ".before-restore"
        private const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        private const val MAX_DATABASE_BYTES = 512L * 1024 * 1024
    }
}
