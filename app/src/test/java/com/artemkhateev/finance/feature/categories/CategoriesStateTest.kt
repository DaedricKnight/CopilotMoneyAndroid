package com.artemkhateev.finance.feature.categories

import com.artemkhateev.finance.data.model.Category
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

    private fun spend(cents: Long, category: String, date: LocalDate = today.withDayOfMonth(5)) =
        Transaction("t-$category-$cents", "acc", "Shop", Money(-cents), date, category)

    @Test
    fun `total left is budgets minus spending`() {
        val state = buildCategories(today, listOf(food, leisure), listOf(spend(25_000, "food"), spend(12_000, "leisure")))

        assertEquals(Money(3_000), state.totalLeft)
        assertEquals(1.2f, state.budgets.single { it.category.id == "leisure" }.ratio, 0.001f)
    }

    @Test
    fun `spending without a budget is listed separately`() {
        val state = buildCategories(today, listOf(food, gifts), listOf(spend(4_000, "gifts")))

        assertEquals(listOf("food"), state.budgets.map { it.category.id })
        assertEquals(listOf("gifts"), state.unbudgeted.map { it.category.id })
    }

    @Test
    fun `detail shows only this month transactions of the category`() {
        val detail = buildCategoryDetail(
            today,
            "food",
            listOf(food),
            listOf(spend(1_000, "food"), spend(2_000, "food", LocalDate.of(2026, 8, 30)), spend(3_000, "leisure")),
        )

        assertEquals(Money(1_000), detail?.spent)
        assertEquals(1, detail?.transactions?.size)
    }
}
