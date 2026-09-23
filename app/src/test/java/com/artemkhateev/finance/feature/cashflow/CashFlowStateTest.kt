package com.artemkhateev.finance.feature.cashflow

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.TransactionPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CashFlowStateTest {

    private val today = LocalDate.of(2026, 9, 15)

    private fun tx(cents: Long, month: Int, day: Int, category: String? = "food", year: Int = 2026) =
        Transaction("t-$year-$month-$day-$cents", "acc", "Shop", Money(cents), LocalDate.of(year, month, day), category)

    @Test
    fun `a month is compared with the month right before it`() {
        // Месяц — 16 августа – 15 сентября, прошлый — 16 июля – 15 августа; 10 июля не входит никуда.
        val state = buildCashFlow(
            today,
            TransactionPeriod.Month,
            listOf(
                tx(-7_000, 7, 10),
                tx(100_000, 7, 20, "income"), tx(-20_000, 8, 3),
                tx(-5_000, 8, 20), tx(120_000, 9, 1, "income"), tx(-30_000, 9, 2),
            ),
            emptyList(),
        )

        assertEquals(Money(85_000), state.net.current)
        assertEquals(Money(80_000), state.net.previous)
        assertEquals(6, state.net.changePercent)
        assertEquals(75, state.spend.changePercent)
        assertEquals(20, state.income.changePercent)
        assertEquals("Aug 16 – Sep 15", state.currentPeriod)
        assertEquals("Jul 16 – Aug 15", state.previousPeriod)
    }

    @Test
    fun `bars follow the period`() {
        val transactions = listOf(tx(-1_000, 9, 10), tx(-2_000, 9, 14))

        val week = buildCashFlow(today, TransactionPeriod.Week, transactions, emptyList())
        assertEquals(7, week.spendBars.size)
        assertEquals(LocalDate.of(2026, 9, 10), week.spendBars[1].start)
        assertEquals(1_000L, week.spendBars[1].segments.single().value)
        assertEquals("Sep 9", week.firstLabel)
        assertEquals("Sep 15", week.lastLabel)

        // Месяц — по неделям с 16 августа: 16, 23, 30 августа, 6 и 13 сентября.
        val month = buildCashFlow(today, TransactionPeriod.Month, transactions, emptyList())
        assertEquals(5, month.netBars.size)
        assertEquals(-1_000L, month.netBars[3].segments.single().value)
        assertEquals(-2_000L, month.netBars[4].segments.single().value)

        val halfYear = buildCashFlow(today, TransactionPeriod.HalfYear, transactions, emptyList())
        assertEquals(6, halfYear.incomeBars.size)
        assertEquals(LocalDate.of(2026, 3, 16), halfYear.incomeBars.first().start)

        assertEquals(12, buildCashFlow(today, TransactionPeriod.Year, transactions, emptyList()).netBars.size)
        assertTrue(buildCashFlow(today, TransactionPeriod.Day, transactions, emptyList()).netBars.isEmpty())
    }

    @Test
    fun `a year is compared with the year before it`() {
        val state = buildCashFlow(
            today,
            TransactionPeriod.Year,
            listOf(tx(-10_000, 3, 1, year = 2025), tx(-15_000, 3, 1)),
            emptyList(),
        )

        assertEquals(Money(15_000), state.spend.current)
        assertEquals(Money(10_000), state.spend.previous)
        assertEquals("Sep 16, 2024 – Sep 15, 2025", state.previousPeriod)
        assertEquals("Sep 16, 2025", state.firstLabel)
        assertEquals("Sep 15", state.lastLabel)
    }

    @Test
    fun `a day is compared with yesterday`() {
        val state = buildCashFlow(today, TransactionPeriod.Day, listOf(tx(-3_000, 9, 14), tx(-6_000, 9, 15)), emptyList())

        assertEquals(100, state.spend.changePercent)
        assertEquals("Sep 15", state.currentPeriod)
        assertEquals("Sep 14", state.previousPeriod)
    }

    @Test
    fun `nothing to compare with gives no percentage`() {
        assertNull(buildCashFlow(today, TransactionPeriod.Month, listOf(tx(-30_000, 9, 2)), emptyList()).spend.changePercent)
    }
}
