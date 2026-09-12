package com.artemkhateev.finance.data.demo

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryByName
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.sumOfMoney
import com.artemkhateev.finance.data.model.value
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DemoFinanceRepositoryTest {

    private val today = LocalDate.of(2026, 9, 12)
    private val repository = DemoFinanceRepository(today)

    private suspend fun balanceOf(accountId: String) = repository.accounts.first().single { it.id == accountId }.balance

    @Test
    fun `new category gets an id and categories stay in name order`() = runBlocking {
        repository.saveCategory(Category("", "Books", "📚", CategoryTone.Teal, monthlyBudget = Money(3_000)))
        val categories = repository.categories.first()

        assertTrue(categories.single { it.name == "Books" }.id.isNotBlank())
        assertEquals(categories.sortedWith(CategoryByName), categories)
    }

    @Test
    fun `edited category replaces the old one`() = runBlocking {
        val coffee = repository.categories.first().single { it.id == "coffee" }
        repository.saveCategory(coffee.copy(monthlyBudget = Money(6_000)))

        assertEquals(Money(6_000), repository.categories.first().single { it.id == "coffee" }.monthlyBudget)
    }

    @Test
    fun `deleted category disappears but its transactions stay`() = runBlocking {
        val coffeeTransactions = repository.transactions.first().count { it.categoryId == "coffee" }
        repository.deleteCategory("coffee")

        assertTrue(repository.categories.first().none { it.id == "coffee" })
        assertEquals(coffeeTransactions, repository.transactions.first().count { it.categoryId == "coffee" })
    }

    @Test
    fun `adding an expense lowers the account balance`() = runBlocking {
        val before = balanceOf("checking")
        repository.saveTransaction(Transaction("", "checking", "Bakery", Money(-1_250), today, null, reviewed = true))

        assertEquals(before - Money(1_250), balanceOf("checking"))
    }

    @Test
    fun `moving a transaction to another account moves its amount`() = runBlocking {
        val saved = Transaction("t-move", "checking", "Bakery", Money(-1_000), today, null)
        repository.saveTransaction(saved)
        val checkingBefore = balanceOf("checking")
        val cardBefore = balanceOf("card")

        repository.saveTransaction(saved.copy(accountId = "card"), previous = saved)

        assertEquals(checkingBefore + Money(1_000), balanceOf("checking"))
        assertEquals(cardBefore - Money(1_000), balanceOf("card"))
    }

    @Test
    fun `deleting a transaction gives its amount back`() = runBlocking {
        val before = balanceOf("card")
        val saved = Transaction("t-delete", "card", "Bakery", Money(-500), today, null)
        repository.saveTransaction(saved)
        repository.deleteTransaction(saved)

        assertEquals(before, balanceOf("card"))
    }

    @Test
    fun `deleting an account deletes its holdings`() = runBlocking {
        assertTrue(repository.holdings.first().any { it.accountId == "brokerage" })
        repository.deleteAccount("brokerage")

        assertTrue(repository.holdings.first().none { it.accountId == "brokerage" })
    }

    @Test
    fun `second snapshot of the day replaces the first`() = runBlocking {
        repository.recordPortfolioValue(PortfolioSnapshot(today, Money(1_000)))
        repository.recordPortfolioValue(PortfolioSnapshot(today, Money(2_000)))
        val history = repository.portfolioHistory.first()

        assertEquals(Money(2_000), history.single { it.date == today }.value)
        assertEquals(history.sortedBy { it.date.toEpochDay() }, history)
    }

    @Test
    fun `demo portfolio history ends at today's value`() = runBlocking {
        val value = repository.holdings.first().sumOfMoney { it.value }
        assertEquals(PortfolioSnapshot(today, value), repository.portfolioHistory.first().last())
    }
}
