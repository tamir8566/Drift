package com.tamir.drift.notify

/**
 * Where a notification opens the app. The target travels as an intent extra; MainActivity reads
 * it (onCreate / onNewIntent) and the navigation host consumes it once.
 */
object DeepLink {
    const val EXTRA = "com.tamir.drift.extra.OPEN"

    const val BUDGET = "budget"
    const val GOAL = "goal"
    const val WHERE = "where"
    const val WORK = "work"
    const val SETTINGS = "settings"
}
