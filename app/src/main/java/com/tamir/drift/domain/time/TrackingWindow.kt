package com.tamir.drift.domain.time

import com.tamir.drift.domain.model.TxnSummary
import com.tamir.drift.domain.work.ShiftSummary
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The app-wide "tracking start" (PRD "תחילת המעקב"): every cumulative / comparative figure
 * (totals, budget usage, history, insights, goals) only counts records from this day on.
 * Older transactions and shifts stay stored and listed, they just do not feed any statistic.
 *
 * [startEpochDay] is a LocalDate.toEpochDay(); null means "no limit" (everything counts).
 */
data class TrackingWindow(val startEpochDay: Long?) {

    val startDate: LocalDate? get() = startEpochDay?.let { LocalDate.ofEpochDay(it) }

    /** True when a shift day (epoch day) is on or after the start. */
    fun includes(epochDay: Long): Boolean = startEpochDay == null || epochDay >= startEpochDay

    /** True when a transaction timestamp (epoch millis) falls on or after the start day in [zone]. */
    fun includes(timestampMillis: Long, zone: ZoneId): Boolean =
        includes(Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate().toEpochDay())

    /** True when at least one day of [month] is on or after the start (months fully before it are "—"). */
    fun includesMonth(month: YearMonth): Boolean = includes(month.atEndOfMonth().toEpochDay())

    companion object {
        /** November 2026 is a test month: by default everything counts from 1.12.2026. */
        val DEFAULT_START: LocalDate = LocalDate.of(2026, 12, 1)
        val DEFAULT_START_EPOCH_DAY: Long = DEFAULT_START.toEpochDay()

        /** A window without a start — nothing is filtered. */
        val ALL = TrackingWindow(null)

        /**
         * The window statistics use on [todayEpochDay]. Until the tracking start arrives, everything
         * since the first launch counts (the trial period stays visible while it is running). From
         * the tracking start on, only records from that day count: every total, comparison and
         * carry-over begins clean on that day.
         */
        fun current(todayEpochDay: Long, trackingStartEpochDay: Long, firstLaunchEpochDay: Long): TrackingWindow =
            if (todayEpochDay < trackingStartEpochDay) TrackingWindow(firstLaunchEpochDay) else TrackingWindow(trackingStartEpochDay)
    }
}

/** Transactions on or after the tracking start (the input of every statistic). */
fun List<TxnSummary>.sinceStart(window: TrackingWindow, zone: ZoneId): List<TxnSummary> =
    if (window.startEpochDay == null) this else filter { window.includes(it.timestamp, zone) }

/** Shifts on or after the tracking start (the input of every income statistic). */
fun List<ShiftSummary>.sinceStart(window: TrackingWindow): List<ShiftSummary> =
    if (window.startEpochDay == null) this else filter { window.includes(it.epochDay) }
