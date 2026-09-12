package com.artemkhateev.finance.feature.categories

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryDraftTest {

    private val food = Category("food", "Food", "🥑", CategoryTone.Green, monthlyBudget = Money(30_000))

    @Test
    fun `complete draft becomes a category with a budget`() {
        val draft = CategoryDraft(name = " Pets ", emoji = "🐶", tone = CategoryTone.Orange, budgetText = "80")

        assertNull(draft.problem(listOf(food)))
        assertEquals(Category("", "Pets", "🐶", CategoryTone.Orange, monthlyBudget = Money(8_000)), draft.toCategory())
    }

    @Test
    fun `name must be unique ignoring case`() {
        assertEquals(
            "A category with this name already exists",
            CategoryDraft(name = "food", emoji = "🍎").problem(listOf(food)),
        )
        // Своё же имя при правке — не дубликат.
        assertNull(CategoryDraft.from(food).problem(listOf(food)))
    }

    @Test
    fun `empty budget means no budget and zero is an error`() {
        assertNull(CategoryDraft(name = "Gifts", emoji = "🎁").toCategory().monthlyBudget)
        assertEquals(
            "Budget should be more than zero",
            CategoryDraft(name = "Gifts", emoji = "🎁", budgetText = "0").problem(emptyList()),
        )
    }

    @Test
    fun `income category has no budget`() {
        val draft = CategoryDraft(name = "Salary", emoji = "💰", kind = CategoryKind.Income, budgetText = "500")

        assertNull(draft.problem(emptyList()))
        assertNull(draft.toCategory().monthlyBudget)
    }

    @Test
    fun `editing without changes keeps the category`() {
        assertEquals(food, CategoryDraft.from(food).toCategory())
    }
}
