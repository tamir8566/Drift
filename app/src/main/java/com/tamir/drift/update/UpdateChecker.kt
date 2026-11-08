package com.tamir.drift.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.tamir.drift.BuildConfig
import com.tamir.drift.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
) {
    fun isNewerThan(installed: Int) = versionCode > installed

    companion object {
        fun parse(json: String): UpdateManifest {
            val o = JSONObject(json)
            return UpdateManifest(
                versionCode = o.getInt("versionCode"),
                versionName = o.optString("versionName", ""),
                apkUrl = o.getString("apkUrl"),
                notes = o.optString("notes", ""),
            )
        }
    }
}

/**
 * PRD self-update: fetch manifest.json from Supabase Storage (on start at most once a day, or
 * manually from settings); when versionCode is newer → download the APK to cache → hand it to
 * the package installer through a FileProvider URI.
 */
class UpdateChecker(private val context: Context, private val settings: SettingsStore) {

    sealed class Result {
        data class Available(val manifest: UpdateManifest) : Result()
        data object UpToDate : Result()
        data class Error(val message: String) : Result()
    }

    /** Checks only when the last check is older than a day. Returns null when skipped. */
    suspend fun checkIfDue(nowMillis: Long = System.currentTimeMillis()): Result? {
        val s = settings.current()
        if (nowMillis - s.lastUpdateCheck < DAY_MILLIS) return null
        return check(nowMillis)
    }

    suspend fun check(nowMillis: Long = System.currentTimeMillis()): Result = withContext(Dispatchers.IO) {
        val url = settings.current().updateManifestUrl.ifBlank { BuildConfig.UPDATE_MANIFEST_URL }
        try {
            val json = httpGet("$url?t=$nowMillis")
            val manifest = UpdateManifest.parse(json)
            settings.setLastUpdateCheck(nowMillis)
            if (manifest.isNewerThan(BuildConfig.VERSION_CODE)) Result.Available(manifest) else Result.UpToDate
        } catch (e: Exception) {
            Log.w(TAG, "update check failed", e)
            Result.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Downloads the APK into the cache dir; returns the file. */
    suspend fun download(manifest: UpdateManifest, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "drift-${manifest.versionCode}.apk")
        val conn = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } >= 0) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        file
    }

    /** True when the app may launch the package installer for unknown sources. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000; instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "Drift.Update"
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
