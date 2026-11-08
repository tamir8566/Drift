package com.tamir.drift.ui.nav

object Routes {
    const val HOME = "home"
    const val WHERE = "where"
    const val BUDGET = "budget"
    const val WORK = "work"
    const val GOAL = "goal"
    const val ADD = "add"
    const val SHIFT = "shift"
    const val DETAIL = "detail/{id}"
    const val TRANSACTIONS = "transactions"
    const val SETTINGS = "settings"
    const val CALIBRATION = "settings/calibration"
    const val DEBUG = "settings/debug"

    const val ARG_CATEGORY = "category"
    const val ARG_MONTH = "month"
    const val ARG_ID = "id"

    fun where(category: String? = null) = if (category == null) WHERE else "$WHERE?$ARG_CATEGORY=$category"
    fun detail(id: Long) = "detail/$id"

    /** New shift, or edit of an existing one when [shiftId] is given. */
    fun shift(shiftId: Long? = null) = if (shiftId == null) SHIFT else "$SHIFT?$ARG_ID=$shiftId"

    fun transactions(category: String? = null, month: String? = null): String {
        val q = listOfNotNull(category?.let { "$ARG_CATEGORY=$it" }, month?.let { "$ARG_MONTH=$it" })
        return if (q.isEmpty()) TRANSACTIONS else "$TRANSACTIONS?${q.joinToString("&")}"
    }
}
