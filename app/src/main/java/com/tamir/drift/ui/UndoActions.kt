package com.tamir.drift.ui

import com.tamir.drift.R
import com.tamir.drift.capture.CaptureProcessor
import com.tamir.drift.data.db.MerchantRuleEntity
import com.tamir.drift.data.db.ParsePatternEntity
import com.tamir.drift.data.db.SavingsDepositEntity
import com.tamir.drift.data.db.SavingsGoalEntity
import com.tamir.drift.data.db.ShiftEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.data.db.totalAgorot
import com.tamir.drift.di.AppGraph
import com.tamir.drift.ui.format.Fmt
import kotlinx.coroutines.launch

/**
 * Actions that happen at once and can be taken back from the root message ("נמחק · ביטול")
 * instead of asking "are you sure?" first. They run on the app scope, so the screen that
 * started them may close right away.
 */

/** Deletes the expense now; "ביטול" writes the same row back. */
fun AppGraph.deleteTransactionWithUndo(tx: TransactionEntity) {
    scope.launch {
        transactions.delete(tx.id)
        alertChecker.checkAndNotify()
        showUndoMessage(appContext.getString(R.string.tx_deleted), appContext.getString(R.string.cancel)) {
            transactions.restore(tx)
            alertChecker.checkAndNotify()
        }
    }
}

/** Deletes the shift now; "ביטול" writes the same row back. */
fun AppGraph.deleteShiftWithUndo(shift: ShiftEntity) {
    scope.launch {
        work.deleteShift(shift.id)
        showUndoMessage(appContext.getString(R.string.shift_deleted), appContext.getString(R.string.cancel)) {
            work.restoreShift(shift)
        }
    }
}

/** Deletes the learned rule now; "ביטול" stores it again. */
fun AppGraph.deleteLearnedRuleWithUndo(rule: MerchantRuleEntity) {
    scope.launch {
        categories.deleteLearnedRule(rule.keyword)
        showUndoMessage(appContext.getString(R.string.rule_deleted), appContext.getString(R.string.cancel)) {
            categories.restoreLearnedRule(rule)
        }
    }
}

/** Deletes the saved parse pattern now; "ביטול" writes the same row back. */
fun AppGraph.deletePatternWithUndo(pattern: ParsePatternEntity) {
    scope.launch {
        db.parsePatterns().delete(pattern.id)
        showUndoMessage(appContext.getString(R.string.pattern_deleted), appContext.getString(R.string.cancel)) {
            db.parsePatterns().upsert(pattern)
        }
    }
}

/**
 * Deletes the savings goal together with its deposits (the screen asked first: this removes a
 * whole history); "ביטול" writes the goal and every deposit back.
 */
fun AppGraph.deleteSavingsGoalWithUndo(goal: SavingsGoalEntity, deposits: List<SavingsDepositEntity>) {
    scope.launch {
        savings.delete(goal.id)
        showUndoMessage(appContext.getString(R.string.savings_goal_deleted), appContext.getString(R.string.cancel)) {
            savings.restore(goal, deposits)
        }
    }
}

/**
 * After "הוספה כהוצאה" in the calibration screen, which sends the pasted text through the real
 * capture: says what came of it. An expense that was added is a real one — it can be taken back.
 */
fun AppGraph.announceTestCapture(outcome: CaptureProcessor.Outcome) {
    when (outcome) {
        is CaptureProcessor.Outcome.Inserted -> showUndoMessage(
            appContext.getString(R.string.calib_test_added), appContext.getString(R.string.cancel),
        ) {
            transactions.delete(outcome.id)
            alertChecker.checkAndNotify()
        }
        is CaptureProcessor.Outcome.Duplicate -> showMessage(appContext.getString(R.string.calib_test_duplicate))
        else -> showMessage(appContext.getString(R.string.calib_test_nothing))
    }
}

/**
 * "נשמרה משמרת · 400 ₪" (the day total: wage + travel) after a shift was saved, with "ביטול":
 * a new shift is removed again, an edited one gets [previous] — its row before the edit — back.
 */
suspend fun AppGraph.announceShiftSaved(shiftId: Long, previous: ShiftEntity?) {
    val saved = work.getShift(shiftId) ?: return
    showUndoMessage(
        appContext.getString(R.string.shift_saved_message, Fmt.moneyExact(saved.totalAgorot)),
        appContext.getString(R.string.cancel),
    ) {
        if (previous == null) work.deleteShift(shiftId) else work.restoreShift(previous)
    }
}

/** "נשמרה הוצאה · 42 ₪" after a manual expense was added, with "ביטול" that removes it again. */
fun AppGraph.announceExpenseSaved(id: Long, amountMineAgorot: Long) {
    showUndoMessage(
        appContext.getString(R.string.tx_saved_message, Fmt.moneyExact(amountMineAgorot)),
        appContext.getString(R.string.cancel),
    ) {
        transactions.delete(id)
        alertChecker.checkAndNotify()
    }
}

/** The "עודכנו N הוצאות של …" line after a category change moved other expenses of the merchant too. */
fun AppGraph.announceRecategorized(moved: Int, merchant: String) {
    if (moved == 1) showMessage(appContext.getString(R.string.recat_message_one, merchant))
    else if (moved > 1) showMessage(appContext.getString(R.string.recat_message_many, moved, merchant))
}
