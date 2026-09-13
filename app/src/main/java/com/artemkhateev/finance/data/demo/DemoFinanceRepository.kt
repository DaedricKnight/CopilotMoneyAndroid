package com.artemkhateev.finance.data.demo

import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.balanceChanges
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountByName
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryByName
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.ContributionsNewestFirst
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.NewestFirst
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.QUANTITY_SCALE
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringSchedule
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.newAccountId
import com.artemkhateev.finance.data.model.newCategoryId
import com.artemkhateev.finance.data.model.newContributionId
import com.artemkhateev.finance.data.model.newGoalId
import com.artemkhateev.finance.data.model.newHoldingId
import com.artemkhateev.finance.data.model.newRecurringId
import com.artemkhateev.finance.data.model.newTransactionId
import com.artemkhateev.finance.data.model.value
import com.artemkhateev.finance.data.transactionsWindowStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlin.random.Random

/** Данные в памяти для разработки интерфейса: живут до перезапуска приложения. */
class DemoFinanceRepository(today: LocalDate = LocalDate.now()) : FinanceRepository {

    private val categoriesState = MutableStateFlow(DemoData.categories)
    private val accountsState = MutableStateFlow(DemoData.accounts)
    private val transactionsState = MutableStateFlow(DemoData.transactions(today))
    private val recurringsState = MutableStateFlow(DemoData.recurrings(today))
    private val holdingsState = MutableStateFlow(DemoData.holdings(today))
    private val historyState = MutableStateFlow(DemoData.portfolioHistory(today))
    private val goalsState = MutableStateFlow(DemoData.goals(today))
    private val contributionsState = MutableStateFlow(DemoData.goalContributions(today))

