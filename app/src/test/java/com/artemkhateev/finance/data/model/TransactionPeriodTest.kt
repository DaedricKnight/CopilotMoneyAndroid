package com.artemkhateev.finance.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TransactionPeriodTest {

    private val today = LocalDate.of(2026, 9, 22)

    @Test
    fun `period ends today and starts a whole period back`() {
        assertEquals(today, TransactionPeriod.Day.start(today))
        assertEquals(LocalDate.of(2026, 9, 16), TransactionPeriod.Week.start(today))
        assertEquals(LocalDate.of(2026, 8, 23), TransactionPeriod.Month.start(today))
        assertEquals(LocalDate.of(2026, 6, 23), TransactionPeriod.Quarter.start(today))
        assertEquals(LocalDate.of(2026, 3, 23), TransactionPeriod.HalfYear.start(today))
        assertEquals(LocalDate.of(2025, 9, 23), TransactionPeriod.Year.start(today))
    }

    @Test
    fun `previous period has the same length and ends the day before`() {
        assertEquals(LocalDate.of(2026, 9, 21)..LocalDate.of(2026, 9, 21), TransactionPeriod.Day.previous(today))
        assertEquals(LocalDate.of(2026, 9, 9)..LocalDate.of(2026, 9, 15), TransactionPeriod.Week.previous(today))
        assertEquals(LocalDate.of(2026, 7, 23)..LocalDate.of(2026, 8, 22), TransactionPeriod.Month.previous(today))
        assertEquals(LocalDate.of(2024, 9, 23)..LocalDate.of(2025, 9, 22), TransactionPeriod.Year.previous(today))
    }

    @Test
    fun `unknown saved period falls back to a month`() {
        assertEquals(TransactionPeriod.Month, TransactionPeriod.fromKey(null))
        assertEquals(TransactionPeriod.Month, TransactionPeriod.fromKey("Decade"))
        assertEquals(TransactionPeriod.Quarter, TransactionPeriod.fromKey("Quarter"))
    }
}
