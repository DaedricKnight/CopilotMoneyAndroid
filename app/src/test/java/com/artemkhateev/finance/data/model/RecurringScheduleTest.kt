package com.artemkhateev.finance.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

class RecurringScheduleTest {

    private val septemberStart = LocalDate.of(2026, 9, 1)
    private val septemberEnd = LocalDate.of(2026, 9, 30)

    @Test
    fun `weekly payment falls on every matching weekday`() {
        // 1 сентября 2026 года — вторник.
        val dates = RecurringSchedule.Weekly(DayOfWeek.MONDAY).dueDates(septemberStart, septemberEnd)
        assertEquals(listOf(7, 14, 21, 28), dates.map { it.dayOfMonth })
    }

    @Test
    fun `monthly day past the end of a month falls on its last day`() {
        val dates = RecurringSchedule.Monthly(31).dueDates(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 10, 15))
        assertEquals(listOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30)), dates)
    }

    @Test
    fun `yearly payment on february 29 moves to the 28th in common years`() {
        val dates = RecurringSchedule.Yearly(Month.FEBRUARY, 29).dueDates(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 12, 31))
        assertEquals(listOf(LocalDate.of(2027, 2, 28), LocalDate.of(2028, 2, 29)), dates)
    }

    @Test
    fun `yearly payment of another month has no dates in this one`() {
        assertEquals(emptyList<LocalDate>(), RecurringSchedule.Yearly(Month.MARCH, 14).dueDates(septemberStart, septemberEnd))
    }
}
