package com.tamir.drift.update

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamir.drift.R
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch
import java.util.Locale

/** Lets the settings screen open the update dialog after a manual check. */
class UpdateController {
    var pending by mutableStateOf<UpdateManifest?>(null)
        private set

    fun show(manifest: UpdateManifest) { pending = manifest }
    fun dismiss() { pending = null }
}

val LocalUpdateController = staticCompositionLocalOf { UpdateController() }

/**
 * PRD: on start (max once a day) check the manifest; if a newer versionCode exists show a
 * Hebrew dialog with "עדכן עכשיו" → download to cache → package installer.
 *
 * Download progress: the bar and the "מוריד… N%" text are drawn from the same whole percent of
 * one [DownloadProgress] state, and the bar is not animated — so they always agree, also during a
 * fast download. Without a known file length there is no percent: a moving bar and "מוריד…"
 * (with the size downloaded so far) are shown instead of a stuck 0%.
 */
@Composable
fun UpdateDialogHost() {
    val graph = LocalAppGraph.current
    val controller = LocalUpdateController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        when (val r = graph.updateChecker.checkIfDue()) {
            is UpdateChecker.Result.Available -> controller.show(r.manifest)
            else -> Unit
        }
    }

    val manifest = controller.pending ?: return
    var downloading by remember(manifest) { mutableStateOf(false) }
    var progress by remember(manifest) { mutableStateOf<DownloadProgress?>(null) }
    var error by remember(manifest) { mutableStateOf<UpdateChecker.Failure?>(null) }
    var needPermission by remember(manifest) { mutableStateOf(false) }

    DriftDialog(
        title = stringResource(R.string.update_title),
        onDismiss = { if (!downloading) controller.dismiss() },
        confirmText = stringResource(R.string.update_now),
        confirmEnabled = !downloading,
        dismissText = stringResource(R.string.update_later),
        onConfirm = {
            if (!graph.updateChecker.canInstall()) {
                needPermission = true
                runCatching { context.startActivity(graph.updateChecker.unknownSourcesIntent()) }
                return@DriftDialog
            }
            downloading = true; error = null; progress = null
            scope.launch {
                try {
                    val file = graph.updateChecker.download(manifest) { progress = it }
                    context.startActivity(graph.updateChecker.installIntent(file))
                    controller.dismiss()
                } catch (e: Exception) {
                    error = UpdateChecker.failureOf(e)
                } finally {
                    downloading = false
                }
            }
        },
    ) {
        Text(stringResource(R.string.update_body, manifest.versionName.ifBlank { manifest.versionCode.toString() }), style = DriftType.Body, color = DriftColors.Text)
        // The release notes are the last commit subject (English). Only notes written in Hebrew are
        // shown; anything else is replaced by a Hebrew line.
        val notes = manifest.notes.takeIf { hasHebrew(it) } ?: stringResource(R.string.update_notes_fallback)
        Text(notes, style = DriftType.Label12, color = DriftColors.TextSecondary)
        if (downloading) {
            // One read of the state; the bar and the text below both use this value.
            val current = progress
            val percent = current?.percent
            if (percent != null) {
                DownloadBar(percent)
                Text(stringResource(R.string.update_downloading, percent), style = DriftType.Label12, color = DriftColors.TextSecondary)
            } else {
                IndeterminateDownloadBar()
                val bytes = current?.bytes ?: 0L
                Text(
                    if (bytes > 0L) stringResource(R.string.update_downloading_size, megabytes(bytes)) else stringResource(R.string.update_downloading_unknown),
                    style = DriftType.Label12, color = DriftColors.TextSecondary,
                )
            }
        }
        if (needPermission) Text(stringResource(R.string.update_need_permission), style = DriftType.Label12, color = DriftColors.Orange)
        error?.let { Text(stringResource(R.string.update_failed, stringResource(it.textRes())), style = DriftType.Label12, color = DriftColors.Orange) }
    }
}

/** The Hebrew line shown for an update failure. */
internal fun UpdateChecker.Failure.textRes(): Int = when (this) {
    UpdateChecker.Failure.NO_NETWORK -> R.string.update_error_network
    UpdateChecker.Failure.SERVER -> R.string.update_error_server
    UpdateChecker.Failure.OTHER -> R.string.update_error_other
}

/** True when [text] has at least one Hebrew letter. */
internal fun hasHebrew(text: String): Boolean = text.any { it in 'א'..'ת' }

private fun megabytes(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0))

/**
 * The download bar: same 3dp look as the category bars, but drawn directly from [percent] with
 * no animation — a tween restarted on every callback would lag behind the printed percent.
 */
@Composable
private fun DownloadBar(percent: Int) {
    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(DriftColors.BarTrack)) {
        Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).fillMaxHeight().background(DriftColors.Lime))
    }
}

/** Unknown length: a short lime segment gliding along the track, so the download visibly lives. */
@Composable
private fun IndeterminateDownloadBar() {
    val transition = rememberInfiniteTransition(label = "downloadIndeterminate")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing), RepeatMode.Restart),
        label = "downloadIndeterminatePosition",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(DriftColors.BarTrack)
            .drawBehind {
                val segment = size.width * 0.3f
                val x = -segment + (size.width + segment) * position
                drawRect(DriftColors.Lime.copy(alpha = 0.7f), topLeft = Offset(x, 0f), size = Size(segment, size.height))
            },
    )
}
