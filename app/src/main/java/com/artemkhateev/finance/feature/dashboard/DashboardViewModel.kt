package com.artemkhateev.finance.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artemkhateev.finance.data.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(
    private val repository: FinanceRepository,
    today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val state: StateFlow<DashboardUiState?> =
        combine(repository.categories, repository.transactions, repository.recurrings) { categories, transactions, recurrings ->
            buildDashboard(today(), categories, transactions, recurrings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markReviewed(transactionIds: List<String>) {
        viewModelScope.launch { repository.markReviewed(transactionIds) }
    }
}
