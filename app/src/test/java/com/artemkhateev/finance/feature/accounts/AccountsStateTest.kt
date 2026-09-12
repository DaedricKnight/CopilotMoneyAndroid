package com.artemkhateev.finance.feature.accounts

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AccountsStateTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val checking = Account("checking", "Checking", "Bank", AccountType.Checking, Money(100_000), mask = "2124")
    private val savings = Account("savings", "Savings", "Bank", AccountType.Savings, Money(500_000))
    private val card = Account("card", "Visa", "Bank", AccountType.CreditCard, Money(-30_000))

    private fun tx(account: String, cents: Long, day: Int) =
        Transaction("t-$account-$cents-$day", account, "Shop", Money(cents), LocalDate.of(2026, 9, day), null)

    @Test
    fun `net worth is assets minus what is owed`() {
        val state = buildAccounts(today, listOf(card, savings, checking), emptyList())

        assertEquals(Money(600_000), state.assets)
        assertEquals(Money(30_000), state.liabilities)
        assertEquals(Money(570_000), state.netWorth)
        assertEquals(listOf("Cash & checking", "Savings", "Credit cards"), state.groups.map { it.title })
        assertEquals(Money(30_000), state.groups.last().accounts.single().displayBalance)
        assertEquals("Bank •• 2124", state.groups.first().accounts.single().subtitle)
    }

    @Test
    fun `history walks back from today through transactions`() {
        val state = buildAccounts(today, listOf(checking), listOf(tx("checking", -10_000, 15), tx("checking", 50_000, 12)))
        val history = state.history

        // Окно — с 1 августа по 15 сентября.
        assertEquals(46, history.size)
        assertEquals(100_000L, history[45]) // конец 15-го
        assertEquals(110_000L, history[44]) // конец 14-го: расхода 15-го ещё не было
        assertEquals(110_000L, history[42]) // конец 12-го: доход уже пришёл
        assertEquals(60_000L, history[41]) // конец 11-го: дохода ещё нет
        assertEquals(Money(40_000), state.change)
    }

    @Test
    fun `transactions of deleted accounts do not move net worth`() {
        val state = buildAccounts(today, listOf(checking), listOf(tx("gone", -10_000, 14)))
        assertEquals(Money.Zero, state.change)
    }
}
