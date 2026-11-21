package com.tamir.drift.di

import android.content.Context
import com.tamir.drift.capture.CaptureProcessor
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.repo.BudgetRepository
import com.tamir.drift.data.repo.CategoryRepository
import com.tamir.drift.data.repo.MonthSummaryUseCase
import com.tamir.drift.data.repo.SavingsRepository
import com.tamir.drift.data.repo.TransactionRepository
import com.tamir.drift.data.repo.WorkRepository
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.notify.BudgetAlertChecker
import com.tamir.drift.notify.Notifier
import com.tamir.drift.update.UpdateChecker
import kotlinx.coroutines.flow.MutableSharedFlow

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
    val captureProcessor: CaptureProcessor by lazy { CaptureProcessor(db, categories, alertChecker, settings) }
    val updateChecker: UpdateChecker by lazy { UpdateChecker(appContext, settings) }

    /**
     * Short one-line messages for the user ("עודכנו 3 הוצאות של …"); the root of the UI shows each
     * for a few seconds. A screen may emit and leave — the message outlives it.
     */
    val messages: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 8)

    fun showMessage(text: String) {
        messages.tryEmit(text)
    }

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
