package com.artemkhateev.finance.data.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Сумма в минимальных единицах валюты (центах). Double для денег не годится:
 * сумма сотен транзакций накапливает ошибку округления.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(minor + other.minor)
    operator fun minus(other: Money) = Money(minor - other.minor)
    operator fun unaryMinus() = Money(-minor)
    override fun compareTo(other: Money) = minor.compareTo(other.minor)
    fun abs() = Money(kotlin.math.abs(minor))

    companion object {
        val Zero = Money(0)
        fun of(major: Double) = Money((major * 100).roundToLong())
    }
}

inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money =
    Money(sumOf { selector(it).minor })

/** Цвет категории — ключ палитры, а не hex: палитру можно перекрасить, не трогая данные. */
enum class CategoryTone { Orange, Yellow, Green, Teal, Blue, Purple, Magenta, Pink, Red, Gray }

enum class CategoryKind { Expense, Income }

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val tone: CategoryTone,
    val kind: CategoryKind = CategoryKind.Expense,
    /** Месячный бюджет; null — категория без бюджета. */
    val monthlyBudget: Money? = null,
)

/** Категории показываются по имени: своего порядка пользователь пока задать не может. */
val CategoryByName: Comparator<Category> = compareBy<Category, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

fun newCategoryId(nowMillis: Long = System.currentTimeMillis()): String =
    "c-$nowMillis-${UUID.randomUUID().toString().take(8)}"

enum class AccountType { Checking, Savings, CreditCard, Investment }

/**
 * Остаток со знаком: у активов положительный, долг по кредитной карте — отрицательный.
 * Ручные транзакции сдвигают его сами, см. [balanceChanges][com.artemkhateev.finance.data.balanceChanges].
 * Стоимость счёта с позициями считается по позициям, а не по этому полю.
 */
data class Account(
    val id: String,
    val name: String,
    val institution: String,
    val type: AccountType,
    val balance: Money,
    /** Последние цифры номера, как их показывает банк. */
    val mask: String? = null,
)

val AccountByName: Comparator<Account> = compareBy<Account, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

fun newAccountId(nowMillis: Long = System.currentTimeMillis()): String =
    "a-$nowMillis-${UUID.randomUUID().toString().take(8)}"

/**
 * Знак суммы: расход отрицательный, поступление положительное.
 * В списках расход показывается без минуса, доход — зелёным с плюсом.
 */
data class Transaction(
    val id: String,
    val accountId: String,
    val merchant: String,
    val amount: Money,
    val date: LocalDate,
    val categoryId: String?,
    val note: String? = null,
    /** Новые транзакции ждут просмотра в блоке «To review» на дашборде. */
    val reviewed: Boolean = false,
)

/** Порядок лент: новые даты сверху, внутри дня — по убыванию id. */
val NewestFirst: Comparator<Transaction> =
    compareByDescending<Transaction> { it.date.toEpochDay() }.thenByDescending { it.id }

/** Id транзакции, введённой вручную: время создания в начале, поэтому внутри дня новые идут первыми. */
fun newTransactionId(nowMillis: Long = System.currentTimeMillis()): String =
    "m-$nowMillis-${UUID.randomUUID().toString().take(8)}"

enum class RecurringFrequency { Weekly, Monthly, Yearly }

/** Когда списывается регулярный платёж. Число после конца короткого месяца — его последний день. */
sealed interface RecurringSchedule {
    val frequency: RecurringFrequency

    data class Weekly(val dayOfWeek: DayOfWeek) : RecurringSchedule {
        override val frequency: RecurringFrequency get() = RecurringFrequency.Weekly
    }

    /** [dayOfMonth] — от 1 до 31. */
    data class Monthly(val dayOfMonth: Int) : RecurringSchedule {
        override val frequency: RecurringFrequency get() = RecurringFrequency.Monthly
    }

    /** 29 февраля в невисокосный год приходится на 28-е. */
    data class Yearly(val month: Month, val dayOfMonth: Int) : RecurringSchedule {
        override val frequency: RecurringFrequency get() = RecurringFrequency.Yearly
    }
}

data class Recurring(
    val id: String,
    val name: String,
    val emoji: String,
    /** Ожидаемое списание, положительное. */
    val amount: Money,
    val schedule: RecurringSchedule,
    val categoryId: String? = null,
)

fun newRecurringId(nowMillis: Long = System.currentTimeMillis()): String =
    "r-$nowMillis-${UUID.randomUUID().toString().take(8)}"

