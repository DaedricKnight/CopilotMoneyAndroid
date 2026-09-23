package com.artemkhateev.finance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HistoryStartTest {

    private val today = LocalDate.of(2026, 9, 22)

    @Test
    fun `the usual window covers everything since the start of last month`() {
        assertNull(historyStart(LocalDate.of(2026, 8, 1), today))
        assertNull(historyStart(today, today))
    }

    @Test
    fun `longer history is loaded in whole years`() {
        assertEquals(LocalDate.of(2025, 9, 22), historyStart(LocalDate.of(2026, 7, 31), today))
        assertEquals(LocalDate.of(2025, 9, 22), historyStart(LocalDate.of(2025, 9, 22), today))
        assertEquals(LocalDate.of(2024, 9, 22), historyStart(LocalDate.of(2025, 9, 21), today))
    }
}
