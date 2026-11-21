package com.tamir.drift.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class JobPickerTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 21) // Saturday
    private var nextId = 1L

    private fun job(id: Long, active: Boolean = true, main: Boolean = false) =
        JobSummary(id, "job$id", 40_00L, isActive = active, isMain = main)

    private fun shift(jobId: Long, date: LocalDate) = ShiftSummary(nextId++, jobId, date.toEpochDay(), 8.0, 320_00L)

    // ---- main job ----

    @Test
    fun `the marked job is the main one`() {
        val jobs = listOf(job(1), job(2, main = true), job(3))
        val shifts = (1L..5L).map { shift(1, today.minusDays(it)) }
        assertEquals(2L, JobPicker.mainJob(jobs, shifts, today.toEpochDay())?.id)
    }

    @Test
    fun `without a mark - most shifts in the last thirty days`() {
        val jobs = listOf(job(1), job(2))
        val shifts = listOf(shift(1, today.minusDays(2)), shift(2, today.minusDays(3)), shift(2, today.minusDays(4))) +
            (40L..50L).map { shift(1, today.minusDays(it)) } // older than 30 days: ignored
        assertEquals(2L, JobPicker.mainJob(jobs, shifts, today.toEpochDay())?.id)
    }

    @Test
    fun `without shifts, or on a tie - the oldest job`() {
        val jobs = listOf(job(1), job(2))
        assertEquals(1L, JobPicker.mainJob(jobs, emptyList(), today.toEpochDay())?.id)
        val tie = listOf(shift(2, today.minusDays(1)), shift(1, today.minusDays(2)))
        assertEquals(1L, JobPicker.mainJob(jobs, tie, today.toEpochDay())?.id)
    }

    @Test
    fun `an inactive job is not main while an active one exists`() {
        val jobs = listOf(job(1, active = false, main = true), job(2))
        assertEquals(2L, JobPicker.mainJob(jobs, listOf(shift(1, today)), today.toEpochDay())?.id)
        assertEquals(1L, JobPicker.mainJob(listOf(job(1, active = false)), emptyList(), today.toEpochDay())?.id)
        assertNull(JobPicker.mainJob(emptyList(), emptyList(), today.toEpochDay()))
    }

    // ---- default job of a new shift ----

    @Test
    fun `the job most often worked on that weekday`() {
        val jobs = listOf(job(1), job(2))
        val friday = LocalDate.of(2026, 11, 20)
        val shifts = listOf(
            shift(2, friday.minusWeeks(1)), shift(2, friday.minusWeeks(2)), shift(1, friday.minusWeeks(3)),
            shift(1, friday.minusDays(1)), shift(1, friday.minusDays(2)), shift(1, friday.minusDays(3)), // other weekdays
        )
        assertEquals(2L, JobPicker.defaultJobId(friday, jobs, shifts))
        assertEquals(1L, JobPicker.defaultJobId(friday.minusDays(1), jobs, shifts)) // Thursday habit
    }

    @Test
    fun `weekday tie goes to the job worked most recently`() {
        val jobs = listOf(job(1), job(2))
        val friday = LocalDate.of(2026, 11, 20)
        val shifts = listOf(shift(1, friday.minusWeeks(2)), shift(2, friday.minusWeeks(1)))
        assertEquals(2L, JobPicker.defaultJobId(friday, jobs, shifts))
    }

    @Test
    fun `no shift on that weekday - the last used job`() {
        val jobs = listOf(job(1), job(2))
        val monday = LocalDate.of(2026, 11, 23)
        val shifts = listOf(shift(1, monday.minusDays(5)), shift(2, monday.minusDays(2)))
        assertEquals(2L, JobPicker.defaultJobId(monday, jobs, shifts))
    }

    @Test
    fun `no shifts at all - the main job, inactive jobs never`() {
        assertEquals(2L, JobPicker.defaultJobId(today, listOf(job(1), job(2, main = true)), emptyList()))
        assertEquals(1L, JobPicker.defaultJobId(today, listOf(job(1), job(2)), emptyList()))
        val inactiveHabit = listOf(shift(3, today.minusWeeks(1)), shift(3, today.minusWeeks(2)))
        assertEquals(1L, JobPicker.defaultJobId(today, listOf(job(1), job(3, active = false)), inactiveHabit))
        assertNull(JobPicker.defaultJobId(today, listOf(job(3, active = false)), emptyList()))
    }

    @Test
    fun `old habits beyond four months are forgotten`() {
        val jobs = listOf(job(1), job(2))
        val friday = LocalDate.of(2026, 11, 20)
        val shifts = (20L..30L).map { shift(1, friday.minusWeeks(it)) } + shift(2, friday.minusDays(1))
        assertEquals(2L, JobPicker.defaultJobId(friday, jobs, shifts)) // falls back to the last used
    }
}
