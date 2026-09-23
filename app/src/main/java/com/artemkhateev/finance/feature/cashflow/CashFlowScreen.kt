package com.artemkhateev.finance.feature.cashflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.data.DeviceSettings
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.data.transactionsIncluding
import com.artemkhateev.finance.ui.components.BarChart
import com.artemkhateev.finance.ui.components.ChartBar
import com.artemkhateev.finance.ui.components.ChartPart
import com.artemkhateev.finance.ui.components.DeltaBadge
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.ui.components.appendMoney
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.color
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
import java.time.LocalDate

/** Ключ выбранного периода в настройках устройства. */
private const val PERIOD_KEY = "cashflow.period"

@OptIn(ExperimentalCoroutinesApi::class)
class CashFlowViewModel(
    private val repository: FinanceRepository,
    private val settings: DeviceSettings,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val period = settings.string(PERIOD_KEY).map { TransactionPeriod.fromKey(it) }.distinctUntilChanged()

    /** Нужны и сам период, и такой же перед ним: длинная история грузится, только пока такой период выбран. */
    private val periodTransactions = period.flatMapLatest { chosen ->
        val day = today()
        repository.transactionsIncluding(chosen.previous(day).start, day).map { chosen to it }
    }

    /** null — данные ещё не пришли. */
    val state: StateFlow<CashFlowUiState?> =
        combine(periodTransactions, repository.categories) { (chosen, transactions), categories ->
            buildCashFlow(today(), chosen, transactions, categories)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Период запоминается на устройстве и переживает перезапуск. */
    fun setPeriod(period: TransactionPeriod) {
        settings.putString(PERIOD_KEY, period.name)
    }
}

@Composable
fun CashFlowScreen(
    viewModel: CashFlowViewModel = viewModel { CashFlowViewModel(AppGraph.repository, AppGraph.settings) },
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val state = loaded ?: return
    val colors = FinanceTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") {
            SegmentedControl(
                options = TransactionPeriod.entries.map { it.label },
                selectedIndex = state.period.ordinal,
                onSelect = { viewModel.setPeriod(TransactionPeriod.entries[it]) },
                fill = true,
            )
        }
        item(key = "net") {
            val netColor = if (state.net.current.minor >= 0) colors.positiveText else colors.negativeText
            FlowCard(
                title = "Net income",
                state = state,
                comparison = state.net,
                goodWhenUp = true,
                amountColor = netColor,
                bars = state.netBars.map { bar ->
                    ChartBar(bar.segments.map { ChartPart(it.value, if (it.value >= 0) colors.positive else colors.negative) })
                },
            )
        }
        item(key = "spend") {
            FlowCard(
                title = "Spend",
                state = state,
                comparison = state.spend,
                goodWhenUp = false,
                amountColor = colors.textPrimary,
                bars = state.spendBars.map { bar ->
                    ChartBar(bar.segments.map { ChartPart(it.value, it.tone?.color() ?: colors.textSecondary) })
                },
            )
        }
        item(key = "income") {
            FlowCard(
                title = "Income",
                state = state,
                comparison = state.income,
                goodWhenUp = true,
                amountColor = colors.positiveText,
                bars = state.incomeBars.map { bar -> ChartBar(bar.segments.map { ChartPart(it.value, colors.positive) }) },
            )
        }
    }
}

@Composable
private fun FlowCard(
    title: String,
    state: CashFlowUiState,
    comparison: PeriodComparison,
    goodWhenUp: Boolean,
    amountColor: Color,
    bars: List<ChartBar>,
) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    FinanceCard(
        hero = true,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Text(title, style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(state.currentPeriod, style = typography.bodySecondary, color = colors.textSecondary)
        Spacer(Modifier.height(2.dp))
        MoneyText(comparison.current, style = typography.heroAmount, color = amountColor, sign = SignStyle.Negative)
        comparison.changePercent?.let { percent ->
            Spacer(Modifier.height(6.dp))
            DeltaBadge(percent, goodWhenUp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildAnnotatedString {
                append("vs ")
                withStyle(SpanStyle(color = amountColor)) {
                    appendMoney(comparison.previous, typography.bodySecondary.fontSize, sign = SignStyle.Negative)
                }
                append(" in ${state.previousPeriod}")
            },
            style = typography.bodySecondary,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        // За один день столбиков нет: хватает сравнения сумм.
        if (bars.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            BarChart(
                bars = bars,
                firstLabel = state.firstLabel,
                lastLabel = state.lastLabel,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
        }
    }
}