    override val categories: Flow<List<Category>> = categoriesState.map { it.sortedWith(CategoryByName) }
    override val accounts: Flow<List<Account>> = accountsState.map { it.sortedWith(AccountByName) }
    override val transactions: Flow<List<Transaction>> = transactionsState
    override val recurrings: Flow<List<Recurring>> = recurringsState
    override val holdings: Flow<List<Holding>> = holdingsState
    override val portfolioHistory: Flow<List<PortfolioSnapshot>> = historyState
    override val goals: Flow<List<Goal>> = goalsState
    override val goalContributions: Flow<List<GoalContribution>> = contributionsState

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
        holdingsState.update { list -> list.filterNot { it.accountId == accountId } }
    }

    override suspend fun saveRecurring(recurring: Recurring) {
        val saved = if (recurring.id.isBlank()) recurring.copy(id = newRecurringId()) else recurring
        recurringsState.update { list -> list.filterNot { it.id == saved.id } + saved }
    }

    override suspend fun deleteRecurring(recurringId: String) {
        recurringsState.update { list -> list.filterNot { it.id == recurringId } }
    }

    override suspend fun saveHolding(holding: Holding) {
        val saved = if (holding.id.isBlank()) holding.copy(id = newHoldingId()) else holding
        holdingsState.update { list -> list.filterNot { it.id == saved.id } + saved }
    }

    override suspend fun deleteHolding(holdingId: String) {
        holdingsState.update { list -> list.filterNot { it.id == holdingId } }
    }

    override suspend fun recordPortfolioValue(snapshot: PortfolioSnapshot) {
        historyState.update { list ->
            (list.filterNot { it.date == snapshot.date } + snapshot).sortedBy { it.date.toEpochDay() }
        }
    }

    override suspend fun saveGoal(goal: Goal) {
        val saved = if (goal.id.isBlank()) goal.copy(id = newGoalId()) else goal
        goalsState.update { list -> list.filterNot { it.id == saved.id } + saved }
    }

    override suspend fun deleteGoal(goalId: String) {
        goalsState.update { list -> list.filterNot { it.id == goalId } }
        contributionsState.update { list -> list.filterNot { it.goalId == goalId } }
    }

    override suspend fun saveContribution(contribution: GoalContribution) {
        val saved = if (contribution.id.isBlank()) contribution.copy(id = newContributionId()) else contribution
        contributionsState.update { list ->
            (list.filterNot { it.id == saved.id } + saved).sortedWith(ContributionsNewestFirst)
        }
    }

    override suspend fun deleteContribution(contributionId: String) {
        contributionsState.update { list -> list.filterNot { it.id == contributionId } }
    }

    override suspend fun deleteAllData() {
        categoriesState.value = emptyList()
        accountsState.value = emptyList()
        transactionsState.value = emptyList()
        recurringsState.value = emptyList()
        holdingsState.value = emptyList()
        historyState.value = emptyList()
        goalsState.value = emptyList()
        contributionsState.value = emptyList()
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
        // Остаток не важен: стоимость счёта считается по позициям.
        Account("brokerage", "Brokerage", "Demo Invest", AccountType.Investment, Money.Zero),
    )

    /** Ежемесячные счета, еженедельная доставка овощей и годовая подписка, которая спишется через два месяца. */
    fun recurrings(today: LocalDate) = listOf(
        Recurring("r-rent", "Rent", "🏠", Money.of(1200.0), RecurringSchedule.Monthly(1), categoryId = "rent"),
        Recurring("r-music", "Music streaming", "🎧", Money.of(10.99), RecurringSchedule.Monthly(3), categoryId = "subscriptions"),
        Recurring("r-car", "Car payment", "🚙", Money.of(95.0), RecurringSchedule.Monthly(5), categoryId = "car"),
        Recurring("r-power", "Electricity", "⚡", Money.of(75.0), RecurringSchedule.Monthly(8), categoryId = "utilities"),
        Recurring("r-water", "Water", "🚰", Money.of(32.0), RecurringSchedule.Monthly(12), categoryId = "utilities"),
        Recurring("r-insurance", "Insurance", "☂️", Money.of(45.9), RecurringSchedule.Monthly(15), categoryId = "utilities"),
        Recurring("r-gym", "Gym", "🏋️", Money.of(39.0), RecurringSchedule.Monthly(20), categoryId = "subscriptions"),
        Recurring("r-veggies", "Veggie box", "🥕", Money.of(18.5), RecurringSchedule.Weekly(DayOfWeek.SATURDAY), categoryId = "groceries"),
        Recurring(
            "r-cloud", "Cloud storage", "☁️", Money.of(99.99),
            RecurringSchedule.Yearly(today.plusMonths(2).month, 20), categoryId = "subscriptions",
        ),
    )

    fun holdings(today: LocalDate) = listOf(
        Holding("h-vwce", "brokerage", "VWCE", "Vanguard FTSE All-World", AssetClass.Fund, 42 * QUANTITY_SCALE, Money.of(98.10), Money.of(121.35), today),
        Holding("h-aapl", "brokerage", "AAPL", "Apple", AssetClass.Stock, 8 * QUANTITY_SCALE, Money.of(152.40), Money.of(198.20), today),
        Holding("h-btc", "brokerage", "BTC", "Bitcoin", AssetClass.Crypto, QUANTITY_SCALE / 20, Money.of(38_500.0), Money.of(54_200.0), today),
        Holding("h-aggh", "brokerage", "AGGH", "Global Aggregate Bond", AssetClass.Bond, 60 * QUANTITY_SCALE, Money.of(5.10), Money.of(4.92), today),
        Holding("h-eur", "brokerage", "EUR", "Cash", AssetClass.Cash, 850 * QUANTITY_SCALE, Money.of(1.0), Money.of(1.0), today),
    )

    /**
     * Четыре месяца дневных снимков, заканчиваются сегодняшней стоимостью. Больше не берём: демо-данные
     * заливаются в Firestore одной пачкой, а у неё предел 500 записей.
     */
    fun portfolioHistory(today: LocalDate): List<PortfolioSnapshot> {
        val random = Random(today.year * 1000 + today.dayOfYear)
        // Double здесь только для правдоподобного случайного блуждания; в снимок идут целые центы.
        var value = holdings(today).sumOf { it.value.minor }.toDouble()
        val points = ArrayList<PortfolioSnapshot>()
        for (daysAgo in 0..120) {
            points += PortfolioSnapshot(today.minusDays(daysAgo.toLong()), Money(value.roundToLong()))
            // Шагаем назад во времени: дневной рост от −1,1 % до +1,3 %, так что в среднем портфель рос.
            value /= 1 + (random.nextDouble() * 0.024 - 0.011)
        }
        return points.asReversed()
    }

    /** Цели на все случаи: без срока, по плану, с отставанием и уже достигнутая. */
    fun goals(today: LocalDate) = listOf(
        Goal("g-emergency", "Emergency fund", "🛟", CategoryTone.Teal, Money.of(10_000.0), targetDate = null, startDate = today.minusMonths(8)),
        Goal("g-japan", "Trip to Japan", "🗾", CategoryTone.Pink, Money.of(4_000.0), targetDate = today.plusMonths(9), startDate = today.minusMonths(5)),
        Goal("g-laptop", "New laptop", "💻", CategoryTone.Blue, Money.of(2_400.0), targetDate = today.plusMonths(3), startDate = today.minusMonths(6)),
        Goal("g-concert", "Concert tickets", "🎟️", CategoryTone.Purple, Money.of(180.0), targetDate = today.minusDays(20), startDate = today.minusMonths(3)),
    )

    fun goalContributions(today: LocalDate): List<GoalContribution> {
        val result = mutableListOf<GoalContribution>()

        fun monthly(goalId: String, months: Int, amount: Double, day: Int) {
            for (monthsAgo in months - 1 downTo 0) {
                val month = today.minusMonths(monthsAgo.toLong())
                val date = month.withDayOfMonth(minOf(day, month.lengthOfMonth()))
                // В этом месяце день взноса мог ещё не наступить — тогда взнос сегодняшний.
                result += GoalContribution("gc-$goalId-$monthsAgo", goalId, Money.of(amount), if (date.isAfter(today)) today else date)
            }
        }

        monthly("g-emergency", months = 8, amount = 500.0, day = 2)
        monthly("g-japan", months = 5, amount = 320.0, day = 5)
        monthly("g-laptop", months = 4, amount = 150.0, day = 10)
        result += GoalContribution("gc-g-concert-2", "g-concert", Money.of(100.0), today.minusMonths(2))
        result += GoalContribution("gc-g-concert-1", "g-concert", Money.of(80.0), today.minusMonths(1))
        return result.sortedWith(ContributionsNewestFirst)
    }

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
            if (date.dayOfWeek == DayOfWeek.SATURDAY) add(date, "Veggie box", -18.5, "groceries")
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
