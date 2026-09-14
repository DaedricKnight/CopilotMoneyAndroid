package com.artemkhateev.finance.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.feature.categories.SuggestedCategory
import com.artemkhateev.finance.feature.categories.existingOrNew
import com.artemkhateev.finance.ui.format.dayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
    /** Сколько загруженных транзакций у каждой категории. */
    val categoryUsage: Map<String, Int> = emptyMap(),
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
            TransactionsUiState(
                days = buildTransactionDays(today(), transactions, categories, accounts),
                categories = categories,
                accounts = accounts,
                categoryUsage = transactions.mapNotNull { it.categoryId }.groupingBy { it }.eachCount(),
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableDraft = MutableStateFlow<TransactionDraft?>(null)

    /** Открытая форма добавления или правки; null — форма закрыта. */
    val draft: StateFlow<TransactionDraft?> = mutableDraft.asStateFlow()

    /** Транзакция в том виде, в каком её открыли на правку: от неё считается сдвиг остатков. */
    private var editing: Transaction? = null

    fun startNew() {
        editing = null
        mutableDraft.value = TransactionDraft(date = today(), accountId = state.value?.accounts?.firstOrNull()?.id)
    }

    fun startEdit(transaction: Transaction) {
        editing = transaction
        mutableDraft.value = TransactionDraft.from(transaction)
    }

    fun updateDraft(change: (TransactionDraft) -> TransactionDraft) {
        mutableDraft.update { it?.let(change) }
    }

    fun dismissDraft() {
        editing = null
        mutableDraft.value = null
    }

    fun saveDraft() {
        val transaction = mutableDraft.value?.toTransaction() ?: return
        val previous = editing
        dismissDraft()
        viewModelScope.launch { repository.saveTransaction(transaction, previous) }
    }

    fun deleteDraft() {
        val transaction = editing ?: return
        dismissDraft()
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    /** Категория из каталога, выбранная прямо в форме: заводится сразу, а её id форма получает до ответа репозитория. */
    fun createCategory(suggestion: SuggestedCategory): String {
        val existing = state.value?.categories.orEmpty()
        val category = suggestion.existingOrNew(existing)
        if (category !in existing) viewModelScope.launch { repository.saveCategory(category) }
        return category.id
    }
}
