package com.artemkhateev.finance.feature.categories

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CategoriesStateTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val food = Category("food", "Food", "🥑", CategoryTone.Green, monthlyBudget = Money(30_000))
    private val leisure = Category("leisure", "Leisure", "🎟️", CategoryTone.Magenta, monthlyBudget = Money(10_000))
    private val gifts = Category("gifts", "Gifts", "🎁", CategoryTone.Pink)
    private val travel = Category("travel", "Travel", "✈️", CategoryTone.Blue)
    private val salary = Category("salary", "Salary", "💰", CategoryTone.Green, kind = CategoryKind.Income)

    private fun tx(cents: Long, category: String, date: LocalDate = today.withDayOfMonth(5)) =
        Transaction("t-$category-$cents-$date", "acc", "Shop", Money(cents), date, category)

    @Test
    fun `total left is budgets minus spending`() {
        val state = buildCategories(today, listOf(food, leisure), listOf(tx(-25_000, "food"), tx(-12_000, "leisure")))

        assertEquals(Money(3_000), state.totalLeft)
        assertEquals(1.2f, state.budgets.single { it.category.id == "leisure" }.ratio, 0.001f)
    }

    @Test
    fun `every category without a budget is listed with its month amount`() {
        val state = buildCategories(today, listOf(food, gifts, salary), listOf(tx(-4_000, "gifts"), tx(485_000, "salary")))

        assertEquals(listOf("food"), state.budgets.map { it.category.id })
        assertEquals(
            listOf("gifts" to Money(4_000), "salary" to Money(485_000)),
            state.others.map { it.category.id to it.amount },
        )
    }

    @Test
    fun `name sort ignores letter case`() {
        val apple = Category("apple", "apple", "🍎", CategoryTone.Red)
        val state = buildCategories(today, listOf(salary, gifts, apple), emptyList(), CategorySort.Name)

        assertEquals(listOf("apple", "gifts", "salary"), state.others.map { it.category.id })
    }

    @Test
    fun `spent sort puts bigger spending first and income after expenses`() {
        val state = buildCategories(
            today,
            listOf(food, leisure, gifts, travel, salary),
            listOf(tx(-8_000, "food"), tx(-9_000, "leisure"), tx(-4_000, "gifts"), tx(-6_000, "travel"), tx(485_000, "salary")),
            CategorySort.Spent,
        )

        assertEquals(listOf("leisure", "food"), state.budgets.map { it.category.id })
        assertEquals(listOf("travel", "gifts", "salary"), state.others.map { it.category.id })
    }

    @Test
    fun `budget used sort puts the biggest share of budget first`() {
        // Food: 250 из 300 (83 %), leisure: 120 из 100 (120 %).
        val transactions = listOf(tx(-25_000, "food"), tx(-12_000, "leisure"))

        assertEquals(
            listOf("food", "leisure"),
            buildCategories(today, listOf(food, leisure), transactions, CategorySort.Spent).budgets.map { it.category.id },
        )
        assertEquals(
            listOf("leisure", "food"),
            buildCategories(today, listOf(food, leisure), transactions, CategorySort.BudgetUsed).budgets.map { it.category.id },
        )
    }

    @Test
    fun `transactions sort counts only this month`() {
        val lastMonth = LocalDate.of(2026, 8, 20)
        val state = buildCategories(
            today,
            listOf(travel, gifts),
            listOf(
                tx(-1_000, "gifts"),
                tx(-2_000, "gifts"),
                tx(-50_000, "travel"),
                tx(-3_000, "travel", lastMonth),
                tx(-4_000, "travel", lastMonth),
            ),
            CategorySort.Transactions,
        )

        assertEquals(listOf("gifts", "travel"), state.others.map { it.category.id })
    }

    @Test
    fun `equal amounts keep name order`() {
        val state = buildCategories(today, listOf(travel, gifts), emptyList(), CategorySort.Spent)

        assertEquals(listOf("gifts", "travel"), state.others.map { it.category.id })
    }

    @Test
    fun `unknown saved sort falls back to name`() {
        assertEquals(CategorySort.Name, CategorySort.fromKey("Oldest"))
        assertEquals(CategorySort.Name, CategorySort.fromKey(null))
        assertEquals(CategorySort.BudgetUsed, CategorySort.fromKey("BudgetUsed"))
    }

    @Test
    fun `detail shows only this month transactions of the category`() {
        val detail = buildCategoryDetail(
            today,
            "food",
            listOf(food),
            listOf(tx(-1_000, "food"), tx(-2_000, "food", LocalDate.of(2026, 8, 30)), tx(-3_000, "leisure")),
        )

        assertEquals(Money(1_000), detail?.amount)
        assertEquals(1, detail?.transactions?.size)
    }
}
