package com.artemkhateev.finance.feature.importing

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.NewestFirst
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportPlanTest {

    private val today = LocalDate.of(2026, 9, 13)
    private val now = 1_757_790_000_000L

    private fun row(day: Int, cents: Long, merchant: String, month: Int = 9, category: String? = merchant, account: String? = "Cash") =
        ImportRow(LocalDate.of(2026, month, day), Money(cents), merchant, category, account)

    @Test
    fun `new categories and account are created once`() {
        val rows = listOf(row(10, -1_240, "Groceries"), row(10, -3_390, "Groceries"), row(6, -2_200, "Taxi"), row(1, 200_000, "Salary"))
        val plan = planImport(rows, emptyList(), emptyList(), emptyList(), defaultAccountTargets(rows, emptyList()), today, now)

        assertEquals(listOf("Groceries", "Taxi", "Salary"), plan.newCategories.map { it.name })
        assertEquals(listOf("🛒", "🚕", "💰"), plan.newCategories.map { it.emoji })
        assertEquals(CategoryKind.Income, plan.newCategories.last().kind)
        assertEquals(listOf("Cash"), plan.newAccounts.map { it.name })
        assertEquals(4, plan.transactions.size)
        assertTrue(plan.transactions.all { it.accountId == plan.newAccounts.single().id && it.reviewed })
        assertEquals(Money(200_000 - 1_240 - 3_390 - 2_200), plan.total)
    }

    @Test
    fun `existing names are reused and a chosen account wins`() {
        val groceries = Category("food", "groceries", "🥑", CategoryTone.Green)
        val wallet = Account("w1", "Wallet", "", AccountType.Checking, Money.Zero)
        val plan = planImport(listOf(row(4, -815, "Groceries")), listOf(groceries), listOf(wallet), emptyList(), mapOf("Cash" to "w1"), today, now)

        assertTrue(plan.newCategories.isEmpty())
        assertTrue(plan.newAccounts.isEmpty())
        assertEquals("food", plan.transactions.single().categoryId)
        assertEquals("w1", plan.transactions.single().accountId)
    }

    @Test
    fun `transactions already in the app are skipped`() {
        val cash = Account("cash", "Cash", "", AccountType.Checking, Money.Zero)
        val rows = listOf(row(4, -815, "Groceries"), row(4, -4_460, "Groceries"))
        val existing = listOf(Transaction("m1", "cash", "groceries", Money(-815), LocalDate.of(2026, 9, 4), null))
        val plan = planImport(rows, emptyList(), listOf(cash), existing, defaultAccountTargets(rows, listOf(cash)), today, now)

        assertEquals(1, plan.duplicates)
        assertEquals(Money(-4_460), plan.transactions.single().amount)
    }

    @Test
    fun `order of a newest-first file is kept inside a day`() {
        val rows = listOf(row(10, -1_240, "Groceries"), row(10, -3_390, "Groceries"), row(9, -2_050, "Groceries"))
        val plan = planImport(rows, emptyList(), emptyList(), emptyList(), emptyMap(), today, now)

        assertEquals(listOf(-1_240L, -3_390L, -2_050L), plan.transactions.sortedWith(NewestFirst).map { it.amount.minor })
    }

    @Test
    fun `records older than the shown window are counted`() {
        val plan = planImport(listOf(row(20, -500, "Groceries", month = 7), row(4, -815, "Groceries")), emptyList(), emptyList(), emptyList(), emptyMap(), today, now)
        assertEquals(1, plan.olderThanWindow)
    }
}
