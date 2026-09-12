package com.artemkhateev.finance.data

import com.artemkhateev.finance.data.demo.DemoFinanceRepository
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Источник данных приложения. Сейчас за ним демо-данные в памяти; облачная
 * реализация (Firestore) встанет на это же место, экраны не изменятся.
 */
interface FinanceRepository {
    val categories: Flow<List<Category>>
    val accounts: Flow<List<Account>>
    val transactions: Flow<List<Transaction>>
    val recurrings: Flow<List<Recurring>>

    suspend fun markReviewed(transactionIds: Collection<String>)
    suspend fun setCategory(transactionId: String, categoryId: String?)
}

/** Единственное место, где выбирается реализация репозитория. */
object AppGraph {
    val repository: FinanceRepository by lazy { DemoFinanceRepository() }
}
