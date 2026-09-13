package com.artemkhateev.finance.feature.categories

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySuggestionsTest {

    private fun category(name: String) = Category("id-$name", name, "🙂", CategoryTone.Gray)

    @Test
    fun `catalog has unique names and every entry has an emoji and a group`() {
        val names = CategoryCatalog.map { it.name.lowercase() }

        assertEquals(names.distinct(), names)
        assertTrue(CategoryCatalog.all { it.emoji.isNotBlank() && it.group.isNotBlank() && it.keywords.isNotEmpty() })
        assertTrue(CategoryCatalog.size >= 40)
        assertEquals(CategoryKind.Income, CategoryCatalog.single { it.name == "Salary" }.kind)
    }

    @Test
    fun `category you already have is marked added`() {
        val groceries = categorySuggestions(listOf(category("groceries"))).single { it.suggestion.name == "Groceries" }

        assertTrue(groceries.added)
        assertFalse(groceries.selectedByDefault)
    }

    @Test
    fun `similar categories leave the suggestion unticked`() {
        val existing = listOf(category("Taxi"), category("Lunch, fast food"), category("Spa & beauty"))
        val suggestions = categorySuggestions(existing).associateBy { it.suggestion.name }

        assertEquals("Taxi", suggestions.getValue("Taxi & rideshare").similarTo)
        assertEquals("Lunch, fast food", suggestions.getValue("Restaurants").similarTo)
        assertEquals("Spa & beauty", suggestions.getValue("Personal care").similarTo)
        assertTrue(suggestions.getValue("Coffee").selectedByDefault)
    }

    @Test
    fun `short keywords match whole words only`() {
        val car = categorySuggestions(listOf(category("Credit card fees"))).single { it.suggestion.name == "Car" }
        assertNull(car.similarTo)
    }
}
