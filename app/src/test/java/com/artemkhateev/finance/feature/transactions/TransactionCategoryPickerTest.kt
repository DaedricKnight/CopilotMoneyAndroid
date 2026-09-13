package com.artemkhateev.finance.feature.transactions

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCategoryPickerTest {

    private fun category(id: String, name: String, kind: CategoryKind = CategoryKind.Expense) =
        Category(id, name, "🙂", CategoryTone.Gray, kind)

    private val food = category("food", "Food")
    private val car = category("car", "Car")
    private val rent = category("rent", "Rent")
    private val salary = category("salary", "Salary", CategoryKind.Income)

    @Test
    fun `frequent categories come first and the selected one is always shown`() {
        val usage = mapOf("rent" to 5, "car" to 2)

        assertEquals(listOf("rent", "car", "food"), quickCategories(listOf(food, car, rent, salary), CategoryKind.Expense, usage, null, 3).map { it.id })
        assertEquals(listOf("rent", "food"), quickCategories(listOf(food, car, rent), CategoryKind.Expense, usage, selectedId = "food", limit = 2).map { it.id })
    }

    @Test
    fun `catalog offers only missing categories of the same kind`() {
        val choices = catalogChoices(listOf(category("g", "groceries")), CategoryKind.Expense, query = "")

        assertTrue(choices.none { it.name == "Groceries" })
        assertTrue(choices.all { it.kind == CategoryKind.Expense })
        assertTrue(choices.any { it.name == "Hotels" })
    }

    @Test
    fun `search narrows the catalog by name`() {
        assertEquals(listOf("Salary"), catalogChoices(emptyList(), CategoryKind.Income, query = " sal").map { it.name })
    }
}
