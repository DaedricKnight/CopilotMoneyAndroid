package com.artemkhateev.finance.feature.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.newAccountId
import com.artemkhateev.finance.data.model.sumOfMoney
import com.artemkhateev.finance.data.model.value
import com.artemkhateev.finance.ui.components.AreaLineChart
import com.artemkhateev.finance.ui.components.EmptyState
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.RoundAddButton
import com.artemkhateev.finance.ui.components.SectionHeader
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.ui.components.appendMoney
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.MoneyFormatter
import com.artemkhateev.finance.ui.format.percentText
import com.artemkhateev.finance.ui.format.quantityText
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.TONE_BACKGROUND_ALPHA
import com.artemkhateev.finance.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

class InvestmentsViewModel(
    private val repository: FinanceRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val range = MutableStateFlow(InvestmentRange.ThreeMonths)

    /** null — данные ещё не пришли. */
    val state: StateFlow<InvestmentsUiState?> =
        combine(range, repository.accounts, repository.holdings, repository.portfolioHistory) { selected, accounts, holdings, history ->
            buildInvestments(today(), selected, accounts, holdings, history)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Счета, которые форма предлагает для позиции. */
    val investmentAccounts: StateFlow<List<Account>> =
        repository.accounts.map { list -> list.filter { it.type == AccountType.Investment } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutableDraft = MutableStateFlow<HoldingDraft?>(null)

    /** Открытая форма позиции; null — закрыта. */
    val draft: StateFlow<HoldingDraft?> = mutableDraft.asStateFlow()

    /** Позиция в том виде, в каком её открыли на правку. */
    private var editing: Holding? = null

    fun setRange(value: InvestmentRange) {
        range.value = value
    }

    fun startNew() {
        editing = null
        mutableDraft.value = HoldingDraft(accountId = investmentAccounts.value.firstOrNull()?.id)
    }

    fun startEdit(holding: Holding) {
        editing = holding
        mutableDraft.value = HoldingDraft.from(holding)
    }

    fun updateDraft(change: (HoldingDraft) -> HoldingDraft) {
        mutableDraft.update { it?.let(change) }
    }

    fun dismissDraft() {
        editing = null
        mutableDraft.value = null
    }

    fun saveDraft() {
        val draft = mutableDraft.value ?: return
        if (draft.problem() != null) return
        val previous = editing
        val others = countedHoldings().filterNot { it.id == previous?.id }
        dismissDraft()
        viewModelScope.launch {
            val holding = draft.toHolding(today(), draft.accountId ?: brokerageAccountId(), previous)
            repository.saveHolding(holding)
            recordValue(others + holding)
        }
    }

    fun deleteDraft() {
        val holding = editing ?: return
        val others = countedHoldings().filterNot { it.id == holding.id }
        dismissDraft()
        viewModelScope.launch {
            repository.deleteHolding(holding.id)
            recordValue(others)
        }
    }

    private fun countedHoldings(): List<Holding> = state.value?.holdings?.map { it.holding }.orEmpty()

    /** Позиции живут в инвестиционном счёте: без счёта их стоимость не попала бы в чистый капитал. */
    private suspend fun brokerageAccountId(): String {
        val accounts = repository.accounts.first()
        accounts.firstOrNull { it.type == AccountType.Investment }?.let { return it.id }
        val account = Account(newAccountId(), freeAccountName("Brokerage", accounts), "", AccountType.Investment, Money.Zero)
        repository.saveAccount(account)
        return account.id
    }

    /** Снимок за сегодня: из таких снимков складывается график. */
    private suspend fun recordValue(holdings: List<Holding>) {
        repository.recordPortfolioValue(PortfolioSnapshot(today(), holdings.sumOfMoney { it.value }))
    }
}

@Composable
fun InvestmentsScreen(
    viewModel: InvestmentsViewModel = viewModel { InvestmentsViewModel(AppGraph.repository) },
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val accounts by viewModel.investmentAccounts.collectAsStateWithLifecycle()
    val state = loaded ?: return
    val colors = FinanceTheme.colors

    Box(Modifier.fillMaxSize()) {
        if (state.holdings.isEmpty()) {
            EmptyState("No investments yet", "Tap + to add a stock, a fund or crypto and track its value.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Запас снизу, чтобы последнюю строку не закрывала кнопка «+».
                contentPadding = screenContentPadding(extraBottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "summary") { SummaryCard(state, onRange = viewModel::setRange) }
                item(key = "allocation-header") { SectionHeader("Allocation") }
                item(key = "allocation") { AllocationCard(state.allocation) }
                item(key = "holdings-header") { SectionHeader("Holdings") }
                item(key = "holdings") {
                    FinanceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        state.holdings.forEachIndexed { index, row ->
                            if (index > 0) {
                                HorizontalDivider(color = colors.border, modifier = Modifier.padding(start = 66.dp))
                            }
                            HoldingRow(row, onClick = { viewModel.startEdit(row.holding) })
                        }
                    }
                }
            }
        }
        RoundAddButton(
            contentDescription = "Add holding",
            onClick = viewModel::startNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    draft?.let { current ->
        HoldingEditorSheet(
            draft = current,
            accounts = accounts,
            onChange = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteDraft,
            onDismiss = viewModel::dismissDraft,
        )
    }
}

@Composable
private fun SummaryCard(state: InvestmentsUiState, onRange: (InvestmentRange) -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    FinanceCard(
        hero = true,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text("Investments", style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        MoneyText(state.value, style = typography.heroAmount)
        ChangeLine(state.rangeChange, state.rangeChangeTenths, suffix = " since ${state.firstLabel}")
        Spacer(Modifier.height(14.dp))
        AreaLineChart(
            values = state.history,
            color = colors.accent,
            firstLabel = state.firstLabel,
            lastLabel = state.lastLabel,
            modifier = Modifier.fillMaxWidth().height(130.dp),
        )
        Spacer(Modifier.height(14.dp))
        SegmentedControl(
            options = InvestmentRange.entries.map { it.label },
            selectedIndex = state.range.ordinal,
            onSelect = { onRange(InvestmentRange.entries[it]) },
        )
        Spacer(Modifier.height(12.dp))
        ChangeLine(state.gain, state.gainTenths, prefix = "All time ")
    }
}

/** «↑ €123.45 (1.2%)» в цвете роста или падения; направление показывает стрелка. */
@Composable
private fun ChangeLine(change: Money, tenths: Int?, prefix: String = "", suffix: String = "") {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val tone = when {
        change.minor > 0 -> colors.positiveText
        change.minor < 0 -> colors.negativeText
        else -> colors.textSecondary
    }
    Text(
        text = buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(color = tone)) {
                append(
                    when {
                        change.minor > 0 -> "↑ "
                        change.minor < 0 -> "↓ "
                        else -> ""
                    },
                )
                appendMoney(change.abs(), typography.bodySecondary.fontSize)
                if (tenths != null) append(" (${percentText(abs(tenths), signed = false)})")
            }
            append(suffix)
        },
        style = typography.bodySecondary,
        color = colors.textSecondary,
    )
}

@Composable
private fun AllocationCard(allocation: List<AllocationSliceUi>) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    FinanceCard {
        Row(
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            allocation.forEach { slice ->
                Box(
                    Modifier
                        .weight(slice.share.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(slice.assetClass.color()),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        allocation.forEach { slice ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(slice.assetClass.color()))
                Spacer(Modifier.width(10.dp))
                Text(slice.assetClass.label(), style = typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    text = percentText((slice.share * 1000).roundToInt(), signed = false),
                    style = typography.bodySecondary,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.width(14.dp))
                MoneyText(slice.value, cents = false)
            }
        }
    }
}

@Composable
private fun HoldingRow(row: HoldingRowUi, onClick: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val holding = row.holding
    val tone = holding.assetClass.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = TONE_BACKGROUND_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Text(holding.symbol.take(4), style = typography.chip, color = tone, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = holding.name,
                style = typography.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${quantityText(holding.quantityMicros)} × ${MoneyFormatter.format(holding.price)}",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(row.value)
            row.gainTenths?.let { tenths ->
                Text(
                    text = percentText(tenths),
                    style = typography.caption,
                    color = when {
                        row.gain.minor > 0 -> colors.positiveText
                        row.gain.minor < 0 -> colors.negativeText
                        else -> colors.textSecondary
                    },
                )
            }
        }
    }
}

internal fun AssetClass.label(): String = when (this) {
    AssetClass.Stock -> "Stocks"
    AssetClass.Fund -> "Funds & ETFs"
    AssetClass.Crypto -> "Crypto"
    AssetClass.Bond -> "Bonds"
    AssetClass.Cash -> "Cash"
    AssetClass.Other -> "Other"
}

internal fun AssetClass.pillLabel(): String = when (this) {
    AssetClass.Stock -> "Stock"
    AssetClass.Fund -> "Fund / ETF"
    AssetClass.Crypto -> "Crypto"
    AssetClass.Bond -> "Bond"
    AssetClass.Cash -> "Cash"
    AssetClass.Other -> "Other"
}

/** Цвета классов активов берутся из палитры категорий, чтобы экраны оставались в одной гамме. */
internal fun AssetClass.color(): Color = when (this) {
    AssetClass.Stock -> CategoryTone.Blue.color()
    AssetClass.Fund -> CategoryTone.Teal.color()
    AssetClass.Crypto -> CategoryTone.Yellow.color()
    AssetClass.Bond -> CategoryTone.Purple.color()
    AssetClass.Cash -> CategoryTone.Green.color()
    AssetClass.Other -> CategoryTone.Gray.color()
}
