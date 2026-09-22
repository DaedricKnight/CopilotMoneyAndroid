package com.artemkhateev.finance.feature.transactions

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionsStateTest {

    private val today = LocalDate.of(2026, 9, 22)
    private val food = Category("food", "Food", "🥑", CategoryTone.Green)
    private val cash = Account("cash", "Cash", "💶", AccountType.Checking, Money(100_000))

    private fun tx(cents: Long, date: LocalDate) =
        Transaction("t-$date-$cents", "cash", "Shop", Money(cents), date, "food")

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
    fun `only transactions of the period are listed and summed`() {
        val transactions = listOf(
            tx(-1_000, today),
            tx(300_000, today.minusDays(2)),
            tx(-2_000, today.minusDays(3)),
            tx(-4_000, today.minusDays(20)),
        )

        val day = buildTransactions(today, TransactionPeriod.Day, transactions, listOf(food), listOf(cash))
        assertEquals(Money(1_000), day.spent)
        assertEquals(1, day.count)

        val week = buildTransactions(today, TransactionPeriod.Week, transactions, listOf(food), listOf(cash))
        // Доход в траты не идёт, но в списке остаётся.
        assertEquals(Money(3_000), week.spent)
        assertEquals(3, week.count)
        assertEquals(listOf(today, today.minusDays(2), today.minusDays(3)), week.days.map { it.date })

        val month = buildTransactions(today, TransactionPeriod.Month, transactions, listOf(food), listOf(cash))
        assertEquals(Money(7_000), month.spent)
        assertEquals(4, month.count)
    }

    @Test
    fun `future dates stay out of the period`() {
        val state = buildTransactions(
            today,
            TransactionPeriod.Year,
            listOf(tx(-1_000, today.plusDays(1)), tx(-2_000, today)),
            listOf(food),
            listOf(cash),
        )

        assertEquals(Money(2_000), state.spent)
        assertEquals(1, state.count)
    }

    @Test
    fun `category usage counts only the period`() {
        val state = buildTransactions(
            today,
            TransactionPeriod.Day,
            listOf(tx(-1_000, today), tx(-2_000, today.minusDays(9))),
            listOf(food),
            listOf(cash),
        )

        assertEquals(mapOf("food" to 1), state.categoryUsage)
    }

    @Test
    fun `only periods longer than a month need the year window`() {
        assertFalse(TransactionPeriod.Day.needsYear)
        assertFalse(TransactionPeriod.Week.needsYear)
        assertFalse(TransactionPeriod.Month.needsYear)
        assertTrue(TransactionPeriod.Quarter.needsYear)
        assertTrue(TransactionPeriod.HalfYear.needsYear)
        assertTrue(TransactionPeriod.Year.needsYear)
    }

    @Test
    fun `unknown saved period falls back to a month`() {
        assertEquals(TransactionPeriod.Month, TransactionPeriod.fromKey(null))
        assertEquals(TransactionPeriod.Month, TransactionPeriod.fromKey("Decade"))
        assertEquals(TransactionPeriod.Quarter, TransactionPeriod.fromKey("Quarter"))
    }
}
