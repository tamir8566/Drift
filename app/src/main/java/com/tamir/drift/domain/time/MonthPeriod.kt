package com.tamir.drift.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A calendar month expressed as an epoch-millis range in the given zone. */
data class MonthPeriod(val yearMonth: YearMonth, val zone: ZoneId) {

    val startMillis: Long = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Exclusive end. */
    val endMillis: Long = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val daysInMonth: Int = yearMonth.lengthOfMonth()

    fun contains(epochMillis: Long) = epochMillis in startMillis until endMillis

    fun previous() = MonthPeriod(yearMonth.minusMonths(1), zone)

    /** Day of month for [nowMillis] clamped to this month (1..daysInMonth). */
    fun dayOfMonth(nowMillis: Long): Int {
        val date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when {
            date.isBefore(yearMonth.atDay(1)) -> 1
            date.isAfter(yearMonth.atEndOfMonth()) -> daysInMonth
            else -> date.dayOfMonth
        }
    }

    companion object {
        fun of(epochMillis: Long, zone: ZoneId): MonthPeriod =
            MonthPeriod(YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zone)), zone)

        fun of(date: LocalDate, zone: ZoneId): MonthPeriod = MonthPeriod(YearMonth.from(date), zone)

        /** Key like "2026-11" used for grouping. */
        fun key(epochMillis: Long, zone: ZoneId): String =
            YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zone)).toString()
    }
}
