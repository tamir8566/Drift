package com.tamir.drift.di

import android.content.Context
import com.tamir.drift.capture.CaptureProcessor
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.repo.BudgetRepository
import com.tamir.drift.data.repo.CategoryRepository
import com.tamir.drift.data.repo.MonthSummaryUseCase
import com.tamir.drift.data.repo.SavingsRepository
import com.tamir.drift.data.repo.TransactionRepository
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.notify.BudgetAlertChecker
import com.tamir.drift.notify.Notifier
import com.tamir.drift.update.UpdateChecker

/** Simple manual dependency graph; one instance per process. */
class AppGraph private constructor(val appContext: Context) {

    val db: DriftDatabase by lazy { DriftDatabase.build(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val categories: CategoryRepository by lazy { CategoryRepository(appContext, db) }
    val transactions: TransactionRepository by lazy { TransactionRepository(db, categories) }
    val budgets: BudgetRepository by lazy { BudgetRepository(db) }
    val savings: SavingsRepository by lazy { SavingsRepository(db) }
    val monthSummary: MonthSummaryUseCase by lazy { MonthSummaryUseCase() }
    val notifier: Notifier by lazy { Notifier(appContext) }
    val alertChecker: BudgetAlertChecker by lazy { BudgetAlertChecker(appContext, db, settings, notifier, monthSummary) }
    val captureProcessor: CaptureProcessor by lazy { CaptureProcessor(db, categories, alertChecker) }
    val updateChecker: UpdateChecker by lazy { UpdateChecker(appContext, settings) }

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
