package com.tamir.drift

import android.app.Application
import com.tamir.drift.di.AppGraph
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
        scope.launch {
            graph.categories.ensureSeeded()
            graph.work.ensureSeeded()
            WeeklySummaryScheduler.schedule(this@DriftApp, graph.settings.current())
            WorkSummaryScheduler.schedule(this@DriftApp)
        }
    }
}
