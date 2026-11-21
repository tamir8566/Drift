package com.tamir.drift.ui.nav

object Routes {
    const val HOME = "home"
    const val WHERE = "where"
    const val BUDGET = "budget"
    const val WORK = "work"
    const val GOAL = "goal"
    const val ADD = "add"
    const val SHIFT = "shift"
    const val SHIFTS = "shifts"
    const val DETAIL = "detail/{id}"
    const val TRANSACTIONS = "transactions"
    const val SETTINGS = "settings"
    const val CALIBRATION = "settings/calibration"
    const val DEBUG = "settings/debug"
    const val UNSORTED = "unsorted"

    const val ARG_CATEGORY = "category"
    const val ARG_MONTH = "month"
    const val ARG_ID = "id"
    const val ARG_SEARCH = "search"
    const val ARG_UNPARSED = "unparsed"
    const val ARG_DATE = "date"
    const val ARG_TX = "tx"

    fun where(category: String? = null) = if (category == null) WHERE else "$WHERE?$ARG_CATEGORY=$category"
    fun detail(id: Long) = "detail/$id"

    /**
     * New shift, or edit of an existing one when [shiftId] is given. [epochDay] opens a new shift
     * on that day (a tap on a day of the work calendar) instead of today.
     */
    fun shift(shiftId: Long? = null, epochDay: Long? = null): String {
        val q = listOfNotNull(
            shiftId?.let { "$ARG_ID=$it" },
            epochDay?.let { "$ARG_DATE=$it" },
        )
        return if (q.isEmpty()) SHIFT else "$SHIFT?${q.joinToString("&")}"
    }

    /** The calibration screen; with [transactionId] it opens with that expense's raw text filled in. */
    fun calibration(transactionId: Long? = null) = if (transactionId == null) CALIBRATION else "$CALIBRATION?$ARG_TX=$transactionId"

    /**
     * The expenses list. [focusSearch] opens it with the search field focused (the home search
     * icon); [unparsedOnly] opens it showing only what could not be parsed (the home attention row).
     */
    fun transactions(category: String? = null, month: String? = null, focusSearch: Boolean = false, unparsedOnly: Boolean = false): String {
        val q = listOfNotNull(
            category?.let { "$ARG_CATEGORY=$it" },
            month?.let { "$ARG_MONTH=$it" },
            if (focusSearch) "$ARG_SEARCH=true" else null,
            if (unparsedOnly) "$ARG_UNPARSED=true" else null,
        )
        return if (q.isEmpty()) TRANSACTIONS else "$TRANSACTIONS?${q.joinToString("&")}"
    }
}