/** Даты списаний с [start] по [end] включительно, по возрастанию. */
fun RecurringSchedule.dueDates(start: LocalDate, end: LocalDate): List<LocalDate> {
    if (end.isBefore(start)) return emptyList()
    val candidates = when (this) {
        is RecurringSchedule.Weekly ->
            generateSequence(start.with(TemporalAdjusters.nextOrSame(dayOfWeek))) { it.plusWeeks(1) }
                .takeWhile { !it.isAfter(end) }
                .toList()
        is RecurringSchedule.Monthly ->
            generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(YearMonth.from(end)) }
                .map { it.atDay(minOf(dayOfMonth, it.lengthOfMonth())) }
                .toList()
        is RecurringSchedule.Yearly ->
            (start.year..end.year).map { year ->
                val yearMonth = YearMonth.of(year, month)
                yearMonth.atDay(minOf(dayOfMonth, yearMonth.lengthOfMonth()))
            }
    }
    return candidates.filter { !it.isBefore(start) && !it.isAfter(end) }
}

fun Recurring.dueDates(start: LocalDate, end: LocalDate): List<LocalDate> = schedule.dueDates(start, end)

/** Количество в позициях хранится в миллионных долях: у фондов и крипты бывают дробные доли. */
const val QUANTITY_DECIMALS = 6
const val QUANTITY_SCALE = 1_000_000L

enum class AssetClass { Stock, Fund, Crypto, Bond, Cash, Other }

/** Позиция в инвестиционном счёте. Котировок приложение не получает — цену обновляют вручную. */
data class Holding(
    val id: String,
    val accountId: String,
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    val quantityMicros: Long,
    /** Средняя цена покупки за единицу. */
    val costPerUnit: Money,
    /** Текущая цена за единицу. */
    val price: Money,
    /** Когда цену меняли в последний раз. */
    val priceUpdated: LocalDate,
)

/** Количество × цена за единицу с округлением до цента. */
fun holdingValue(quantityMicros: Long, unitPrice: Money): Money = Money(
    BigDecimal.valueOf(quantityMicros)
        .multiply(BigDecimal.valueOf(unitPrice.minor))
        .divide(BigDecimal.valueOf(QUANTITY_SCALE), 0, RoundingMode.HALF_UP)
        .longValueExact(),
)

val Holding.value: Money get() = holdingValue(quantityMicros, price)

val Holding.cost: Money get() = holdingValue(quantityMicros, costPerUnit)

fun newHoldingId(nowMillis: Long = System.currentTimeMillis()): String =
    "h-$nowMillis-${UUID.randomUUID().toString().take(8)}"

/** Стоимость портфеля за день. Истории цен нет, поэтому её копят снимки при каждом изменении позиций. */
data class PortfolioSnapshot(val date: LocalDate, val value: Money)

/**
 * Стоимость портфеля на конец каждого дня с [start] по [today]. Сегодня — [currentValue], сумма позиций
 * сейчас; раньше — последний снимок на тот день. До первого снимка берём его же значение: как и остатки
 * счетов, до первой записи стоимость считается неизменной.
 */
fun portfolioValueByDay(
    start: LocalDate,
    today: LocalDate,
    snapshots: List<PortfolioSnapshot>,
    currentValue: Long,
): List<Long> {
    val past = snapshots.filter { it.date.isBefore(today) }.sortedBy { it.date.toEpochDay() }
    var next = 0
    var value = past.firstOrNull()?.value?.minor ?: currentValue
    val values = ArrayList<Long>()
    var day = start
    while (!day.isAfter(today)) {
        while (next < past.size && !past[next].date.isAfter(day)) {
            value = past[next].value.minor
            next++
        }
        values += if (day == today) currentValue else value
        day = day.plusDays(1)
    }
    return values
}

/** Цель накопления. Деньги на неё не переводятся: взносы только отмечают, сколько отложено. */
data class Goal(
    val id: String,
    val name: String,
    val emoji: String,
    val tone: CategoryTone,
    val target: Money,
    /** К какой дате накопить; null — без срока. */
    val targetDate: LocalDate?,
    /** С этого дня идёт план: сколько должно быть отложено к сегодняшнему дню при равномерных взносах. */
    val startDate: LocalDate,
)

fun newGoalId(nowMillis: Long = System.currentTimeMillis()): String =
    "g-$nowMillis-${UUID.randomUUID().toString().take(8)}"

/** Взнос в цель; снятие — отрицательная сумма. Отложено на цель — сумма её взносов. */
data class GoalContribution(
    val id: String,
    val goalId: String,
    val amount: Money,
    val date: LocalDate,
)

/** История взносов: новые даты сверху, внутри дня — по убыванию id. */
val ContributionsNewestFirst: Comparator<GoalContribution> =
    compareByDescending<GoalContribution> { it.date.toEpochDay() }.thenByDescending { it.id }

fun newContributionId(nowMillis: Long = System.currentTimeMillis()): String =
    "gc-$nowMillis-${UUID.randomUUID().toString().take(8)}"
