package com.artemkhateev.finance.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.data.FinanceRepository
import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.ui.components.AreaLineChart
import com.artemkhateev.finance.ui.components.EmptyState
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.RoundAddButton
import com.artemkhateev.finance.ui.components.appendMoney
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.TONE_BACKGROUND_ALPHA
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

class AccountsViewModel(
    private val repository: FinanceRepository,
    today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    /** null — данные ещё не пришли. */
    val state: StateFlow<AccountsUiState?> =
        combine(
            repository.accounts,
            repository.transactions,
            repository.holdings,
            repository.portfolioHistory,
        ) { accounts, transactions, holdings, history ->
            buildAccounts(today(), accounts, transactions, holdings, history)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Все счета: по ним форма проверяет, не занято ли имя. */
    val allAccounts: StateFlow<List<Account>> =
        repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val mutableDraft = MutableStateFlow<AccountDraft?>(null)

    /** Открытая форма счёта; null — закрыта. */
    val draft: StateFlow<AccountDraft?> = mutableDraft.asStateFlow()

    fun startNew() {
        mutableDraft.value = AccountDraft()
    }

    fun startEdit(account: Account) {
        mutableDraft.value = AccountDraft.from(account)
    }

    fun updateDraft(change: (AccountDraft) -> AccountDraft) {
        mutableDraft.update { it?.let(change) }
    }

    fun dismissDraft() {
        mutableDraft.value = null
    }

    fun saveDraft() {
        val draft = mutableDraft.value ?: return
        if (draft.problem(allAccounts.value) != null) return
        mutableDraft.value = null
        viewModelScope.launch { repository.saveAccount(draft.toAccount()) }
    }

    fun deleteDraft() {
        val id = mutableDraft.value?.id?.takeIf { it.isNotBlank() } ?: return
        mutableDraft.value = null
        viewModelScope.launch { repository.deleteAccount(id) }
    }
}

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = viewModel { AccountsViewModel(AppGraph.repository) },
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val state = loaded ?: return
    val colors = FinanceTheme.colors

    Box(Modifier.fillMaxSize()) {
        if (state.groups.isEmpty()) {
            EmptyState("No accounts yet", "Tap + to add a bank account, a card or cash.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Запас снизу, чтобы последнюю строку не закрывала кнопка «+».
                contentPadding = screenContentPadding(extraBottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "net-worth") { NetWorthCard(state) }
                state.groups.forEach { group ->
                    item(key = "header-${group.title}") { GroupHeader(group) }
                    item(key = "group-${group.title}") {
                        FinanceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                            group.accounts.forEachIndexed { index, row ->
                                if (index > 0) {
                                    HorizontalDivider(color = colors.border, modifier = Modifier.padding(start = 62.dp))
                                }
                                AccountRow(row, onClick = { viewModel.startEdit(row.account) })
                            }
                        }
                    }
                }
            }
        }
        RoundAddButton(
            contentDescription = "Add account",
            onClick = viewModel::startNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    draft?.let { current ->
        AccountEditorSheet(
            draft = current,
            existing = allAccounts,
            valuedByHoldings = current.id in state.holdingAccountIds,
            onChange = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteDraft,
            onDismiss = viewModel::dismissDraft,
        )
    }
}

@Composable
private fun NetWorthCard(state: AccountsUiState) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    FinanceCard(
        hero = true,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text("Net worth", style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        MoneyText(state.netWorth, style = typography.heroAmount, sign = SignStyle.Negative)
        val change = state.change.minor
        Text(
            text = buildAnnotatedString {
                if (change == 0L) {
                    append("No change since ${state.firstLabel}")
                } else {
                    withStyle(SpanStyle(color = if (change > 0) colors.positiveText else colors.negativeText)) {
                        append(if (change > 0) "↑ " else "↓ ")
                        appendMoney(state.change.abs(), typography.bodySecondary.fontSize)
                    }
                    append(" since ${state.firstLabel}")
                }
            },
            style = typography.bodySecondary,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        AreaLineChart(
            values = state.history,
            color = colors.accent,
            firstLabel = state.firstLabel,
            lastLabel = state.lastLabel,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Assets", state.assets, Modifier.weight(1f))
            Stat("Liabilities", state.liabilities, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, amount: Money, modifier: Modifier = Modifier) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MoneyText(amount, style = typography.body, sign = SignStyle.Negative)
        Text(label, style = typography.caption, color = colors.textSecondary)
    }
}

@Composable
private fun GroupHeader(group: AccountGroupUi) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.title.uppercase(),
            style = typography.sectionLabel,
            color = colors.sectionLabel,
            modifier = Modifier.weight(1f),
        )
        MoneyText(group.total, style = typography.sectionLabel, color = colors.sectionLabel, sign = SignStyle.Negative)
    }
}

@Composable
private fun AccountRow(row: AccountRowUi, onClick: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = TONE_BACKGROUND_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.account.type.emoji(), fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.account.name,
                style = typography.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.subtitle.isNotEmpty()) {
                Text(row.subtitle, style = typography.caption, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        MoneyText(
            amount = row.displayBalance,
            color = if (row.displayBalance.minor < 0) colors.negativeText else colors.textPrimary,
            sign = SignStyle.Negative,
        )
    }
}
