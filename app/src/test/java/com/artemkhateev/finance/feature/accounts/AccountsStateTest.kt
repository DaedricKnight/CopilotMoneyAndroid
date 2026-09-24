package com.artemkhateev.finance.feature.accounts

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.QUANTITY_SCALE
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.TransactionPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AccountsStateTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val checking = Account("checking", "Checking", "Bank", AccountType.Checking, Money(100_000), mask = "2124")
    private val savings = Account("savings", "Savings", "Bank", AccountType.Savings, Money(500_000))
    private val card = Account("card", "Visa", "Bank", AccountType.CreditCard, Money(-30_000))
    private val brokerage = Account("brokerage", "Brokerage", "Invest", AccountType.Investment, Money(1_000))
    private val fund = Holding(
        "fund", "brokerage", "VWCE", "All-World", AssetClass.Fund,
        quantityMicros = 10 * QUANTITY_SCALE, costPerUnit = Money(10_000), price = Money(12_000), priceUpdated = today,
    )

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

        // Месяц — 16 августа – 15 сентября; график с конца 15 августа.
        assertEquals(32, history.size)
        assertEquals(100_000L, history[31]) // конец 15-го
        assertEquals(110_000L, history[30]) // конец 14-го: расхода 15-го ещё не было
        assertEquals(110_000L, history[28]) // конец 12-го: доход уже пришёл
        assertEquals(60_000L, history[27]) // конец 11-го: дохода ещё нет
        assertEquals(Money(40_000), state.change)
    }

    @Test
    fun `transactions of deleted accounts do not move net worth`() {
        val state = buildAccounts(today, listOf(checking), listOf(tx("gone", -10_000, 14)))
        assertEquals(Money.Zero, state.change)
    }

    @Test
    fun `account with holdings is worth its holdings`() {
        val state = buildAccounts(today, listOf(checking, brokerage), emptyList(), listOf(fund))
        val row = state.groups.single { it.title == "Investments" }.accounts.single()

        assertEquals(Money(120_000), row.displayBalance)
        assertEquals(Money(1_000), row.account.balance) // форма правки видит хранимый остаток
        assertEquals(Money(220_000), state.netWorth)
        assertEquals(setOf("brokerage"), state.holdingAccountIds)
    }

    @Test
    fun `net worth history follows portfolio snapshots`() {
        val snapshots = listOf(PortfolioSnapshot(LocalDate.of(2026, 9, 10), Money(100_000)))
        val state = buildAccounts(today, listOf(checking, brokerage), emptyList(), listOf(fund), snapshots)

        assertEquals(200_000L, state.history.first()) // до первого снимка — его значение
        assertEquals(200_000L, state.history[27]) // конец 11-го
        assertEquals(220_000L, state.history.last())
        assertEquals(Money(20_000), state.change)
    }

    @Test
    fun `snapshots without holdings do not move net worth`() {
        val snapshots = listOf(PortfolioSnapshot(LocalDate.of(2026, 9, 10), Money(100_000)))
        val state = buildAccounts(today, listOf(checking), emptyList(), emptyList(), snapshots)

        assertEquals(List(32) { 100_000L }, state.history)
    }

    @Test
    fun `period sets how far back the history goes`() {
        val day = buildAccounts(today, listOf(checking), emptyList(), period = TransactionPeriod.Day)
        assertEquals(2, day.history.size)
        assertEquals("Sep 14", day.firstLabel)

        val week = buildAccounts(today, listOf(checking), emptyList(), period = TransactionPeriod.Week)
        assertEquals(8, week.history.size)
        assertEquals("Sep 8", week.firstLabel)

        // Год: с конца 15 сентября 2025-го, подпись с годом.
        val year = buildAccounts(today, listOf(checking), emptyList(), period = TransactionPeriod.Year)
        assertEquals(366, year.history.size)
        assertEquals("Sep 15, 2025", year.firstLabel)
    }

    @Test
    fun `change counts only the period`() {
        val transactions = listOf(tx("checking", 50_000, 10), tx("checking", -10_000, 15))

        assertEquals(Money(40_000), buildAccounts(today, listOf(checking), transactions, period = TransactionPeriod.Week).change)
        assertEquals(Money(-10_000), buildAccounts(today, listOf(checking), transactions, period = TransactionPeriod.Day).change)
    }
}
