package com.tamir.drift.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.model.TransactionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Captures Google Pay / Google Wallet transaction notifications. Only packages from the
 * configurable list are considered; title and body are joined with a newline for the parser.
 */
class DriftNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val combined = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
        if (combined.isBlank()) return
        val postTime = sbn.postTime

        scope.launch {
            val graph = AppGraph.get(applicationContext)
            val packages = graph.settings.current().notificationPackages
            if (pkg !in packages) return@launch
            // Group summaries repeat the children; skip them.
            if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@launch
            graph.captureProcessor.process(TransactionSource.GPAY, pkg, combined, postTime)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
