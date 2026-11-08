package com.tamir.drift.ui.format

import android.content.Context
import com.tamir.drift.R
import com.tamir.drift.data.repo.Money
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Number / date formatting for the Hebrew UI. Digits are Western (as in the design). */
object Fmt {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val intFormat = DecimalFormat("#,##0", symbols)
    private val decFormat = DecimalFormat("#,##0.00", symbols)

    /** "2,840" — whole shekels (rounded). Used for all big figures. */
    fun money(shekels: Double): String = intFormat.format(shekels.roundToLong())

    /** "2,840" or "2,840.50" — keeps agorot only when they are non-zero. */
    fun moneyExact(agorot: Long): String =
        if (agorot % 100 == 0L) intFormat.format(agorot / 100) else decFormat.format(Money.toShekels(agorot))

    fun moneyAgorot(agorot: Long): String = money(Money.toShekels(agorot))

    /** "43%" */
    fun percent(value: Double): String = "${value.roundToInt()}%"

    /** "↓ 8%" / "↑ 12%" (arrows are plain text characters, not emoji). */
    fun change(percent: Double): String {
        val arrow = if (percent < 0) "↓" else "↑"
        return "$arrow ${abs(percent).roundToInt()}%"
    }

    fun monthName(context: Context, month: Int): String = context.resources.getStringArray(R.array.months_full)[month - 1]
    fun monthShort(context: Context, month: Int): String = context.resources.getStringArray(R.array.months_short)[month - 1]

    fun monthLabel(context: Context, ym: YearMonth, withYear: Boolean = false): String =
        if (withYear) "${monthName(context, ym.monthValue)} ${ym.year}" else monthName(context, ym.monthValue)

    fun dayName(context: Context, day: DayOfWeek): String = when (day) {
        DayOfWeek.SUNDAY -> context.getString(R.string.day_sunday)
        DayOfWeek.MONDAY -> context.getString(R.string.day_monday)
        DayOfWeek.TUESDAY -> context.getString(R.string.day_tuesday)
        DayOfWeek.WEDNESDAY -> context.getString(R.string.day_wednesday)
        DayOfWeek.THURSDAY -> context.getString(R.string.day_thursday)
        DayOfWeek.FRIDAY -> context.getString(R.string.day_friday)
        DayOfWeek.SATURDAY -> context.getString(R.string.day_saturday)
    }

    /** "היום" / "אתמול" / "5.11" relative date label for lists. */
    fun relativeDay(context: Context, epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (date) {
            today -> context.getString(R.string.today)
            today.minusDays(1) -> context.getString(R.string.yesterday)
            else -> shortDate(date)
        }
    }

    /** "5.11" or "5.11.25" when not the current year. */
    fun shortDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = LocalDate.now(zone)
        return if (date.year == today.year) "${date.dayOfMonth}.${date.monthValue}"
        else "${date.dayOfMonth}.${date.monthValue}.${date.year % 100}"
    }

    fun shortDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        shortDate(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(), zone)

    fun fullDate(date: LocalDate): String = "${date.dayOfMonth}.${date.monthValue}.${date.year}"

    fun time(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
    }

    fun clock(hour: Int, minute: Int): String = String.format(Locale.US, "%02d:%02d", hour, minute)
}
