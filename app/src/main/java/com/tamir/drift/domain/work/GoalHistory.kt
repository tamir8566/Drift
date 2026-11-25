package com.tamir.drift.domain.work

import java.time.LocalDate

/** "From [fromEpochDay] on the goal is [amountAgorot]". */
data class GoalChange(val fromEpochDay: Long, val amountAgorot: Long)

/**
 * A goal amount over time (the weekly income goal, the monthly saving goal).
 *
 * The carry-over runs across weeks and months, so every past period has to be judged by the goal
 * that was in force then: changing the goal today must not rewrite the balance of earlier weeks.
 *
 * Stored as text, oldest first: `<epochDay>:<agorot>;<epochDay>:<agorot>`. An empty history means
 * the goal was never changed since histories exist — the current amount applies to every day.
 */
class GoalHistory private constructor(
    private val changes: List<GoalChange>,
    private val currentAgorot: Long,
) {

    /**
     * The goal in force on [epochDay]; before the first recorded change, the oldest known amount.
     * From the latest change on it is always the goal set now: the history only describes the
     * past, so a stored text that fell out of step can never show a different goal than the one
     * the user sees and edits.
     */
    fun at(epochDay: Long): Long {
        if (changes.isEmpty() || epochDay >= changes.last().fromEpochDay) return currentAgorot
        var result = changes.first().amountAgorot
        for (change in changes) {
            if (change.fromEpochDay <= epochDay) result = change.amountAgorot else break
        }
        return result
    }

    fun at(date: LocalDate): Long = at(date.toEpochDay())

    companion object {
        /** The oldest entry of a history: "since always". */
        const val BASE_EPOCH_DAY = 0L

        /** A goal that never changed. */
        fun fixed(amountAgorot: Long): GoalHistory = GoalHistory(emptyList(), amountAgorot)

        /** [text] is the stored history (null / blank / unreadable = none); [currentAgorot] the goal set now. */
        fun parse(text: String?, currentAgorot: Long): GoalHistory = GoalHistory(decode(text), currentAgorot)

        /**
         * The stored history after the goal becomes [newAgorot] from [fromEpochDay] on.
         * [oldAgorot] is the goal until now (used when no history exists yet). A second change for
         * the same period replaces the first one, and a change back to the previous amount leaves
         * no trace.
         */
        fun change(text: String?, oldAgorot: Long, newAgorot: Long, fromEpochDay: Long): String {
            val existing = decode(text).ifEmpty { listOf(GoalChange(BASE_EPOCH_DAY, oldAgorot)) }
            val kept = existing.filter { it.fromEpochDay < fromEpochDay }
            val result = when {
                kept.isEmpty() -> listOf(GoalChange(fromEpochDay, newAgorot))
                kept.last().amountAgorot == newAgorot -> kept
                else -> kept + GoalChange(fromEpochDay, newAgorot)
            }
            return encode(result)
        }

        fun encode(changes: List<GoalChange>): String =
            changes.sortedBy { it.fromEpochDay }.joinToString(";") { "${it.fromEpochDay}:${it.amountAgorot}" }

        fun decode(text: String?): List<GoalChange> {
            if (text.isNullOrBlank()) return emptyList()
            return text.split(';').mapNotNull { part ->
                val pieces = part.trim().split(':')
                if (pieces.size != 2) return@mapNotNull null
                val day = pieces[0].toLongOrNull() ?: return@mapNotNull null
                val amount = pieces[1].toLongOrNull() ?: return@mapNotNull null
                if (amount < 0L) null else GoalChange(day, amount)
            }.sortedBy { it.fromEpochDay }
        }
    }
}
