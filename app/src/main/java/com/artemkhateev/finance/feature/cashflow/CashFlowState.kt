package com.artemkhateev.finance.feature.cashflow

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.transactionsWindowStart
import com.artemkhateev.finance.ui.format.periodLabel
import com.artemkhateev.finance.ui.format.shortDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class PeriodComparison(
    val current: Money,
    val previous: Money,
    /** Изменение к прошлому периоду в процентах; null — сравнивать не с чем. */
    val changePercent: Int?,
)

/** Часть недельного столбика; у чистого дохода и дохода цвет задаёт экран, у расходов — категория. */
data class BarSegmentUi(val value: Long, val tone: CategoryTone? = null)

data class FlowBarUi(val weekStart: LocalDate, val segments: List<BarSegmentUi>)

data class CashFlowUiState(
    val currentPeriod: String,
    val previousPeriod: String,
    val net: PeriodComparison,
    val spend: PeriodComparison,
    val income: PeriodComparison,
    val netBars: List<FlowBarUi>,
    val spendBars: List<FlowBarUi>,
    val incomeBars: List<FlowBarUi>,
    val firstLabel: String,
    val lastLabel: String,
)

fun buildCashFlow(today: LocalDate, transactions: List<Transaction>, categories: List<Category>): CashFlowUiState {
    val toneById = categories.associate { it.id to it.tone }
    val monthStart = today.withDayOfMonth(1)
    val previousStart = monthStart.minusMonths(1)
    // Тот же отрезок прошлого месяца: 1–12 сентября сравниваем с 1–12 августа.
    val previousEnd = previousStart.withDayOfMonth(minOf(today.dayOfMonth, previousStart.lengthOfMonth()))

    fun between(start: LocalDate, end: LocalDate) =
        transactions.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    fun incomeOf(list: List<Transaction>) = list.filter { it.amount.minor > 0 }.sumOf { it.amount.minor }
    fun spendOf(list: List<Transaction>) = -list.filter { it.amount.minor < 0 }.sumOf { it.amount.minor }

    val now = between(monthStart, today)
    val before = between(previousStart, previousEnd)

    // Недели от начала загруженного окна по сегодня — столько истории экраны и получают.
    val windowStart = transactionsWindowStart(today)
    val weeks = generateSequence(windowStart) { it.plusWeeks(1) }.takeWhile { !it.isAfter(today) }.toList()
    val byWeek = between(windowStart, today).groupBy { ChronoUnit.WEEKS.between(windowStart, it.date).toInt() }

    fun bars(segments: (List<Transaction>) -> List<BarSegmentUi>) =
        weeks.mapIndexed { index, start -> FlowBarUi(start, segments(byWeek[index].orEmpty())) }

    return CashFlowUiState(
        currentPeriod = periodLabel(monthStart, today),
        previousPeriod = periodLabel(previousStart, previousEnd),
        net = compare(incomeOf(now) - spendOf(now), incomeOf(before) - spendOf(before)),
        spend = compare(spendOf(now), spendOf(before)),
        income = compare(incomeOf(now), incomeOf(before)),
        netBars = bars { week -> listOf(BarSegmentUi(incomeOf(week) - spendOf(week))) },
        spendBars = bars { week ->
            week.filter { it.amount.minor < 0 }
                .groupBy { it.categoryId }
                .map { (categoryId, list) -> BarSegmentUi(-list.sumOf { it.amount.minor }, categoryId?.let { toneById[it] }) }
                .sortedByDescending { it.value }
        },
        incomeBars = bars { week -> listOf(BarSegmentUi(incomeOf(week))) },
        firstLabel = shortDate(windowStart),
        lastLabel = shortDate(today),
    )
}

private fun compare(current: Long, previous: Long) = PeriodComparison(
    current = Money(current),
    previous = Money(previous),
    changePercent = if (previous == 0L) null else ((current - previous) * 100.0 / abs(previous)).roundToInt(),
)
