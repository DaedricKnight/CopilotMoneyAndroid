package com.artemkhateev.finance.data

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Источник данных приложения: демо-данные в памяти или Firestore текущего пользователя.
 * Экраны не знают, какая реализация за ним стоит.
 */
interface FinanceRepository {
    /** Категории по имени. */
    val categories: Flow<List<Category>>

    /** Счета по имени. */
    val accounts: Flow<List<Account>>

    /** Транзакции начиная с [transactionsWindowStart], новые сверху. */
    val transactions: Flow<List<Transaction>>

    /** Транзакции за последний год, новые сверху: лента подписывается на них, когда период длиннее окна. */
    val transactionsYear: Flow<List<Transaction>>
    val recurrings: Flow<List<Recurring>>

    val holdings: Flow<List<Holding>>

    /** Снимки стоимости портфеля за последний год, от старых к новым. */
    val portfolioHistory: Flow<List<PortfolioSnapshot>>

    val goals: Flow<List<Goal>>

    /** Взносы во все цели за всё время, новые сверху. */
    val goalContributions: Flow<List<GoalContribution>>

    suspend fun markReviewed(transactionIds: Collection<String>)
    suspend fun setCategory(transactionId: String, categoryId: String?)

    /**
     * Пустой id — новая транзакция, id выдаст репозиторий; иначе транзакция перезаписывается.
     * Остатки счетов сдвигаются на разницу с [previous] — прежней версией правленой транзакции.
     */
    suspend fun saveTransaction(transaction: Transaction, previous: Transaction? = null)

    /** Удаляет транзакцию и возвращает её сумму остатку счёта. */
    suspend fun deleteTransaction(transaction: Transaction)

    /** Пустой id — новая категория, id выдаст репозиторий; иначе категория перезаписывается. */
    suspend fun saveCategory(category: Category)

    /** Транзакции удалённой категории остаются и показываются без категории. */
    suspend fun deleteCategory(categoryId: String)

    /** Пустой id — новый счёт, id выдаст репозиторий; иначе счёт перезаписывается. */
    suspend fun saveAccount(account: Account)

    /**
     * Удаляет счёт вместе с его позициями. Транзакции счёта остаются, но на остатки
     * и чистый капитал больше не влияют.
     */
    suspend fun deleteAccount(accountId: String)

    /** Пустой id — новый регулярный платёж, id выдаст репозиторий; иначе платёж перезаписывается. */
    suspend fun saveRecurring(recurring: Recurring)

    /** Транзакции, которыми платёж уже вносили, остаются. */
    suspend fun deleteRecurring(recurringId: String)

    /** Пустой id — новая позиция, id выдаст репозиторий; иначе позиция перезаписывается. */
    suspend fun saveHolding(holding: Holding)
    suspend fun deleteHolding(holdingId: String)

    /** Записывает стоимость портфеля за день; второй снимок за тот же день заменяет первый. */
    suspend fun recordPortfolioValue(snapshot: PortfolioSnapshot)

    /** Пустой id — новая цель, id выдаст репозиторий; иначе цель перезаписывается. */
    suspend fun saveGoal(goal: Goal)

    /** Удаляет цель вместе с её взносами. */
    suspend fun deleteGoal(goalId: String)

    /** Пустой id — новый взнос, id выдаст репозиторий; иначе взнос перезаписывается. */
    suspend fun saveContribution(contribution: GoalContribution)
    suspend fun deleteContribution(contributionId: String)

    /** Удаляет всё: транзакции, счета, категории, регулярные платежи, позиции с историей и цели. */
    suspend fun deleteAllData()

    /**
     * Записывает готовые транзакции как есть — например, из импорта. Остатки счетов не сдвигаются:
     * в выгрузке из другого приложения эти траты уже учтены в его балансе.
     */
    suspend fun importTransactions(transactions: List<Transaction>)
}

/** Экраны показывают прошлый и текущий месяц: более ранние транзакции не загружаются и не вводятся. */
fun transactionsWindowStart(today: LocalDate): LocalDate = today.minusMonths(1).withDayOfMonth(1)

/**
 * Как сохранение или удаление транзакции сдвигает остатки: id счёта → сдвиг в центах.
 * [saved] — новая версия (null при удалении), [previous] — прежняя (null для новой транзакции).
 */
fun balanceChanges(saved: Transaction?, previous: Transaction?): Map<String, Long> {
    val changes = mutableMapOf<String, Long>()
    previous?.let { changes[it.accountId] = (changes[it.accountId] ?: 0L) - it.amount.minor }
    saved?.let { changes[it.accountId] = (changes[it.accountId] ?: 0L) + it.amount.minor }
    return changes.filterValues { it != 0L }
}
