package com.tamir.drift.domain.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GoalHistoryTest {

    private fun day(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun `without a history the current goal applies to every day`() {
        val h = GoalHistory.parse(null, 1_200_00L)
        assertEquals(1_200_00L, h.at(day(2020, 1, 1)))
        assertEquals(1_200_00L, h.at(day(2030, 1, 1)))
        assertEquals(1_200_00L, GoalHistory.parse("", 1_200_00L).at(0L))
        assertEquals(900_00L, GoalHistory.fixed(900_00L).at(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `a change applies from its day on and leaves earlier days alone`() {
        val text = GoalHistory.change(null, oldAgorot = 1_000_00L, newAgorot = 1_500_00L, fromEpochDay = day(2026, 12, 6))
        val h = GoalHistory.parse(text, 1_500_00L)
        assertEquals(1_000_00L, h.at(day(2026, 12, 5)))
        assertEquals(1_500_00L, h.at(day(2026, 12, 6)))
        assertEquals(1_500_00L, h.at(day(2027, 3, 1)))
        assertEquals(1_000_00L, h.at(day(2026, 1, 1)))
    }

    @Test
    fun `several changes keep their order`() {
        var text: String? = null
        text = GoalHistory.change(text, 1_000_00L, 1_500_00L, day(2026, 12, 6))
        text = GoalHistory.change(text, 1_500_00L, 800_00L, day(2027, 1, 3))
        val h = GoalHistory.parse(text, 800_00L)
        assertEquals(1_000_00L, h.at(day(2026, 12, 1)))
        assertEquals(1_500_00L, h.at(day(2026, 12, 31)))
        assertEquals(800_00L, h.at(day(2027, 1, 3)))
        assertEquals(3, GoalHistory.decode(text).size)
    }

    @Test
    fun `a second change in the same period replaces the first`() {
        // The +/− buttons change the goal step by step within one week: one entry, not one per tap.
        var text: String? = null
        val week = day(2026, 12, 6)
        text = GoalHistory.change(text, 1_000_00L, 1_050_00L, week)
        text = GoalHistory.change(text, 1_050_00L, 1_100_00L, week)
        text = GoalHistory.change(text, 1_100_00L, 1_150_00L, week)
        assertEquals(2, GoalHistory.decode(text).size)
        assertEquals(1_150_00L, GoalHistory.parse(text, 1_150_00L).at(week))
        assertEquals(1_000_00L, GoalHistory.parse(text, 1_150_00L).at(week - 1))
    }

    @Test
    fun `changing back to the previous amount leaves no trace`() {
        val week = day(2026, 12, 6)
        var text = GoalHistory.change(null, 1_000_00L, 1_050_00L, week)
        text = GoalHistory.change(text, 1_050_00L, 1_000_00L, week)
        assertEquals(listOf(GoalChange(GoalHistory.BASE_EPOCH_DAY, 1_000_00L)), GoalHistory.decode(text))
    }

    @Test
    fun `the goal set now always wins from the latest change on`() {
        // A stored history that fell out of step with the goal itself (an edited backup, say)
        // must not show a different current goal than the one on the screen.
        val text = GoalHistory.change(null, 1_000_00L, 1_500_00L, day(2026, 12, 6))
        val h = GoalHistory.parse(text, 2_000_00L)
        assertEquals(1_000_00L, h.at(day(2026, 12, 5)))
        assertEquals(2_000_00L, h.at(day(2026, 12, 6)))
        assertEquals(2_000_00L, h.at(day(2027, 6, 1)))
    }

    @Test
    fun `unreadable text is ignored`() {
        assertEquals(emptyList<GoalChange>(), GoalHistory.decode("garbage"))
        assertEquals(listOf(GoalChange(5L, 7L)), GoalHistory.decode("x:y;5:7;;9"))
        assertEquals(1_200_00L, GoalHistory.parse("garbage", 1_200_00L).at(100L))
        // Entries are read oldest first whatever their stored order.
        assertEquals(listOf(GoalChange(1L, 10L), GoalChange(2L, 20L)), GoalHistory.decode("2:20;1:10"))
    }
}
