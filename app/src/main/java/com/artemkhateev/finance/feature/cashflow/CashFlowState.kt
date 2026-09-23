package com.artemkhateev.finance.feature.cashflow

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.ui.format.longDate
import com.artemkhateev.finance.ui.format.periodLabel
import com.artemkhateev.finance.ui.format.shortDate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class PeriodComparison(
    val current: Money,
    val previous: Money,
    /** Изменение к прошлому периоду в процентах; null — сравнивать не с чем. */
    val changePercent: Int?,
)

/** Часть столбика; у чистого дохода и дохода цвет задаёт экран, у расходов — категория. */
data class BarSegmentUi(val value: Long, val tone: CategoryTone? = null)

/** Столбик за день, неделю или месяц начиная со [start]. */
data class FlowBarUi(val start: LocalDate, val segments: List<BarSegmentUi>)

data class CashFlowUiState(
    val period: TransactionPeriod,
    val currentPeriod: String,
    val previousPeriod: String,
    val net: PeriodComparison,
    val spend: PeriodComparison,
    val income: PeriodComparison,
    /** Столбики текущего периода; у одного дня их нет. */
    val netBars: List<FlowBarUi>,
    val spendBars: List<FlowBarUi>,
    val incomeBars: List<FlowBarUi>,
    val firstLabel: String,
    val lastLabel: String,
)

/** Период сравнивается с таким же прямо перед ним: последние 7 дней — с 7 днями до них. */
fun buildCashFlow(
    today: LocalDate,
    period: TransactionPeriod,
    transactions: List<Transaction>,
    categories: List<Category>,
): CashFlowUiState {
    val toneById = categories.associate { it.id to it.tone }
    val start = period.start(today)
    val previous = period.previous(today)

    fun between(from: LocalDate, to: LocalDate) =
        transactions.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
    fun incomeOf(list: List<Transaction>) = list.filter { it.amount.minor > 0 }.sumOf { it.amount.minor }
    fun spendOf(list: List<Transaction>) = -list.filter { it.amount.minor < 0 }.sumOf { it.amount.minor }

    val now = between(start, today)
    val before = between(previous.start, previous.endInclusive)

    val barStarts = barStarts(period, start, today)
    // Столбик транзакции — последний из начавшихся не позже её даты: границы совпадают с началами и в коротких месяцах.
    val byBar = now.groupBy { transaction -> barStarts.indexOfLast { !it.isAfter(transaction.date) } }

    fun bars(segments: (List<Transaction>) -> List<BarSegmentUi>) =
        barStarts.mapIndexed { index, barStart -> FlowBarUi(barStart, segments(byBar[index].orEmpty())) }

    return CashFlowUiState(
        period = period,
        currentPeriod = periodLabel(start, today),
        previousPeriod = periodLabel(previous.start, previous.endInclusive),
        net = compare(incomeOf(now) - spendOf(now), incomeOf(before) - spendOf(before)),
        spend = compare(spendOf(now), spendOf(before)),
        income = compare(incomeOf(now), incomeOf(before)),
        netBars = bars { list -> listOf(BarSegmentUi(incomeOf(list) - spendOf(list))) },
        spendBars = bars { list ->
            list.filter { it.amount.minor < 0 }
                .groupBy { it.categoryId }
                .map { (categoryId, spent) -> BarSegmentUi(-spent.sumOf { it.amount.minor }, categoryId?.let { toneById[it] }) }
                .sortedByDescending { it.value }
        },
        incomeBars = bars { list -> listOf(BarSegmentUi(incomeOf(list))) },
        // Период из прошлого года: первая подпись с годом, иначе края графика не отличить.
        firstLabel = if (start.year != today.year) longDate(start) else shortDate(start),
        lastLabel = shortDate(today),
    )
}

/** Начала столбиков: неделя — по дням, месяц и квартал — по неделям, полгода и год — по месяцам; у дня столбиков нет. */
private fun barStarts(period: TransactionPeriod, start: LocalDate, today: LocalDate): List<LocalDate> {
    val step: (Long) -> LocalDate = when (period) {
        TransactionPeriod.Day -> return emptyList()
        TransactionPeriod.Week -> { n -> start.plusDays(n) }
        TransactionPeriod.Month, TransactionPeriod.Quarter -> { n -> start.plusWeeks(n) }
        TransactionPeriod.HalfYear, TransactionPeriod.Year -> { n -> start.plusMonths(n) }
    }
    return generateSequence(0L) { it + 1 }.map(step).takeWhile { !it.isAfter(today) }.toList()
}

private fun compare(current: Long, previous: Long) = PeriodComparison(
    current = Money(current),
    previous = Money(previous),
    changePercent = if (previous == 0L) null else ((current - previous) * 100.0 / abs(previous)).roundToInt(),
)
