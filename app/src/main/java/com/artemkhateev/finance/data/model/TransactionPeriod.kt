package com.artemkhateev.finance.data.model

import java.time.LocalDate

/** Последние день, неделя, месяц…: период всегда заканчивается сегодня. По нему фильтруют ленту и считают денежный поток. */
enum class TransactionPeriod(val label: String) {
    Day("1D"),
    Week("1W"),
    Month("1M"),
    Quarter("3M"),
    HalfYear("6M"),
    Year("1Y");

    /** Первый день периода, который заканчивается [end]. */
    fun start(end: LocalDate): LocalDate = when (this) {
        Day -> end
        Week -> end.minusDays(6)
        Month -> end.minusMonths(1).plusDays(1)
        Quarter -> end.minusMonths(3).plusDays(1)
        HalfYear -> end.minusMonths(6).plusDays(1)
        Year -> end.minusYears(1).plusDays(1)
    }

    /** Такой же период прямо перед текущим: с ним сравнивают. */
    fun previous(today: LocalDate): ClosedRange<LocalDate> {
        val end = start(today).minusDays(1)
        return start(end)..end
    }

    companion object {
        /** Сохранённое значение; незнакомое или пустое — месяц. */
        fun fromKey(key: String?): TransactionPeriod = entries.firstOrNull { it.name == key } ?: Month
    }
}
