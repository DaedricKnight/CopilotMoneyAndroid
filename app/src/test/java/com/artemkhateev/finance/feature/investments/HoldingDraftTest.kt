package com.artemkhateev.finance.feature.investments

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HoldingDraftTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val draft = HoldingDraft(symbol = "vwce", quantityText = "1.5", priceText = "120", costText = "100")

    @Test
    fun `complete draft becomes a holding with an upper-case ticker`() {
        val holding = draft.toHolding(today, "brokerage", previous = null)

        assertEquals("VWCE", holding.symbol)
        assertEquals("VWCE", holding.name)
        assertEquals(1_500_000L, holding.quantityMicros)
        assertEquals(Money(12_000), holding.price)
        assertEquals(Money(10_000), holding.costPerUnit)
        assertEquals(today, holding.priceUpdated)
    }

    @Test
    fun `empty average cost means bought at the current price`() {
        val holding = draft.copy(costText = "").toHolding(today, "brokerage", previous = null)
        assertEquals(holding.price, holding.costPerUnit)
    }

    @Test
    fun `price date moves only when the price changes`() {
        val saved = draft.toHolding(LocalDate.of(2026, 9, 1), "brokerage", previous = null).copy(id = "h1")

        val moreUnits = HoldingDraft.from(saved).copy(quantityText = "2").toHolding(today, "brokerage", saved)
        val newPrice = HoldingDraft.from(saved).copy(priceText = "125").toHolding(today, "brokerage", saved)

        assertEquals(LocalDate.of(2026, 9, 1), moreUnits.priceUpdated)
        assertEquals(today, newPrice.priceUpdated)
    }

    @Test
    fun `problems explain what is missing`() {
        assertEquals("Enter a ticker or a short name", draft.copy(symbol = " ").problem())
        assertEquals("Quantity should be more than zero", draft.copy(quantityText = "0").problem())
        assertEquals("Price should be more than zero", draft.copy(priceText = "").problem())
        assertNull(draft.problem())
    }

    @Test
    fun `preview shows value and gain while typing`() {
        assertEquals(Money(18_000), draft.previewValue)
        assertEquals(Money(3_000), draft.previewGain)
        assertEquals(200, draft.previewGainTenths)
        assertNull(draft.copy(priceText = "").previewValue)
    }

    @Test
    fun `editing shows numbers the way they were typed`() {
        val holding = draft.copy(name = "All-World").toHolding(today, "brokerage", previous = null).copy(id = "h1")
        val reopened = HoldingDraft.from(holding)

        assertEquals("1.5", reopened.quantityText)
        assertEquals("120", reopened.priceText)
        assertEquals("100", reopened.costText)
        assertEquals("brokerage", reopened.accountId)
    }

    @Test
    fun `new brokerage account avoids taken names`() {
        val taken = listOf(
            Account("a", "brokerage", "", AccountType.Checking, Money.Zero),
            Account("b", "Brokerage 2", "", AccountType.Savings, Money.Zero),
        )
        assertEquals("Brokerage 3", freeAccountName("Brokerage", taken))
        assertEquals("Brokerage", freeAccountName("Brokerage", emptyList()))
    }
}
