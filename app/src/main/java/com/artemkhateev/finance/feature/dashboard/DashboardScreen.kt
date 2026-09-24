package com.artemkhateev.finance.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artemkhateev.finance.data.AppGraph
import com.artemkhateev.finance.ui.components.CardShape
import com.artemkhateev.finance.ui.components.CategoryChip
import com.artemkhateev.finance.ui.components.FinanceCard
import com.artemkhateev.finance.ui.components.MoneyText
import com.artemkhateev.finance.ui.components.PillButton
import com.artemkhateev.finance.ui.components.ProgressRing
import com.artemkhateev.finance.ui.components.SectionHeader
import com.artemkhateev.finance.ui.components.SegmentedControl
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.ui.components.SpendingLineChart
import com.artemkhateev.finance.ui.components.appendMoney
import com.artemkhateev.finance.ui.components.screenContentPadding
import com.artemkhateev.finance.ui.format.SignStyle
import com.artemkhateev.finance.ui.theme.FinanceTheme

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel { DashboardViewModel(AppGraph.repository, AppGraph.settings) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui = state ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") {
            SegmentedControl(
                options = TransactionPeriod.entries.map { it.label },
                selectedIndex = ui.period.ordinal,
                onSelect = { viewModel.setPeriod(TransactionPeriod.entries[it]) },
                fill = true,
            )
        }
        item(key = "spending") { SpendingCard(ui.spending, ui.period, ui.periodDates) }
        ui.toReview?.let { group ->
            item(key = "review-header") { SectionHeader("To review", action = "View all") }
            item(key = "review") {
                ReviewCard(
                    group = group,
                    hasMore = ui.reviewCount > group.rows.size,
                    onMarkReviewed = { viewModel.markReviewed(group.rows.map { it.transaction.id }) },
                )
            }
        }
        item(key = "budgets-header") { SectionHeader("Budgets", action = "Categories") }
        item(key = "budgets") { BudgetRings(ui.budgets) }
    }
}

/** Каким периодом бюджет: «budgeted this week». */
private val TransactionPeriod.current: String
    get() = when (this) {
        TransactionPeriod.Day -> "today"
        TransactionPeriod.Week -> "this week"
        TransactionPeriod.Month -> "this month"
        TransactionPeriod.Quarter -> "this quarter"
        TransactionPeriod.HalfYear -> "this half-year"
        TransactionPeriod.Year -> "this year"
    }

@Composable
private fun SpendingCard(spending: SpendingLineUi, period: TransactionPeriod, periodDates: String) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    val overBudget = spending.left.minor < 0
    val underPace = spending.paceDelta.minor >= 0

    FinanceCard(
        hero = true,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                MoneyText(
                    amount = spending.left.abs(),
                    style = typography.heroAmount,
                    cents = false,
                    suffix = if (overBudget) " over" else " left",
                )
                Text(
                    text = buildAnnotatedString {
                        append("out of ")
                        appendMoney(spending.budget, typography.bodySecondary.fontSize, cents = false)
                        append(" budgeted ${period.current}")
                    },
                    style = typography.bodySecondary,
                    color = colors.textSecondary,
                )
                Text(periodDates, style = typography.caption, color = colors.textSecondary)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "How this is calculated",
                tint = colors.icon,
                modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
            )
        }
        // За один день линии нет: хватает остатка.
        if (period != TransactionPeriod.Day) {
            SpendingLineChart(
                dailyCumulative = spending.dailyCumulative,
                pace = spending.pace,
                label = buildAnnotatedString {
                    appendMoney(spending.paceDelta.abs(), typography.caption.fontSize, cents = false)
                    append(if (underPace) " under" else " over")
                },
                labelColor = if (underPace) colors.tooltipPositive else colors.negative,
                lineColors = if (underPace) {
                    listOf(colors.chartLineStart, colors.chartLineEnd)
                } else {
                    listOf(colors.warning, colors.negative)
                },
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
        }
    }
}

@Composable
private fun ReviewCard(group: ReviewGroupUi, hasMore: Boolean, onMarkReviewed: () -> Unit) {
    val colors = FinanceTheme.colors
    val typography = FinanceTheme.typography
    Box(Modifier.padding(top = if (hasMore) 8.dp else 0.dp)) {
        // Край следующей карточки стопки: в очереди на просмотр есть другие дни.
        if (hasMore) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-8).dp)
                    .clip(CardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, CardShape),
            )
        }
        FinanceCard(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(group.label, style = typography.bodySecondary, color = colors.textSecondary)
            Spacer(Modifier.height(10.dp))
            group.rows.forEach { row -> ReviewRow(row) }
            Spacer(Modifier.height(12.dp))
            PillButton("Mark as reviewed", onClick = onMarkReviewed)
        }
    }
}

@Composable
private fun ReviewRow(row: ReviewRowUi) {
    val colors = FinanceTheme.colors
    val income = row.transaction.amount.minor > 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.transaction.merchant,
            style = FinanceTheme.typography.body,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CategoryChip(row.category, Modifier.padding(horizontal = 10.dp))
        Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.CenterEnd) {
            MoneyText(
                amount = if (income) row.transaction.amount else row.transaction.amount.abs(),
                color = if (income) colors.positiveText else colors.textPrimary,
                sign = if (income) SignStyle.Always else SignStyle.None,
            )
        }
    }
}

@Composable
private fun BudgetRings(budgets: List<BudgetRingUi>) {
    val colors = FinanceTheme.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(budgets, key = { it.categoryId }) { ring ->
            ProgressRing(
                progress = ring.progress,
                color = when (ring.status) {
                    BudgetStatus.Over -> colors.negative
                    BudgetStatus.Warning -> colors.warning
                    BudgetStatus.OnTrack -> colors.positive
                },
                modifier = Modifier.size(64.dp),
            ) {
                Text(ring.emoji, fontSize = 24.sp)
            }
        }
    }
}
