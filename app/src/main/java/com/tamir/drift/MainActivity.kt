package com.tamir.drift

import android.Manifest
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.tamir.drift.di.AppGraph
import com.tamir.drift.ui.LocalAppGraph
import com.tamir.drift.ui.nav.DriftApp
import com.tamir.drift.ui.theme.DriftTheme
import com.tamir.drift.update.LocalUpdateController
import com.tamir.drift.update.UpdateController
import com.tamir.drift.update.UpdateDialogHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = AppGraph.get(this)
        setContent {
            val updateController = remember { UpdateController() }
            CompositionLocalProvider(LocalAppGraph provides graph, LocalUpdateController provides updateController) {
                DriftTheme {
                    NotificationPermissionRequest()
                    DriftApp()
                    UpdateDialogHost()
                }
            }
        }
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
