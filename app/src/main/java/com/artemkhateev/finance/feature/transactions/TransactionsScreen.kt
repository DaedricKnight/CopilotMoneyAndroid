package com.artemkhateev.finance.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.RoundAddButton
import com.artemkhateev.finance.ui.components.SectionHeader
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.format.recent
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.TONE_BACKGROUND_ALPHA
import com.artemkhateev.finance.ui.theme.color

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = viewModel { TransactionsViewModel(AppGraph.repository, AppGraph.settings) },
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val state = loaded ?: return
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Запас снизу, чтобы последнюю строку не закрывала кнопка «+».
            contentPadding = screenContentPadding(extraBottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "period") { PeriodCard(state, onPeriod = viewModel::setPeriod) }
            if (state.days.isEmpty()) {
                item(key = "empty") {
                    FinanceCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp)) {
                        Text("Nothing in this period", style = typography.cardTitle, color = colors.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tap + to add a transaction, or pick a longer period.",
                            style = typography.bodySecondary,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            state.days.forEach { day ->
                item(key = "header-${day.date}") { SectionHeader(day.label) }
                item(key = "day-${day.date}") {
                    FinanceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        day.rows.forEachIndexed { index, row ->
                            if (index > 0) {
                                HorizontalDivider(color = colors.border, modifier = Modifier.padding(start = 62.dp))
                            }
                            TransactionRow(row, onClick = { viewModel.startEdit(row.transaction) })
                        }
                    }
                }
            }
        }
        RoundAddButton(
            contentDescription = "Add transaction",
            onClick = viewModel::startNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }

    draft?.let { current ->
        TransactionEditorSheet(
            draft = current,
            categories = state.categories,
            accounts = state.accounts,
            onChange = viewModel::updateDraft,
            onSave = viewModel::saveDraft,
            onDelete = viewModel::deleteDraft,
            onDismiss = viewModel::dismissDraft,
            categoryUsage = state.categoryUsage,
            onCreateCategory = viewModel::createCategory,
        )
    }
}

/** Траты за выбранный период и сам выбор периода. */
@Composable
private fun PeriodCard(state: TransactionsUiState, onPeriod: (TransactionPeriod) -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    FinanceCard(
        hero = true,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text("Spending", style = typography.cardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        MoneyText(state.spent, style = typography.heroAmount, cents = false)
        Text("spent ${state.period.recent}", style = typography.bodySecondary, color = colors.textSecondary)
        Text(
            text = if (state.count == 1) "1 transaction" else "${state.count} transactions",
            style = typography.caption,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        SegmentedControl(
            options = TransactionPeriod.entries.map { it.label },
            selectedIndex = state.period.ordinal,
            onSelect = { onPeriod(TransactionPeriod.entries[it]) },
            fill = true,
        )
    }
}

@Composable
private fun TransactionRow(row: TransactionRowUi, onClick: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val tone = row.category?.tone?.color() ?: colors.textSecondary
    val income = row.transaction.amount.minor > 0

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
                .background(tone.copy(alpha = TONE_BACKGROUND_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.category?.emoji ?: "❔", fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.transaction.merchant,
                    style = typography.body,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!row.transaction.reviewed) {
                    Spacer(Modifier.width(6.dp))
                    // Точка — транзакция ещё не просмотрена.
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accent))
                }
            }
            Text(row.accountName, style = typography.caption, color = colors.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        MoneyText(
            amount = if (income) row.transaction.amount else row.transaction.amount.abs(),
            color = if (income) colors.positiveText else colors.textPrimary,
            sign = if (income) SignStyle.Always else SignStyle.None,
        )
    }
}
