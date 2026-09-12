package com.artemkhateev.finance.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.ui.format.dayLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TransactionRowUi(val transaction: Transaction, val category: Category?, val accountName: String)

data class TransactionDayUi(val date: LocalDate, val label: String, val rows: List<TransactionRowUi>)

fun buildTransactionDays(
    today: LocalDate,
    transactions: List<Transaction>,
    categories: List<Category>,
    accounts: List<Account>,
): List<TransactionDayUi> {
    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    return transactions
        .groupBy { it.date }
        .toSortedMap(compareByDescending<LocalDate> { it })
        .map { (date, list) ->
            TransactionDayUi(
                date = date,
                label = dayLabel(date, today),
                rows = list.map {
                    TransactionRowUi(it, categoryById[it.categoryId], accountById[it.accountId]?.name.orEmpty())
                },
            )
        }
}

class TransactionsViewModel(
    repository: FinanceRepository,
    today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val days: StateFlow<List<TransactionDayUi>> =
        combine(repository.transactions, repository.categories, repository.accounts) { transactions, categories, accounts ->
            buildTransactionDays(today(), transactions, categories, accounts)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
