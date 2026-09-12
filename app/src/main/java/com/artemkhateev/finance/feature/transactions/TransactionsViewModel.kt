package com.artemkhateev.finance.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.ui.format.dayLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TransactionRowUi(val transaction: Transaction, val category: Category?, val accountName: String)

data class TransactionDayUi(val date: LocalDate, val label: String, val rows: List<TransactionRowUi>)

data class TransactionsUiState(
    val days: List<TransactionDayUi>,
    val categories: List<Category>,
    val accounts: List<Account>,
)

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
    private val repository: FinanceRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    /** null — данные ещё не пришли. */
    val state: StateFlow<TransactionsUiState?> =
        combine(repository.transactions, repository.categories, repository.accounts) { transactions, categories, accounts ->
            TransactionsUiState(buildTransactionDays(today(), transactions, categories, accounts), categories, accounts)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableDraft = MutableStateFlow<TransactionDraft?>(null)

    /** Открытая форма добавления или правки; null — форма закрыта. */
    val draft: StateFlow<TransactionDraft?> = mutableDraft.asStateFlow()

    fun startNew() {
        mutableDraft.value = TransactionDraft(date = today(), accountId = state.value?.accounts?.firstOrNull()?.id)
    }

    fun startEdit(transaction: Transaction) {
        mutableDraft.value = TransactionDraft.from(transaction)
    }

    fun updateDraft(change: (TransactionDraft) -> TransactionDraft) {
        mutableDraft.update { it?.let(change) }
    }

    fun dismissDraft() {
        mutableDraft.value = null
    }

    fun saveDraft() {
        val transaction = mutableDraft.value?.toTransaction() ?: return
        mutableDraft.value = null
        viewModelScope.launch { repository.saveTransaction(transaction) }
    }

    fun deleteDraft() {
        val id = mutableDraft.value?.id?.takeIf { it.isNotBlank() } ?: return
        mutableDraft.value = null
        viewModelScope.launch { repository.deleteTransaction(id) }
    }
}
