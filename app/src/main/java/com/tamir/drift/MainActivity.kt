package com.tamir.drift

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tamir.drift.di.AppGraph
import com.tamir.drift.notify.DeepLink
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.components.ProvideFeedback
import com.tamir.drift.ui.nav.DriftApp
import com.tamir.drift.ui.release.WhatsNewHost
import com.tamir.drift.ui.theme.DriftTheme
import com.tamir.drift.update.LocalUpdateController
import com.tamir.drift.update.UpdateController
import com.tamir.drift.update.UpdateDialogHost

class MainActivity : ComponentActivity() {

    /** The screen a tapped notification asked for ([DeepLink]); the navigation host consumes it once. */
    private val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(this)
        // The three feedback sounds are loaded while the first screen is drawn.
        graph.sounds.warmUp()
        // Only a fresh launch: after a rotation the same intent must not navigate again.
        if (savedInstanceState == null) readDeepLink(intent)
        setContent {
            val updateController = remember { UpdateController() }
            CompositionLocalProvider(LocalAppGraph provides graph, LocalUpdateController provides updateController) {
                DriftTheme {
                    // Motion, haptics and sound follow the three "משוב" switches in settings.
                    ProvideFeedback {
                        NotificationPermissionRequest()
                        DriftApp(deepLink = pendingDeepLink.value, onDeepLinkConsumed = { pendingDeepLink.value = null })
                        UpdateDialogHost()
                        WhatsNewHost()
                    }
                }
            }
        }
    }

    /** The "captured" sound is for a capture that arrives while the app is on screen — only then. */
    override fun onStart() {
        super.onStart()
        val graph = AppGraph.get(this)
        graph.appInForeground = true
        // Back on screen: if the date changed while the app was away, every screen moves to the new day.
        graph.refreshToday()
    }

    override fun onStop() {
        AppGraph.get(this).appInForeground = false
        super.onStop()
    }

    /** The activity is singleTask: a notification tapped while the app is open arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeepLink(intent)
    }

    private fun readDeepLink(source: Intent?) {
        val launch = source ?: return
        val target = launch.getStringExtra(DeepLink.EXTRA) ?: return
        // Consumed here too, so the extra does not fire again when the activity is recreated.
        launch.removeExtra(DeepLink.EXTRA)
        pendingDeepLink.value = target
    }
}

/** Asks for POST_NOTIFICATIONS once on Android 13+ (budget alerts + weekly summary). */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
