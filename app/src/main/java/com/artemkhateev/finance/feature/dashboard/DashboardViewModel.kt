package com.artemkhateev.finance.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artemkhateev.finance.data.DeviceSettings
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.data.transactionsIncluding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ключ выбранного периода в настройках устройства. */
private const val PERIOD_KEY = "dashboard.period"

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: FinanceRepository,
    private val settings: DeviceSettings,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val period = settings.string(PERIOD_KEY).map { TransactionPeriod.fromKey(it) }.distinctUntilChanged()

    /** Транзакции с начала текущего периода: длинная история грузится, только пока такой период выбран. */
    private val periodTransactions = period.flatMapLatest { chosen ->
        val day = today()
        repository.transactionsIncluding(chosen.calendar(day).start, day).map { chosen to it }
    }

    // Состояние считается не на главном потоке: иначе расчёт тормозит переключение вкладок.
    val state: StateFlow<DashboardUiState?> =
        combine(repository.categories, periodTransactions, repository.recurrings) { categories, (chosen, transactions), recurrings ->
            buildDashboard(today(), categories, transactions, recurrings, chosen)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Период запоминается на устройстве и переживает перезапуск. */
    fun setPeriod(period: TransactionPeriod) {
        settings.putString(PERIOD_KEY, period.name)
    }

    fun markReviewed(transactionIds: List<String>) {
        viewModelScope.launch { repository.markReviewed(transactionIds) }
    }
}
