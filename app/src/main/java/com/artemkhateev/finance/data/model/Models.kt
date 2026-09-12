package com.artemkhateev.finance.data.model

import java.time.LocalDate
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

data class Account(
    val id: String,
    val name: String,
    val institution: String,
    val type: AccountType,
    val balance: Money,
    /** Последние цифры номера, как их показывает банк. */
    val mask: String? = null,
)

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

data class Recurring(
    val id: String,
    val name: String,
    val emoji: String,
    /** Ожидаемое списание, положительное. */
    val amount: Money,
    val dayOfMonth: Int,
    val categoryId: String? = null,
)
