package com.artemkhateev.finance.feature.categories

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.NewestFirst
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.sumOfMoney
import java.time.LocalDate

/**
 * Порядок категорий на экране: и полос бюджетов, и остальных категорий. При равенстве — по имени;
 * во всех сортировках, кроме [Name], доходы идут после расходов.
 */
enum class CategorySort(val title: String) {
    /** По имени, без учёта регистра. */
    Name("Name"),

    /** Больше потрачено за месяц — выше; у доходов — получено. */
    Spent("Spent"),

    /** Бюджеты — по доле потраченного, перерасход сверху; категории без бюджета — как [Spent]. */
    BudgetUsed("Budget used"),

    /** Больше транзакций за месяц — выше. */
    Transactions("Transactions");

    companion object {
        /** Сохранённое значение; незнакомое или пустое — по имени. */
        fun fromKey(key: String?): CategorySort = entries.firstOrNull { it.name == key } ?: Name
    }
}

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
    val sort: CategorySort = CategorySort.Name,
)

data class CategoryDetailUi(
    val category: Category,
    /** Потрачено за месяц для расходной категории, получено — для доходной. */
    val amount: Money,
    val budget: Money?,
    /** Транзакции категории за этот месяц, новые сверху. */
    val transactions: List<Transaction>,
)

private val ByName = compareBy<Category, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

fun buildCategories(
    today: LocalDate,
    categories: List<Category>,
    transactions: List<Transaction>,
    sort: CategorySort = CategorySort.Name,
): CategoriesUiState {
    val thisMonth = monthTransactions(today, transactions)
    val spentByCategory = thisMonth.filter { it.amount.minor < 0 }
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> -list.sumOf { it.amount.minor } }
    val receivedByCategory = thisMonth.filter { it.amount.minor > 0 }
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> list.sumOf { it.amount.minor } }
    val countByCategory = thisMonth.groupingBy { it.categoryId }.eachCount()
    fun count(category: Category) = countByCategory[category.id] ?: 0

    val budgets = categories
        .filter { it.kind == CategoryKind.Expense && (it.monthlyBudget?.minor ?: 0L) > 0L }
        .map { category ->
            val budget = category.monthlyBudget!!
            val spent = Money(spentByCategory[category.id] ?: 0L)
            CategoryBudgetUi(category, spent, budget, spent.minor.toFloat() / budget.minor)
        }
        .sortedWith(
            when (sort) {
                CategorySort.Name -> compareBy<CategoryBudgetUi, Category>(ByName) { it.category }
                CategorySort.Spent -> compareByDescending<CategoryBudgetUi> { it.spent.minor }.thenBy(ByName) { it.category }
                CategorySort.BudgetUsed -> compareByDescending<CategoryBudgetUi> { it.ratio }.thenBy(ByName) { it.category }
                CategorySort.Transactions ->
                    compareByDescending<CategoryBudgetUi> { count(it.category) }.thenBy(ByName) { it.category }
            },
        )
    val budgetedIds = budgets.map { it.category.id }.toSet()
    val incomeLast = compareBy<CategoryAmountUi> { it.category.kind == CategoryKind.Income }
    val others = categories
        .filterNot { it.id in budgetedIds }
        .map { category ->
            val byCategory = if (category.kind == CategoryKind.Income) receivedByCategory else spentByCategory
            CategoryAmountUi(category, Money(byCategory[category.id] ?: 0L))
        }
        .sortedWith(
            when (sort) {
                CategorySort.Name -> compareBy<CategoryAmountUi, Category>(ByName) { it.category }
                // Без бюджета доли нет — такие категории идут по сумме.
                CategorySort.Spent, CategorySort.BudgetUsed ->
                    incomeLast.thenByDescending { it.amount.minor }.thenBy(ByName) { it.category }
                CategorySort.Transactions -> incomeLast.thenByDescending { count(it.category) }.thenBy(ByName) { it.category }
            },
        )

    return CategoriesUiState(
        totalLeft = budgets.sumOfMoney { it.budget } - budgets.sumOfMoney { it.spent },
        budgets = budgets,
        others = others,
        sort = sort,
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
