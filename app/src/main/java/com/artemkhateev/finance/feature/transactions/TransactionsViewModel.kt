package com.artemkhateev.finance.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artemkhateev.finance.data.DeviceSettings
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.data.transactionsIncluding
import com.artemkhateev.finance.feature.categories.SuggestedCategory
import com.artemkhateev.finance.feature.categories.existingOrNew
import com.artemkhateev.finance.ui.format.dayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ключ выбранного периода в настройках устройства. */
private const val PERIOD_KEY = "transactions.period"

data class TransactionRowUi(val transaction: Transaction, val category: Category?, val accountName: String)

data class TransactionDayUi(val date: LocalDate, val label: String, val rows: List<TransactionRowUi>)

data class TransactionsUiState(
    val period: TransactionPeriod,
    /** Потрачено за период: доходы в сумму не входят. */
    val spent: Money,
    /** Сколько транзакций попало в период. */
    val count: Int,
    val days: List<TransactionDayUi>,
    val categories: List<Category>,
    val accounts: List<Account>,
    /** Сколько транзакций периода у каждой категории. */
    val categoryUsage: Map<String, Int> = emptyMap(),
)

fun buildTransactions(
    today: LocalDate,
    period: TransactionPeriod,
    transactions: List<Transaction>,
    categories: List<Category>,
    accounts: List<Account>,
): TransactionsUiState {
    val start = period.start(today)
    val inPeriod = transactions.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
    return TransactionsUiState(
        period = period,
        spent = Money(-inPeriod.filter { it.amount.minor < 0 }.sumOf { it.amount.minor }),
        count = inPeriod.size,
        days = buildTransactionDays(today, inPeriod, categories, accounts),
        categories = categories,
        accounts = accounts,
        categoryUsage = inPeriod.mapNotNull { it.categoryId }.groupingBy { it }.eachCount(),
    )
}

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

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val repository: FinanceRepository,
    private val settings: DeviceSettings,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val period = settings.string(PERIOD_KEY).map { TransactionPeriod.fromKey(it) }.distinctUntilChanged()

    /** Транзакции выбранного периода: длинная история грузится, только пока такой период выбран. */
    private val periodTransactions = period.flatMapLatest { chosen ->
        val day = today()
        repository.transactionsIncluding(chosen.start(day), day).map { chosen to it }
    }

    /** null — данные ещё не пришли. */
    val state: StateFlow<TransactionsUiState?> =
        combine(periodTransactions, repository.categories, repository.accounts) { (chosen, transactions), categories, accounts ->
            buildTransactions(today(), chosen, transactions, categories, accounts)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mutableDraft = MutableStateFlow<TransactionDraft?>(null)

    /** Открытая форма добавления или правки; null — форма закрыта. */
    val draft: StateFlow<TransactionDraft?> = mutableDraft.asStateFlow()

    /** Транзакция в том виде, в каком её открыли на правку: от неё считается сдвиг остатков. */
    private var editing: Transaction? = null

    /** Период запоминается на устройстве и переживает перезапуск. */
    fun setPeriod(period: TransactionPeriod) {
        settings.putString(PERIOD_KEY, period.name)
    }

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
