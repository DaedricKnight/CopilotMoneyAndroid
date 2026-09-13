package com.artemkhateev.finance.feature.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface ImportUiState {
    data object Loading : ImportUiState
    data object NeedsSignIn : ImportUiState
    data class Failed(val message: String) : ImportUiState
    data class Ready(
        val plan: ImportPlan,
        /** Счета из файла и куда пойдут их строки. */
        val sources: List<SourceAccountUi>,
        /** Счета приложения, в которые можно направить строки. */
        val accounts: List<Account>,
        /** Категории приложения вместе с новыми — для подписей в превью. */
        val categories: List<Category>,
        val unreadable: Int,
    ) : ImportUiState
    data class Importing(val count: Int) : ImportUiState
    data class Done(val count: Int) : ImportUiState
}

/** Счёт из файла; [targetAccountId] null — строки пойдут в новый счёт с этим именем. */
data class SourceAccountUi(val name: String, val targetAccountId: String?)

class ImportViewModel(
    private val repository: FinanceRepository,
    private val signedIn: () -> Boolean,
    private val load: suspend () -> String?,
    private val today: () -> LocalDate = { LocalDate.now() },
    // Запись доводится до конца, даже если экран импорта закрыли.
    private val writeScope: CoroutineScope = AppGraph.appScope,
) : ViewModel() {

    private val mutableState = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val state: StateFlow<ImportUiState> = mutableState.asStateFlow()

    private val nowMillis = System.currentTimeMillis()
    private var rows: List<ImportRow> = emptyList()
    private var unreadable = 0
    private var categories: List<Category> = emptyList()
    private var accounts: List<Account> = emptyList()
    private var existing: List<Transaction> = emptyList()
    private var targets: Map<String, String?> = emptyMap()

    init {
        viewModelScope.launch {
            if (!signedIn()) {
                mutableState.value = ImportUiState.NeedsSignIn
                return@launch
            }
            val text = runCatching { load() }.getOrNull()
            if (text.isNullOrBlank()) {
                mutableState.value = ImportUiState.Failed("The file is empty or can't be opened")
                return@launch
            }
            when (val parsed = withContext(Dispatchers.Default) { parseRecords(text) }) {
                is ParsedFile.Failure -> mutableState.value = ImportUiState.Failed(parsed.message)
                is ParsedFile.Rows -> {
                    rows = parsed.rows
                    unreadable = parsed.unreadable
                    categories = repository.categories.first()
                    accounts = repository.accounts.first()
                    existing = repository.transactions.first()
                    targets = defaultAccountTargets(rows, accounts)
                    publish()
                }
            }
        }
    }

    fun chooseAccount(source: String, accountId: String?) {
        if (mutableState.value !is ImportUiState.Ready) return
        targets = targets + (source to accountId)
        publish()
    }

    fun confirm() {
        val plan = (mutableState.value as? ImportUiState.Ready)?.plan ?: return
        if (plan.transactions.isEmpty()) return
        mutableState.value = ImportUiState.Importing(plan.transactions.size)
        writeScope.launch {
            val result = runCatching {
                plan.newCategories.forEach { repository.saveCategory(it) }
                plan.newAccounts.forEach { repository.saveAccount(it) }
                repository.importTransactions(plan.transactions)
            }
            mutableState.value = result.fold(
                onSuccess = { ImportUiState.Done(plan.transactions.size) },
                onFailure = { ImportUiState.Failed("Couldn't import: ${it.message}") },
            )
        }
    }

    private fun publish() {
        val plan = planImport(rows, categories, accounts, existing, targets, today(), nowMillis)
        mutableState.value = ImportUiState.Ready(
            plan = plan,
            sources = sourceAccountNames(rows).map { SourceAccountUi(it, targets[it]) },
            accounts = accounts,
            categories = categories + plan.newCategories,
            unreadable = unreadable,
        )
    }
}
