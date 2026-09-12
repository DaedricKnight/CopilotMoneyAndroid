package com.artemkhateev.finance.data

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
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
    val accounts: Flow<List<Account>>

    /** Транзакции начиная с [transactionsWindowStart], новые сверху. */
    val transactions: Flow<List<Transaction>>
    val recurrings: Flow<List<Recurring>>

    suspend fun markReviewed(transactionIds: Collection<String>)
    suspend fun setCategory(transactionId: String, categoryId: String?)

    /** Пустой id — новая транзакция, id выдаст репозиторий; иначе транзакция перезаписывается. */
    suspend fun saveTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transactionId: String)

    /** Пустой id — новая категория, id выдаст репозиторий; иначе категория перезаписывается. */
    suspend fun saveCategory(category: Category)

    /** Транзакции удалённой категории остаются и показываются без категории. */
    suspend fun deleteCategory(categoryId: String)
}

/** Экраны показывают прошлый и текущий месяц: более ранние транзакции не загружаются и не вводятся. */
fun transactionsWindowStart(today: LocalDate): LocalDate = today.minusMonths(1).withDayOfMonth(1)
