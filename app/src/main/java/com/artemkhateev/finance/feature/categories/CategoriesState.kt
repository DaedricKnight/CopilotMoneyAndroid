package com.artemkhateev.finance.feature.categories

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.NewestFirst
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.sumOfMoney
import java.time.LocalDate

data class CategoryBudgetUi(
    val category: Category,
    val spent: Money,
    val budget: Money,
    /** Потрачено от бюджета: 1 — ровно бюджет. */
    val ratio: Float,
)

data class CategorySpendUi(val category: Category, val spent: Money)

data class CategoriesUiState(
    /** Сумма бюджетов минус расходы по ним; отрицательная — перерасход. */
    val totalLeft: Money,
    /** Категории с бюджетом в пользовательском порядке. */
    val budgets: List<CategoryBudgetUi>,
    /** Категории без бюджета, где в этом месяце были расходы. */
    val unbudgeted: List<CategorySpendUi>,
)

data class CategoryDetailUi(
    val category: Category,
    val spent: Money,
    val budget: Money?,
    /** Транзакции категории за этот месяц, новые сверху. */
    val transactions: List<Transaction>,
)

fun buildCategories(today: LocalDate, categories: List<Category>, transactions: List<Transaction>): CategoriesUiState {
    val spentByCategory = monthExpenses(today, transactions)
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> -list.sumOf { it.amount.minor } }
    val expenseCategories = categories.filter { it.kind == CategoryKind.Expense }

    val budgets = expenseCategories
        .filter { (it.monthlyBudget?.minor ?: 0L) > 0L }
        .map { category ->
            val budget = category.monthlyBudget!!
            val spent = Money(spentByCategory[category.id] ?: 0L)
            CategoryBudgetUi(category, spent, budget, spent.minor.toFloat() / budget.minor)
        }
    val unbudgeted = expenseCategories
        .filter { (it.monthlyBudget?.minor ?: 0L) <= 0L }
        .mapNotNull { category -> spentByCategory[category.id]?.let { CategorySpendUi(category, Money(it)) } }
        .sortedByDescending { it.spent.minor }

    return CategoriesUiState(
        totalLeft = budgets.sumOfMoney { it.budget } - budgets.sumOfMoney { it.spent },
        budgets = budgets,
        unbudgeted = unbudgeted,
    )
}

fun buildCategoryDetail(
    today: LocalDate,
    categoryId: String,
    categories: List<Category>,
    transactions: List<Transaction>,
): CategoryDetailUi? {
    val category = categories.firstOrNull { it.id == categoryId } ?: return null
    val monthStart = today.withDayOfMonth(1)
    val thisMonth = transactions
        .filter { it.categoryId == categoryId && !it.date.isBefore(monthStart) && !it.date.isAfter(today) }
        .sortedWith(NewestFirst)
    return CategoryDetailUi(
        category = category,
        spent = Money(-thisMonth.filter { it.amount.minor < 0 }.sumOf { it.amount.minor }),
        budget = category.monthlyBudget?.takeIf { it.minor > 0 },
        transactions = thisMonth,
    )
}

private fun monthExpenses(today: LocalDate, transactions: List<Transaction>): List<Transaction> {
    val monthStart = today.withDayOfMonth(1)
    return transactions.filter { it.amount.minor < 0 && !it.date.isBefore(monthStart) && !it.date.isAfter(today) }
}
