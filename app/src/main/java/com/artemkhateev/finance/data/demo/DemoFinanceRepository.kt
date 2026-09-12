package com.artemkhateev.finance.data.demo

import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.balanceChanges
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountByName
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryByName
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.NewestFirst
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.newAccountId
import com.artemkhateev.finance.data.model.newCategoryId
import com.artemkhateev.finance.data.model.newTransactionId
import com.artemkhateev.finance.data.transactionsWindowStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/** Данные в памяти для разработки интерфейса: живут до перезапуска приложения. */
class DemoFinanceRepository(today: LocalDate = LocalDate.now()) : FinanceRepository {

    private val categoriesState = MutableStateFlow(DemoData.categories)
    private val accountsState = MutableStateFlow(DemoData.accounts)
    private val transactionsState = MutableStateFlow(DemoData.transactions(today))

    override val categories: Flow<List<Category>> = categoriesState.map { it.sortedWith(CategoryByName) }
    override val accounts: Flow<List<Account>> = accountsState.map { it.sortedWith(AccountByName) }
    override val transactions: Flow<List<Transaction>> = transactionsState
    override val recurrings: Flow<List<Recurring>> = MutableStateFlow(DemoData.recurrings)

    override suspend fun markReviewed(transactionIds: Collection<String>) {
        val ids = transactionIds.toSet()
        transactionsState.update { list ->
            list.map { if (it.id in ids) it.copy(reviewed = true) else it }
        }
    }

    override suspend fun setCategory(transactionId: String, categoryId: String?) {
        transactionsState.update { list ->
            list.map { if (it.id == transactionId) it.copy(categoryId = categoryId) else it }
        }
    }

    override suspend fun saveTransaction(transaction: Transaction, previous: Transaction?) {
        val saved = if (transaction.id.isBlank()) transaction.copy(id = newTransactionId()) else transaction
        transactionsState.update { list -> (list.filterNot { it.id == saved.id } + saved).sortedWith(NewestFirst) }
        applyBalanceChanges(balanceChanges(saved, previous))
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        transactionsState.update { list -> list.filterNot { it.id == transaction.id } }
        applyBalanceChanges(balanceChanges(saved = null, previous = transaction))
    }

    override suspend fun saveCategory(category: Category) {
        val saved = if (category.id.isBlank()) category.copy(id = newCategoryId()) else category
        categoriesState.update { list -> list.filterNot { it.id == saved.id } + saved }
    }

    override suspend fun deleteCategory(categoryId: String) {
        categoriesState.update { list -> list.filterNot { it.id == categoryId } }
    }

    override suspend fun saveAccount(account: Account) {
        val saved = if (account.id.isBlank()) account.copy(id = newAccountId()) else account
        accountsState.update { list -> list.filterNot { it.id == saved.id } + saved }
    }

    override suspend fun deleteAccount(accountId: String) {
        accountsState.update { list -> list.filterNot { it.id == accountId } }
    }

    private fun applyBalanceChanges(changes: Map<String, Long>) {
        if (changes.isEmpty()) return
        accountsState.update { list ->
            list.map { account -> changes[account.id]?.let { account.copy(balance = account.balance + Money(it)) } ?: account }
        }
    }
}

internal object DemoData {

    val categories = listOf(
        Category("rent", "Rent", "🔑", CategoryTone.Orange, monthlyBudget = Money.of(1200.0)),
        Category("groceries", "Groceries", "🥑", CategoryTone.Green, monthlyBudget = Money.of(500.0)),
        Category("restaurants", "Restaurants", "🍔", CategoryTone.Yellow, monthlyBudget = Money.of(260.0)),
        Category("car", "Car", "🚙", CategoryTone.Blue, monthlyBudget = Money.of(160.0)),
        Category("transport", "Transport", "🚌", CategoryTone.Purple, monthlyBudget = Money.of(60.0)),
        Category("entertainment", "Entertainment", "🎟️", CategoryTone.Magenta, monthlyBudget = Money.of(70.0)),
        Category("clothing", "Clothing", "👕", CategoryTone.Teal, monthlyBudget = Money.of(120.0)),
        Category("subscriptions", "Subscriptions", "💳", CategoryTone.Pink, monthlyBudget = Money.of(45.0)),
        Category("utilities", "Utilities", "💡", CategoryTone.Gray, monthlyBudget = Money.of(160.0)),
        Category("coffee", "Coffee", "☕", CategoryTone.Red, monthlyBudget = Money.of(40.0)),
        Category("income", "Income", "💰", CategoryTone.Green, kind = CategoryKind.Income),
    )

