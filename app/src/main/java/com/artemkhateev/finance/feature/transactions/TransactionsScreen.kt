package com.artemkhateev.finance.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.artemkhateev.finance.ui.components.EmptyState
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.SectionHeader
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.theme.FinanceTheme
import com.artemkhateev.finance.ui.theme.TONE_BACKGROUND_ALPHA
import com.artemkhateev.finance.ui.theme.color

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = viewModel { TransactionsViewModel(AppGraph.repository) },
) {
    val state by viewModel.days.collectAsStateWithLifecycle()
    val days = state ?: return
    val colors = FinanceTheme.colors

    if (days.isEmpty()) {
        EmptyState("No transactions yet", "Load demo data in settings to see the app with real numbers.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            item(key = "header-${day.date}") { SectionHeader(day.label) }
            item(key = "day-${day.date}") {
                FinanceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    day.rows.forEachIndexed { index, row ->
                        if (index > 0) {
                            HorizontalDivider(color = colors.border, modifier = Modifier.padding(start = 62.dp))
                        }
                        TransactionRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(row: TransactionRowUi) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val tone = row.category?.tone?.color() ?: colors.textSecondary
    val income = row.transaction.amount.minor > 0

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
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
