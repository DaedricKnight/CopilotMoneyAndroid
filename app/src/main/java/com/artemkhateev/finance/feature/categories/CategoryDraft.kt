package com.artemkhateev.finance.feature.categories

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.ui.format.amountInputText
import com.artemkhateev.finance.ui.format.parseAmount

/** Форма категории. Бюджет хранится текстом; пустой — категория без бюджета. */
data class CategoryDraft(
    /** Пустой — новая категория: id выдаст репозиторий. */
    val id: String = "",
    val name: String = "",
    val emoji: String = "",
    val tone: CategoryTone = CategoryTone.Blue,
    val kind: CategoryKind = CategoryKind.Expense,
    val budgetText: String = "",
) {
    /** У доходов бюджета не бывает. */
    val budget: Money?
        get() = if (kind == CategoryKind.Income) null else parseAmount(budgetText)?.takeIf { it.minor > 0 }

    /** Почему сохранить нельзя; null — можно. */
    fun problem(existing: List<Category>): String? = when {
        emoji.isBlank() -> "Pick an emoji"
        name.isBlank() -> "Name the category"
        existing.any { it.id != id && it.name.equals(name.trim(), ignoreCase = true) } ->
            "A category with this name already exists"
        kind == CategoryKind.Expense && budgetText.isNotBlank() && budget == null -> "Budget should be more than zero"
        else -> null
    }

    fun toCategory() = Category(
        id = id,
        name = name.trim(),
        emoji = emoji,
        tone = tone,
        kind = kind,
        monthlyBudget = budget,
    )

    companion object {
        fun from(category: Category) = CategoryDraft(
            id = category.id,
            name = category.name,
            emoji = category.emoji,
            tone = category.tone,
            kind = category.kind,
            budgetText = category.monthlyBudget?.let(::amountInputText).orEmpty(),
        )
    }
}
