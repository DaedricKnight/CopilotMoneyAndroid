package com.artemkhateev.finance.feature.investments

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.QUANTITY_SCALE
import com.artemkhateev.finance.data.model.holdingValue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InvestmentsStateTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val brokerage = Account("brokerage", "Brokerage", "", AccountType.Investment, Money.Zero)

    private fun holding(id: String, assetClass: AssetClass, units: Long, cost: Long, price: Long, account: String = "brokerage") =
        Holding(id, account, id.uppercase(), id, assetClass, units * QUANTITY_SCALE, Money(cost), Money(price), today)

    private fun snapshot(month: Int, day: Int, cents: Long) = PortfolioSnapshot(LocalDate.of(2026, month, day), Money(cents))

    private val fund = holding("fund", AssetClass.Fund, units = 1, cost = 90_000, price = 110_000)

    @Test
    fun `value and gain come from quantity, price and average cost`() {
        val holdings = listOf(
            holding("fund", AssetClass.Fund, units = 10, cost = 10_000, price = 12_000),
            holding("bond", AssetClass.Bond, units = 5, cost = 10_000, price = 9_000),
        )
        val state = buildInvestments(today, InvestmentRange.OneMonth, listOf(brokerage), holdings, emptyList())

        assertEquals(Money(165_000), state.value)
        assertEquals(Money(15_000), state.gain)
        assertEquals(100, state.gainTenths)
        assertEquals(listOf("fund", "bond"), state.holdings.map { it.holding.id })
        assertEquals(-100, state.holdings.last().gainTenths)
    }

    @Test
    fun `allocation groups asset classes, largest first`() {
        val holdings = listOf(
            holding("a", AssetClass.Stock, units = 1, cost = 20_000, price = 20_000),
            holding("b", AssetClass.Fund, units = 1, cost = 60_000, price = 60_000),
            holding("c", AssetClass.Stock, units = 1, cost = 20_000, price = 20_000),
        )
        val state = buildInvestments(today, InvestmentRange.OneMonth, listOf(brokerage), holdings, emptyList())

        assertEquals(listOf(AssetClass.Fund, AssetClass.Stock), state.allocation.map { it.assetClass })
        assertEquals(0.6f, state.allocation.first().share, 0.0001f)
        assertEquals(Money(40_000), state.allocation.last().value)
    }

    @Test
    fun `chart goes day by day from the range start and ends at the live value`() {
        val snapshots = listOf(snapshot(8, 1, 90_000), snapshot(9, 1, 100_000), snapshot(9, 10, 105_000))
        val state = buildInvestments(today, InvestmentRange.OneMonth, listOf(brokerage), listOf(fund), snapshots)

        // С 15 августа по 15 сентября — 32 дня.
        assertEquals(32, state.history.size)
        assertEquals(90_000L, state.history.first()) // 15 августа действует снимок 1 августа
        assertEquals(100_000L, state.history[17]) // 1 сентября
        assertEquals(105_000L, state.history[30]) // 14 сентября
        assertEquals(110_000L, state.history.last())
        assertEquals(Money(20_000), state.rangeChange)
        assertEquals("Aug 15", state.firstLabel)
    }

    @Test
    fun `chart starts at the first snapshot when history is shorter than the range`() {
        val state = buildInvestments(today, InvestmentRange.OneYear, listOf(brokerage), listOf(fund), listOf(snapshot(9, 13, 100_000)))

        assertEquals(listOf(100_000L, 100_000L, 110_000L), state.history)
        assertEquals("Sep 13", state.firstLabel)
    }

    @Test
    fun `without snapshots the chart is flat at today's value`() {
        val state = buildInvestments(today, InvestmentRange.ThreeMonths, listOf(brokerage), listOf(fund), emptyList())

        assertEquals(listOf(110_000L, 110_000L), state.history)
        assertEquals(Money.Zero, state.rangeChange)
    }

    @Test
    fun `holdings of deleted accounts are not counted`() {
        val orphan = holding("orphan", AssetClass.Stock, units = 1, cost = 5_000, price = 5_000, account = "gone")
        val state = buildInvestments(today, InvestmentRange.OneMonth, listOf(brokerage), listOf(fund, orphan), emptyList())

        assertEquals(listOf("fund"), state.holdings.map { it.holding.id })
        assertEquals(Money(110_000), state.value)
    }

    @Test
    fun `value of a fractional quantity rounds to the cent`() {
        assertEquals(Money(271_000), holdingValue(QUANTITY_SCALE / 20, Money(5_420_000)))
        assertEquals(Money(333), holdingValue(333_333, Money(1_000)))
        assertEquals(Money(1), holdingValue(500_000, Money(1)))
    }
}
