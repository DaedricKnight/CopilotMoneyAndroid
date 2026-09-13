package com.artemkhateev.finance.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateLabelsTest {

    @Test
    fun `ordinal suffixes follow english rules`() {
        val days = listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31)
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st"),
            days.map(::ordinalDay),
        )
    }

    @Test
    fun `period label uses short month names`() {
        assertEquals("Aug 1 – Aug 15", periodLabel(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15)))
    }

    @Test
    fun `history dates show the year only when it differs`() {
        val today = LocalDate.of(2026, 9, 15)
        assertEquals("Today", historyDate(today, today))
        assertEquals("Yesterday", historyDate(LocalDate.of(2026, 9, 14), today))
        assertEquals("Sep 2", historyDate(LocalDate.of(2026, 9, 2), today))
        assertEquals("Dec 2, 2025", historyDate(LocalDate.of(2025, 12, 2), today))
    }

    @Test
    fun `goal dates use month and year`() {
        assertEquals("Jun 2027", monthYear(LocalDate.of(2027, 6, 1)))
        assertEquals("Jun 1, 2027", longDate(LocalDate.of(2027, 6, 1)))
    }
}
