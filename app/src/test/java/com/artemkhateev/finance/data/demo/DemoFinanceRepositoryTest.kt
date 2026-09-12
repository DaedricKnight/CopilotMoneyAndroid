package com.artemkhateev.finance.data.demo

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryByName
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DemoFinanceRepositoryTest {

    private val repository = DemoFinanceRepository(LocalDate.of(2026, 9, 12))

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
}