    val accounts = listOf(
        Account("checking", "Checking", "Demo Bank", AccountType.Checking, Money.of(7989.12), mask = "2124"),
        Account("savings", "Savings", "Demo Bank", AccountType.Savings, Money.of(15200.0), mask = "8830"),
        Account("card", "Credit Card", "Demo Card", AccountType.CreditCard, Money.of(-642.37), mask = "4412"),
    )

    val recurrings = listOf(
        Recurring("r-rent", "Rent", "🏠", Money.of(1200.0), dayOfMonth = 1, categoryId = "rent"),
        Recurring("r-music", "Music streaming", "🎧", Money.of(10.99), dayOfMonth = 3, categoryId = "subscriptions"),
        Recurring("r-car", "Car payment", "🚙", Money.of(95.0), dayOfMonth = 5, categoryId = "car"),
        Recurring("r-power", "Electricity", "⚡", Money.of(75.0), dayOfMonth = 8, categoryId = "utilities"),
        Recurring("r-water", "Water", "🚰", Money.of(32.0), dayOfMonth = 12, categoryId = "utilities"),
        Recurring("r-insurance", "Insurance", "☂️", Money.of(45.9), dayOfMonth = 15, categoryId = "utilities"),
        Recurring("r-gym", "Gym", "🏋️", Money.of(39.0), dayOfMonth = 20, categoryId = "subscriptions"),
    )

    private val coffee = listOf("Kava Bar", "Bean There")
    private val groceries = listOf("Green Grocer", "City Market", "Corner Store")
    private val restaurants = listOf("Trattoria Mare", "Burger Lab", "Noodle House")
    private val entertainment = listOf("Movie Night", "Concert Hall", "Bowling Alley")

    /** Прошлый месяц целиком и текущий по сегодня, новые сверху. */
    fun transactions(today: LocalDate): List<Transaction> {
        // Зерно от месяца: цифры стабильны между запусками, но меняются с месяцем.
        val random = Random(today.year * 100 + today.monthValue)
        val result = mutableListOf<Transaction>()

        fun add(date: LocalDate, merchant: String, amount: Double, categoryId: String, accountId: String = "card") {
            result += Transaction(
                id = "demo-${result.size}",
                accountId = accountId,
                merchant = merchant,
                amount = Money.of(amount),
                date = date,
                categoryId = categoryId,
                // Всё старше двух дней уже просмотрено, свежее ждёт в «To review».
                reviewed = date.isBefore(today.minusDays(2)),
            )
        }

        fun cents(from: Double, to: Double) = random.nextInt((from * 100).toInt(), (to * 100).toInt()) / 100.0
        fun chance(oneIn: Int) = random.nextInt(oneIn) == 0

        var date = transactionsWindowStart(today)
        while (!date.isAfter(today)) {
            when (date.dayOfMonth) {
                1 -> {
                    add(date, "Salary", 4850.0, "income", accountId = "checking")
                    add(date, "Rent", -1200.0, "rent", accountId = "checking")
                }
                3 -> add(date, "Music streaming", -10.99, "subscriptions")
                5 -> add(date, "Car payment", -95.0, "car", accountId = "checking")
                8 -> add(date, "Electricity", -cents(62.0, 88.0), "utilities", accountId = "checking")
                12 -> add(date, "Water", -32.0, "utilities", accountId = "checking")
                15 -> add(date, "Insurance", -45.9, "utilities", accountId = "checking")
                20 -> add(date, "Gym", -39.0, "subscriptions")
            }
            val weekday = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
            if (weekday && chance(2)) add(date, coffee.random(random), -cents(2.5, 4.8), "coffee")
            if (chance(3)) add(date, groceries.random(random), -cents(12.0, 68.0), "groceries")
            if (chance(4)) add(date, restaurants.random(random), -cents(14.0, 55.0), "restaurants")
            if (chance(6)) add(date, "City Transit", -cents(2.0, 18.0), "transport")
            if (chance(12)) add(date, "Fuel Station", -cents(45.0, 70.0), "car")
            if (chance(10)) add(date, entertainment.random(random), -cents(12.0, 40.0), "entertainment")
            if (chance(18)) add(date, "Threads Store", -cents(25.0, 90.0), "clothing")
            date = date.plusDays(1)
        }

        // Блоку «So far today» на дашборде всегда есть что показать.
        add(today, "Trattoria Mare", -37.5, "restaurants")
        add(today, "Green Grocer", -15.4, "groceries")

        return result.asReversed()
    }
}
