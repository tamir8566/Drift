package com.tamir.drift.ui

import com.tamir.drift.R
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

/**
 * "נשמרה משמרת · 304 ₪" (the day total: wage + travel) after a shift was saved, with "ביטול":
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
