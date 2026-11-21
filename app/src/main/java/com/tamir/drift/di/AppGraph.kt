package com.tamir.drift.di

import android.content.Context
import com.tamir.drift.backup.BackupManager
import com.tamir.drift.capture.CaptureProcessor
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.repo.BudgetRepository
import com.tamir.drift.data.repo.CategoryRepository
import com.tamir.drift.data.repo.MonthSummaryUseCase
import com.tamir.drift.data.repo.SavingsRepository
import com.tamir.drift.data.repo.TransactionRepository
import com.tamir.drift.data.repo.WorkRepository
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.feedback.DriftSound
import com.tamir.drift.feedback.SoundPlayer
import com.tamir.drift.notify.BudgetAlertChecker
import com.tamir.drift.notify.Notifier
import com.tamir.drift.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * One short line for the user, shown by the root of the UI. With [actionLabel] and [action] the
 * line carries a link ("ביטול") that runs the action — the undo of what the message reports.
 */
class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (suspend () -> Unit)? = null,
    val durationMillis: Long = DEFAULT_MILLIS,
) {
    companion object {
        const val DEFAULT_MILLIS = 3_500L

        /** How long a message with an undo link stays. */
        const val UNDO_MILLIS = 6_000L
    }
}

/** Simple manual dependency graph; one instance per process. */
class AppGraph private constructor(val appContext: Context) {

    val db: DriftDatabase by lazy { DriftDatabase.build(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val categories: CategoryRepository by lazy { CategoryRepository(appContext, db) }
    val transactions: TransactionRepository by lazy { TransactionRepository(db, categories) }
    val budgets: BudgetRepository by lazy { BudgetRepository(db) }
    val savings: SavingsRepository by lazy { SavingsRepository(db) }
    val work: WorkRepository by lazy { WorkRepository(appContext, db) }
    val monthSummary: MonthSummaryUseCase by lazy { MonthSummaryUseCase() }
    val notifier: Notifier by lazy { Notifier(appContext) }
    val alertChecker: BudgetAlertChecker by lazy { BudgetAlertChecker(appContext, db, settings, notifier, monthSummary) }
    val captureProcessor: CaptureProcessor by lazy {
        CaptureProcessor(
            db, categories, alertChecker, settings,
            // The "captured" sound belongs to the open app only — never to a capture in the background.
            onCaptured = { if (appInForeground) sounds.play(DriftSound.CAPTURED) },
        )
    }
    val updateChecker: UpdateChecker by lazy { UpdateChecker(appContext, settings) }

    /** Backup and restore of the database and the settings (manual, and the weekly automatic one). */
    val backup: BackupManager by lazy { BackupManager(appContext, { db }, settings) }

    /** The three feedback sounds; silent unless the "צלילים" switch is on and the ringer is in normal mode. */
    val sounds: SoundPlayer by lazy { SoundPlayer(appContext, settings, scope) }

    /** True while the main screen is started (visible); the activity sets it in onStart / onStop. */
    @Volatile var appInForeground: Boolean = false

    /**
     * Short one-line messages for the user ("עודכנו 3 הוצאות של …"); the root of the UI shows each
     * for a few seconds. A screen may emit and leave — the message outlives it.
     */
    val messages: MutableSharedFlow<UiMessage> = MutableSharedFlow(extraBufferCapacity = 8)

    /**
     * For work that has to finish after the screen that started it is gone: a save or a delete
     * followed by leaving the screen, and the undo that the root message runs later.
     */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun showMessage(text: String) {
        messages.tryEmit(UiMessage(text))
    }

    /** A message with an undo link: "נמחק · ביטול". [undo] runs when the link is tapped. */
    fun showUndoMessage(text: String, actionLabel: String, undo: suspend () -> Unit) {
        messages.tryEmit(UiMessage(text, actionLabel, undo, UiMessage.UNDO_MILLIS))
    }

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
