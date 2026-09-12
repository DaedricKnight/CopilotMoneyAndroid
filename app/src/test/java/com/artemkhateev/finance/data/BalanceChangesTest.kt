package com.artemkhateev.finance.data

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BalanceChangesTest {

    private val day = LocalDate.of(2026, 9, 12)

    private fun tx(account: String, cents: Long) = Transaction("t", account, "Shop", Money(cents), day, null)

    @Test
    fun `new transaction shifts its account`() {
        assertEquals(mapOf("cash" to -500L), balanceChanges(saved = tx("cash", -500), previous = null))
    }

    @Test
    fun `edit shifts only by the difference`() {
        assertEquals(mapOf("cash" to -200L), balanceChanges(saved = tx("cash", -700), previous = tx("cash", -500)))
    }

    @Test
    fun `moving to another account moves the whole amount`() {
        assertEquals(
            mapOf("cash" to 500L, "card" to -500L),
            balanceChanges(saved = tx("card", -500), previous = tx("cash", -500)),
        )
    }

    @Test
    fun `unchanged amount gives no changes`() {
        assertEquals(emptyMap<String, Long>(), balanceChanges(saved = tx("cash", -500), previous = tx("cash", -500)))
    }

    @Test
    fun `delete gives the amount back`() {
        assertEquals(mapOf("card" to 500L), balanceChanges(saved = null, previous = tx("card", -500)))
    }
}
