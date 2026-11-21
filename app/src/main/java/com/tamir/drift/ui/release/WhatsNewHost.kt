package com.tamir.drift.ui.release

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamir.drift.BuildConfig
import com.tamir.drift.R
import com.tamir.drift.domain.release.WhatsNew
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.DriftDialog
import com.tamir.drift.ui.theme.DriftColors
import com.tamir.drift.ui.theme.DriftType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The bundled release notes: Hebrew, short lines; a line that starts with "#" is a small heading. */
private const val NOTES_ASSET = "release-notes.he.txt"

/**
 * "מה חדש": after the app starts on a new version whose bundled notes differ from the ones shown
 * last, they are shown once in a small dialog ([WhatsNew]). Closing it records the version and the
 * notes; a first install records them without showing anything.
 */
@Composable
fun WhatsNewHost() {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    var notes by remember { mutableStateOf<String?>(null) }
    var fingerprint by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val text: String = withContext(Dispatchers.IO) { readNotes(context) }
        val current = WhatsNew.fingerprint(text)
        val seen = graph.settings.whatsNewSeen()
        when (WhatsNew.decide(BuildConfig.VERSION_CODE, seen.first, current, seen.second, isFreshInstall(context))) {
            WhatsNew.Decision.SHOW -> {
                fingerprint = current
                notes = text
            }
            WhatsNew.Decision.RECORD -> graph.settings.setWhatsNewSeen(BuildConfig.VERSION_CODE, current.takeIf { it.isNotEmpty() })
            WhatsNew.Decision.NONE -> Unit
        }
    }

    val shown: String = notes ?: return
    val shownFingerprint = fingerprint
    val close: () -> Unit = {
        notes = null
        // On the app scope: recorded even if the screen goes away at the same moment.
        graph.scope.launch { graph.settings.setWhatsNewSeen(BuildConfig.VERSION_CODE, shownFingerprint) }
    }
    DriftDialog(
        title = stringResource(R.string.whats_new_title),
        onDismiss = close,
        confirmText = stringResource(R.string.whats_new_close),
        onConfirm = close,
    ) {
        WhatsNew.lines(shown).forEach { line ->
            if (line.isHeading) {
                Text(line.text, style = DriftType.SectionTitle, color = DriftColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(5.dp).background(DriftColors.Lime, CircleShape))
                    Text(line.text, style = DriftType.Label13, color = DriftColors.Text, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun readNotes(context: Context): String = runCatching {
    context.assets.open(NOTES_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
}.getOrDefault("")

/** True when the app was installed and never updated since: there is nothing "new" to tell. */
private fun isFreshInstall(context: Context): Boolean = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.firstInstallTime == info.lastUpdateTime
}.getOrDefault(false)
