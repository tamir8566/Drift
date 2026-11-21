package com.tamir.drift

import android.app.Application
import com.tamir.drift.capture.CaptureStatus
import com.tamir.drift.di.AppGraph
import com.tamir.drift.notify.DailyCheckScheduler
import com.tamir.drift.notify.WeeklySummaryScheduler
import com.tamir.drift.notify.WorkSummaryScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DriftApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val graph = AppGraph.get(this)
        graph.notifier.ensureChannels()
        // Reconnect the notification listener if the system left it unbound (e.g. after an update).
        CaptureStatus.requestRebind(this)
        scope.launch {
            graph.categories.ensureSeeded()
            graph.work.ensureSeeded()
            graph.settings.ensureTrackingStart()
            WeeklySummaryScheduler.schedule(this@DriftApp, graph.settings.current())
            WorkSummaryScheduler.schedule(this@DriftApp)
            DailyCheckScheduler.schedule(this@DriftApp)
        }
    }
}
