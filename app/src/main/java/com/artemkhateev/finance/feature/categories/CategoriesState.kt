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

/** Категория вне карточки бюджетов с суммой за месяц: потрачено для расходов, получено для доходов. */
data class CategoryAmountUi(val category: Category, val amount: Money)

data class CategoriesUiState(
    /** Сумма бюджетов минус расходы по ним; отрицательная — перерасход. */
    val totalLeft: Money,
    val budgets: List<CategoryBudgetUi>,
    /** Все остальные категории — расходы без бюджета и доходы, — чтобы до любой можно было дотянуться. */
    val others: List<CategoryAmountUi>,
)

data class CategoryDetailUi(
    val category: Category,
    /** Потрачено за месяц для расходной категории, получено — для доходной. */
    val amount: Money,
    val budget: Money?,
    /** Транзакции категории за этот месяц, новые сверху. */
    val transactions: List<Transaction>,
)

fun buildCategories(today: LocalDate, categories: List<Category>, transactions: List<Transaction>): CategoriesUiState {
    val thisMonth = monthTransactions(today, transactions)
    val spentByCategory = thisMonth.filter { it.amount.minor < 0 }
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> -list.sumOf { it.amount.minor } }
    val receivedByCategory = thisMonth.filter { it.amount.minor > 0 }
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> list.sumOf { it.amount.minor } }

    val budgets = categories
        .filter { it.kind == CategoryKind.Expense && (it.monthlyBudget?.minor ?: 0L) > 0L }
        .map { category ->
            val budget = category.monthlyBudget!!
            val spent = Money(spentByCategory[category.id] ?: 0L)
            CategoryBudgetUi(category, spent, budget, spent.minor.toFloat() / budget.minor)
        }
    val budgetedIds = budgets.map { it.category.id }.toSet()
    val others = categories
        .filterNot { it.id in budgetedIds }
        .map { category ->
            val byCategory = if (category.kind == CategoryKind.Income) receivedByCategory else spentByCategory
            CategoryAmountUi(category, Money(byCategory[category.id] ?: 0L))
        }

    return CategoriesUiState(
        totalLeft = budgets.sumOfMoney { it.budget } - budgets.sumOfMoney { it.spent },
        budgets = budgets,
        others = others,
    )
}

fun buildCategoryDetail(
    today: LocalDate,
    categoryId: String,
    categories: List<Category>,
    transactions: List<Transaction>,
): CategoryDetailUi? {
    val category = categories.firstOrNull { it.id == categoryId } ?: return null
    val ofCategory = monthTransactions(today, transactions).filter { it.categoryId == categoryId }.sortedWith(NewestFirst)
    val amount = if (category.kind == CategoryKind.Income) {
        ofCategory.filter { it.amount.minor > 0 }.sumOf { it.amount.minor }
    } else {
        -ofCategory.filter { it.amount.minor < 0 }.sumOf { it.amount.minor }
    }
    return CategoryDetailUi(
        category = category,
        amount = Money(amount),
        budget = category.monthlyBudget?.takeIf { it.minor > 0 && category.kind == CategoryKind.Expense },
        transactions = ofCategory,
    )
}

private fun monthTransactions(today: LocalDate, transactions: List<Transaction>): List<Transaction> {
    val monthStart = today.withDayOfMonth(1)
    return transactions.filter { !it.date.isBefore(monthStart) && !it.date.isAfter(today) }
}
