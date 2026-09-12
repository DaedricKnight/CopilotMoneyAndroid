package com.artemkhateev.finance.data

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Источник данных приложения: демо-данные в памяти или Firestore текущего пользователя.
 * Экраны не знают, какая реализация за ним стоит.
 */
interface FinanceRepository {
    val categories: Flow<List<Category>>
    val accounts: Flow<List<Account>>
    val transactions: Flow<List<Transaction>>
    val recurrings: Flow<List<Recurring>>

    suspend fun markReviewed(transactionIds: Collection<String>)
    suspend fun setCategory(transactionId: String, categoryId: String?)
}
