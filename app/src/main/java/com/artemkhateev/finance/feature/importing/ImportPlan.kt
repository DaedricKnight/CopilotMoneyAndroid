package com.artemkhateev.finance.feature.importing

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.transactionsWindowStart
import java.time.LocalDate

/** Что импорт добавит в приложение. */
data class ImportPlan(
    /** Новые транзакции, уже отмеченные просмотренными: иначе они заполнили бы «To review». */
    val transactions: List<Transaction>,
    val newCategories: List<Category>,
    val newAccounts: List<Account>,
    /** Строки, которые уже есть в приложении: их пропускаем. */
    val duplicates: Int,
    /** Транзакции старше прошлого месяца: сохранятся, но экраны их пока не показывают. */
    val olderThanWindow: Int,
    val total: Money,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
)

/** Имя счёта для строк файла без колонки счёта. */
const val UNNAMED_ACCOUNT = "Imported"

fun sourceAccountNames(rows: List<ImportRow>): List<String> = rows.map { it.account ?: UNNAMED_ACCOUNT }.distinct()

/** По умолчанию строки идут в счёт с тем же именем, а если такого нет — в новый. */
fun defaultAccountTargets(rows: List<ImportRow>, accounts: List<Account>): Map<String, String?> =
    sourceAccountNames(rows).associateWith { name -> accounts.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id }

/**
 * [accountTargets] — куда идут строки каждого счёта из файла: id счёта приложения или null — новый счёт
 * с тем же именем. Категории сопоставляются по имени без учёта регистра, недостающие создаются.
 * Строка, которая уже есть в приложении (тот же день, сумма, название и счёт), пропускается.
 */
fun planImport(
    rows: List<ImportRow>,
    categories: List<Category>,
    accounts: List<Account>,
    existing: List<Transaction>,
    accountTargets: Map<String, String?>,
    today: LocalDate,
    nowMillis: Long = System.currentTimeMillis(),
): ImportPlan {
    val newAccounts = mutableListOf<Account>()
    val accountIds = sourceAccountNames(rows).associateWith { name ->
        accountTargets[name]?.takeIf { id -> accounts.any { it.id == id } }
            ?: Account("a-$nowMillis-i${newAccounts.size}", name, "", AccountType.Checking, Money.Zero)
                .also { newAccounts += it }
                .id
    }

    val newCategories = mutableListOf<Category>()
    val usedTones = categories.mapTo(mutableSetOf()) { it.tone }
    val categoryIds = rows.mapNotNull { it.category }.distinctBy { it.lowercase() }.associate { name ->
        val id = categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: run {
            val income = rows.filter { it.category.equals(name, ignoreCase = true) }.all { it.amount.minor > 0 }
            val kind = if (income) CategoryKind.Income else CategoryKind.Expense
            val tone = ToneOrder.firstOrNull { it !in usedTones } ?: ToneOrder[newCategories.size % ToneOrder.size]
            usedTones += tone
            Category("c-$nowMillis-i${newCategories.size}", name, emojiFor(name, kind), tone, kind)
                .also { newCategories += it }
                .id
        }
        name.lowercase() to id
    }

    // Выгрузки обычно идут от новых записей к старым, а лента внутри дня сортирует по убыванию id:
    // id строятся так, чтобы порядок файла сохранился.
    val newestFirst = rows.size > 1 && !rows.first().date.isBefore(rows.last().date)
    var duplicates = 0
    val transactions = rows.mapIndexedNotNull { index, row ->
        val accountId = accountIds.getValue(row.account ?: UNNAMED_ACCOUNT)
        val alreadyThere = existing.any {
            it.date == row.date && it.amount == row.amount && it.accountId == accountId &&
                it.merchant.equals(row.merchant, ignoreCase = true)
        }
        if (alreadyThere) {
            duplicates++
            return@mapIndexedNotNull null
        }
        val order = if (newestFirst) rows.size - 1 - index else index
        Transaction(
            id = "i-$nowMillis-${order.toString().padStart(6, '0')}",
            accountId = accountId,
            merchant = row.merchant,
            amount = row.amount,
            date = row.date,
            categoryId = row.category?.let { categoryIds[it.lowercase()] },
            reviewed = true,
        )
    }

    val windowStart = transactionsWindowStart(today)
    return ImportPlan(
        transactions = transactions,
        newCategories = newCategories,
        newAccounts = newAccounts,
        duplicates = duplicates,
        olderThanWindow = transactions.count { it.date.isBefore(windowStart) },
        total = Money(transactions.sumOf { it.amount.minor }),
        firstDate = transactions.minByOrNull { it.date.toEpochDay() }?.date,
        lastDate = transactions.maxByOrNull { it.date.toEpochDay() }?.date,
    )
}

/** Цвета новых категорий: сначала те, что ещё не заняты. */
private val ToneOrder = listOf(
    CategoryTone.Green, CategoryTone.Yellow, CategoryTone.Blue, CategoryTone.Magenta, CategoryTone.Orange,
    CategoryTone.Teal, CategoryTone.Purple, CategoryTone.Pink, CategoryTone.Red, CategoryTone.Gray,
)

/** Эмодзи новой категории по словам в названии: так импортированные категории сразу узнаваемы. */
private val EmojiKeywords = listOf(
    listOf("grocer", "supermarket") to "🛒",
    listOf("taxi", "uber", "bolt") to "🚕",
    listOf("restaurant", "cafe", "dining", "food") to "🍔",
    listOf("coffee") to "☕",
    listOf("wellness", "beauty", "spa", "hair") to "💇",
    listOf("health", "pharmacy", "medic", "doctor") to "💊",
    listOf("fuel", "petrol") to "⛽",
    listOf("car", "parking", "vehicle") to "🚗",
    listOf("transport", "bus", "train", "metro", "tram") to "🚌",
    listOf("rent", "housing", "mortgage") to "🏠",
    listOf("utilit", "electric", "water", "internet", "phone") to "💡",
    listOf("cloth", "shoes", "shopping") to "👕",
    listOf("entertain", "cinema", "movie", "games", "concert") to "🎟️",
    listOf("travel", "flight", "hotel", "vacation") to "✈️",
    listOf("gift") to "🎁",
    listOf("subscription") to "💳",
    listOf("education", "book", "course") to "📚",
    listOf("sport", "gym", "fitness") to "🏋️",
    listOf("pet", "pets") to "🐶",
    listOf("kid", "kids", "child", "baby") to "👶",
    listOf("salary", "wage", "income", "bonus") to "💰",
)

private fun emojiFor(name: String, kind: CategoryKind): String {
    val words = name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
    // Короткие слова — только целиком: иначе «car» нашёлся бы в «card».
    fun matches(keyword: String) = words.any { word -> if (keyword.length >= 4) word.startsWith(keyword) else word == keyword }
    return EmojiKeywords.firstOrNull { (keywords, _) -> keywords.any(::matches) }?.second
        ?: if (kind == CategoryKind.Income) "💰" else "🏷️"
}
