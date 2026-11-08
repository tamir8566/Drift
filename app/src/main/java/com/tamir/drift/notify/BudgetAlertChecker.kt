package com.tamir.drift.notify

import android.content.Context
import com.tamir.drift.R
import com.tamir.drift.data.db.AlertSentEntity
import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.repo.MonthSummaryUseCase
import com.tamir.drift.data.settings.SettingsStore
import com.tamir.drift.domain.budget.AlertLevel
import com.tamir.drift.domain.budget.BudgetAlert
import com.tamir.drift.ui.format.Fmt
import java.time.YearMonth
import java.time.ZoneId

/**
 * PRD: 80% and 100% alerts for the total budget and for each category budget, each sent at
 * most once per month. Called after every captured/added transaction.
 */
class BudgetAlertChecker(
    private val context: Context,
    private val db: DriftDatabase,
    private val settings: SettingsStore,
    private val notifier: Notifier,
    private val summaryUseCase: MonthSummaryUseCase,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun checkAndNotify(nowMillis: Long = System.currentTimeMillis()) {
        val s = settings.current()
        if (!s.alert80Enabled && !s.alert100Enabled) return

        val month = YearMonth.now(zone)
        val summary = summaryUseCase.build(
            transactions = db.transactions().getAll(),
            categories = db.categories().getAll(),
            budgets = db.budgets().getAll(),
            month = month,
            nowMillis = nowMillis,
        )
        val monthKey = month.toString()
        val sent = db.alertsSent().keysForMonth(monthKey).toSet()
        val categoryNames = db.categories().getAll().associate { it.id to it.name }

        val reached = com.tamir.drift.domain.budget.BudgetCalculator.reachedAlerts(summary)
            .filter { (it.level == AlertLevel.WARNING && s.alert80Enabled) || (it.level == AlertLevel.EXCEEDED && s.alert100Enabled) }
            // When both thresholds are reached at once only the stronger one is new.
            .groupBy { it.categoryKey }
            .flatMap { (_, list) ->
                val unsent = list.filter { key(monthKey, it) !in sent }
                if (unsent.any { it.level == AlertLevel.EXCEEDED }) unsent.filter { it.level == AlertLevel.EXCEEDED } else unsent
            }

        for (alert in reached) {
            val k = key(monthKey, alert)
            // Mark as sent before posting so a crash never double-notifies.
            db.alertsSent().insert(AlertSentEntity(k, nowMillis))
            // If the weaker alert was skipped because the stronger fired, mark it too.
            if (alert.level == AlertLevel.EXCEEDED) {
                db.alertsSent().insert(AlertSentEntity(key(monthKey, alert.copy(level = AlertLevel.WARNING)), nowMillis))
            }
            val scope = alert.categoryKey?.let { categoryNames[it] ?: it }
            val (title, text) = when (alert.level) {
                AlertLevel.WARNING -> {
                    val t = if (scope == null) context.getString(R.string.alert80_title_total)
                    else context.getString(R.string.alert80_title_category, scope)
                    t to context.getString(R.string.alert80_text, Fmt.money(alert.remaining), summary.daysLeft)
                }
                AlertLevel.EXCEEDED -> {
                    val t = if (scope == null) context.getString(R.string.alert100_title_total)
                    else context.getString(R.string.alert100_title_category, scope)
                    t to context.getString(R.string.alert100_text, Fmt.money(alert.overBy))
                }
            }
            notifier.notifyBudget(Notifier.ID_BUDGET_BASE + k.hashCode().and(0x7FFF), title, text)
        }
        db.alertsSent().pruneOlderThan(nowMillis - 120L * 24 * 60 * 60 * 1000)
    }

    private fun key(monthKey: String, a: BudgetAlert) = "$monthKey|${a.categoryKey ?: "total"}|${a.level.percent}"
}
