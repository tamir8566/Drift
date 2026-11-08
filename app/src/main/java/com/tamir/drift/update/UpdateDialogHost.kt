package com.tamir.drift.update

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tamir.drift.R
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.CategoryBar
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.launch

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
    var progress by remember(manifest) { mutableFloatStateOf(0f) }
    var error by remember(manifest) { mutableStateOf<String?>(null) }
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
            downloading = true; error = null
            scope.launch {
                try {
                    val file = graph.updateChecker.download(manifest) { progress = it }
                    context.startActivity(graph.updateChecker.installIntent(file))
                    controller.dismiss()
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                } finally {
                    downloading = false
                }
            }
        },
    ) {
        Text(stringResource(R.string.update_body, manifest.versionName.ifBlank { manifest.versionCode.toString() }), style = DriftType.Body, color = DriftColors.Text)
        if (manifest.notes.isNotBlank()) Text(manifest.notes, style = DriftType.Label12, color = DriftColors.TextSecondary)
        if (downloading) {
            CategoryBar(progress, DriftColors.Lime)
            Text(stringResource(R.string.update_downloading, (progress * 100).toInt()), style = DriftType.Label12, color = DriftColors.TextSecondary)
        }
        if (needPermission) Text(stringResource(R.string.update_need_permission), style = DriftType.Label12, color = DriftColors.Orange)
        error?.let { Text(stringResource(R.string.update_failed, it), style = DriftType.Label12, color = DriftColors.Orange) }
    }
}
