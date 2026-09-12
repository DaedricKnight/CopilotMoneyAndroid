package com.artemkhateev.finance.ui.format

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
private val shortDayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dayFormat)
}

/** «Sep 12». */
fun shortDate(date: LocalDate): String = date.format(shortDayFormat)

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
