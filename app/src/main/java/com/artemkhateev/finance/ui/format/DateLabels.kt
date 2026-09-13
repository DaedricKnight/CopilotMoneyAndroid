package com.artemkhateev.finance.ui.format

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
private val shortDayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val longDayFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val monthYearFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dayFormat)
}

/** «Sep 12». */
fun shortDate(date: LocalDate): String = date.format(shortDayFormat)

/** «Jun 1, 2027». */
fun longDate(date: LocalDate): String = date.format(longDayFormat)

/** «Jun 2027». */
fun monthYear(date: LocalDate): String = date.format(monthYearFormat)

/** Дата в истории: «Today», «Yesterday», «Sep 2», а из другого года — «Dec 2, 2025». */
fun historyDate(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date.year == today.year -> shortDate(date)
    else -> longDate(date)
}

/** «Mon». */
fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.US)

/** «Monday». */
fun weekdayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.US)

/** «Mar». */
fun monthShort(month: Month): String = month.getDisplayName(TextStyle.SHORT, Locale.US)

/** «Sep 1 – Sep 12». */
fun periodLabel(start: LocalDate, end: LocalDate): String = "${shortDate(start)} – ${shortDate(end)}"

/** День месяца, как в референсе: 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st. */
fun ordinalDay(day: Int): String {
    val suffix = if (day % 100 in 11..13) {
        "th"
    } else {
        when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$day$suffix"
}
