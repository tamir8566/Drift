package com.tamir.drift.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamir.drift.data.db.CategoryEntity
import com.tamir.drift.data.db.Origin
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.repo.Money
import com.tamir.drift.data.repo.toShiftSummaries
import com.tamir.drift.data.repo.toSummaries
import com.tamir.drift.di.AppGraph
import com.tamir.drift.domain.backup.BackupNudge
import com.tamir.drift.domain.budget.CategorySpend
import com.tamir.drift.domain.budget.MonthSummary
import com.tamir.drift.domain.classify.UnsortedMerchants
import com.tamir.drift.domain.model.MerchantRuleModel
import com.tamir.drift.domain.time.sinceStart
import com.tamir.drift.domain.work.GoalCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val summary: MonthSummary? = null,
    /** Top 4 categories by spend (PRD: 2×2 grid). */
    val topCategories: List<CategorySpend> = emptyList(),
    val categories: Map<String, CategoryEntity> = emptyMap(),
    val recent: List<TransactionEntity> = emptyList(),
    val unparsedCount: Int = 0,
    /** PRD 8 Nov: shifts income this month (shekels). */
    val earnedThisMonth: Double = 0.0,
    /** PRD 8 Nov: income − my share of expenses (shekels); may be negative. The same figure as the goal screen's "נחסך החודש". */
    val savedThisMonth: Double = 0.0,
    /** True when SMS senders are configured, i.e. the SMS permission matters for capture. */
    val smsCaptureConfigured: Boolean = true,
    /** True when notification packages are configured, i.e. notification access matters for capture. */
    val notificationCaptureConfigured: Boolean = true,
    /** The goal screen's "בכיוון" / "לא בכיוון" for this month; null until loaded. */
    val goalOnTrack: Boolean? = null,
    /** Merchants whose expenses sit in "אחר" with no learned rule ("N בתי עסק מחכים לסיווג"). */
    val unsortedMerchants: Int = 0,
    /** The weekly automatic backup is on and its last run failed ("הגיבוי האוטומטי נכשל · לבדוק"). */
    val backupFailed: Boolean = false,
    /** No backup of any kind yet, a fortnight after the first launch ("אין גיבוי לנתונים · להגדיר"). */
    val backupNudge: Boolean = false,
)

class HomeViewModel(private val graph: AppGraph) : ViewModel() {

    /** Categories with the merchant rules beside them (the typed combine below takes five flows). */
    private val catalog = combine(graph.categories.categories, graph.categories.rules) { cats, rules -> cats to rules }

    val state: StateFlow<HomeUiState> = combine(
        graph.transactions.all,
        catalog,
        graph.budgets.all,
        graph.work.shifts,
        graph.daySettings,
    ) { txs, (cats, rules), budgets, shifts, settings ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        // Tracking start: totals, "השבוע" and "מול חודש קודם" only count records from it on
        // (a previous month before the start has no spend → the existing "—").
        val window = settings.trackingWindow
        val summary = graph.monthSummary.build(txs, cats, budgets, month, window)
        val top = summary.categories.filter { it.spent > 0 }.take(4).ifEmpty { summary.categories.take(4) }
        // Budget math is untouched; the earned/saved row is purely additive.
        // Computed exactly like the goal screen (agorot, expenses through the end of today), so
        // "נחסכו" here and "נחסך החודש" there are always the same number.
        val countedShifts = shifts.toShiftSummaries().sinceStart(window)
        val allTxSummaries = txs.toSummaries()
        val countedTxs = allTxSummaries.sinceStart(window, zone)
        val earnedAgorot = GoalCalculator.earnedIn(countedShifts, month, today.toEpochDay())
        val spentBefore = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spentAgorot = GoalCalculator.spentIn(countedTxs, month, zone, spentBefore)
        val earned = Money.toShekels(earnedAgorot)
        // The same status the goal screen shows as its pill (the main job's rate only feeds the
        // "hours missing" text there, not whether the month is on track).
        val goalStatus = GoalCalculator.monthStatus(
            shifts = countedShifts,
            transactions = countedTxs,
            month = month,
            today = today,
            zone = zone,
            goal = settings.monthlySavingGoal,
            mainRateAgorot = 0L,
            startDate = window.startDate,
        )
        val ruleModels = rules.map { MerchantRuleModel(it.keyword, it.categoryId, it.origin == Origin.LEARNED) }
        HomeUiState(
            loading = false,
            month = month,
            summary = summary,
            topCategories = top,
            categories = cats.associateBy { it.id },
            recent = txs.filter { !it.isUnparsed }.sortedByDescending { it.timestamp }.take(6),
            unparsedCount = txs.count { it.isUnparsed },
            earnedThisMonth = earned,
            savedThisMonth = Money.toShekels(earnedAgorot - spentAgorot),
            smsCaptureConfigured = settings.smsSenders.isNotEmpty(),
            notificationCaptureConfigured = settings.notificationPackages.isNotEmpty(),
            goalOnTrack = goalStatus.onTrack,
            unsortedMerchants = UnsortedMerchants.find(allTxSummaries, ruleModels).size,
            backupFailed = settings.backupAutoEnabled && settings.backupAutoFailedMillis > 0L,
            backupNudge = BackupNudge.shouldShow(
                firstLaunchEpochDay = settings.firstLaunchEpochDay,
                todayEpochDay = today.toEpochDay(),
                lastBackupMillis = settings.lastBackupMillis,
                dismissedAtMillis = settings.backupNudgeDismissedMillis,
                nowMillis = System.currentTimeMillis(),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** "אין גיבוי לנתונים" was dismissed: it stays away for 30 days. */
    fun dismissBackupNudge() {
        viewModelScope.launch { graph.settings.dismissBackupNudge(System.currentTimeMillis()) }
    }
}
