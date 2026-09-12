package com.artemkhateev.finance.feature.cashflow

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CashFlowStateTest {

    // 15-е: сравниваем 1–15 сентября с 1–15 августа.
    private val today = LocalDate.of(2026, 9, 15)

    private fun tx(cents: Long, month: Int, day: Int, category: String? = "food") =
        Transaction("t-$month-$day-$cents", "acc", "Shop", Money(cents), LocalDate.of(2026, month, day), category)

    @Test
    fun `month so far is compared with the same days of last month`() {
        val state = buildCashFlow(
            today,
            listOf(
                tx(100_000, 8, 1, "income"), tx(-20_000, 8, 3),
                // После 15 августа — в сравнение не входит.
                tx(-5_000, 8, 20),
                tx(120_000, 9, 1, "income"), tx(-30_000, 9, 2),
            ),
            emptyList(),
        )

        assertEquals(Money(90_000), state.net.current)
        assertEquals(Money(80_000), state.net.previous)
        assertEquals(13, state.net.changePercent)
        assertEquals(50, state.spend.changePercent)
        assertEquals(20, state.income.changePercent)
        assertEquals("Aug 1 – Aug 15", state.previousPeriod)
    }

    @Test
    fun `weekly bars cover the whole loaded window`() {
        val state = buildCashFlow(today, listOf(tx(100_000, 8, 1, "income"), tx(-20_000, 8, 3)), emptyList())

        assertEquals(7, state.netBars.size)
        assertEquals(80_000L, state.netBars.first().segments.single().value)
        assertEquals("Aug 1", state.firstLabel)
        assertEquals("Sep 15", state.lastLabel)
    }

    @Test
    fun `nothing to compare with gives no percentage`() {
        assertNull(buildCashFlow(today, listOf(tx(-30_000, 9, 2)), emptyList()).spend.changePercent)
    }
}
