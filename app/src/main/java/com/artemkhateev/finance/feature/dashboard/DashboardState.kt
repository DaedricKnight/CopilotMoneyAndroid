package com.artemkhateev.finance.feature.dashboard

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringSchedule
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.data.model.dueDates
import com.artemkhateev.finance.data.transactionsWindowStart
import com.artemkhateev.finance.ui.format.dayLabel
import com.artemkhateev.finance.ui.format.periodLabel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Порядок значим: кольца бюджетов идут от проблемных к спокойным. */
enum class BudgetStatus { Over, Warning, OnTrack }

data class SpendingLineUi(
    /** Остаток бюджета периода; отрицательный — бюджет превышен. */
    val left: Money,
    val budget: Money,
    /** Накопленные расходы по дням периода по сегодняшний включительно, в центах. */
    val dailyCumulative: List<Long>,
    /** Ожидаемые накопленные расходы на каждый день периода, в центах. */
    val pace: List<Long>,
    /** Насколько расходы ниже ожидаемых к сегодняшнему дню; отрицательный — выше. */
    val paceDelta: Money,
)

data class ReviewRowUi(val transaction: Transaction, val category: Category?)

data class ReviewGroupUi(val label: String, val rows: List<ReviewRowUi>)

data class BudgetRingUi(
    val categoryId: String,
    val emoji: String,
    val progress: Float,
    val status: BudgetStatus,
)

data class DashboardUiState(
    val spending: SpendingLineUi,
    /** Самый свежий день с непросмотренными транзакциями; null — всё просмотрено. */
    val toReview: ReviewGroupUi?,
    val reviewCount: Int,
    val budgets: List<BudgetRingUi>,
    /** Текущий календарный период: сегодня, эта неделя, месяц, квартал, полугодие или год. */
    val period: TransactionPeriod = TransactionPeriod.Month,
    /** Даты периода: «Sep 1 – Sep 30». */
    val periodDates: String = "",
)

/** Одно списание регулярного платежа в периоде: день периода, считая с 1, и сумма в центах. */
private data class BillCharge(val day: Int, val cents: Long)

/** Бюджет и темп на текущий календарный период: сколько осталось до его конца и не тратится ли быстрее плана. */
fun buildDashboard(
    today: LocalDate,
    categories: List<Category>,
    transactions: List<Transaction>,
    recurrings: List<Recurring> = emptyList(),
    period: TransactionPeriod = TransactionPeriod.Month,
): DashboardUiState {
    val categoryById = categories.associateBy { it.id }
    val range = period.calendar(today)
    val start = range.start
    val end = range.endInclusive
    fun dayOf(date: LocalDate) = ChronoUnit.DAYS.between(start, date).toInt() + 1
    val days = dayOf(end)
    val elapsed = dayOf(today)

    // Ожидаемые расходы к концу дня: регулярные платежи — в дни списания, остаток
    // лимита — равномерно. Иначе аренда первого числа весь месяц выглядит перерасходом.
    fun expectedBy(day: Int, limit: Long, charges: List<BillCharge>): Long {
        val billsTotal = charges.sumOf { it.cents }
        val billsDue = charges.filter { it.day <= day }.sumOf { it.cents }
        return billsDue + (limit - billsTotal).coerceAtLeast(0L) * day / days
    }

    // Расходы периода по сегодня: поступления и доходные категории не в счёт.
    val expenses = transactions.filter {
        it.amount.minor < 0 &&
            !it.date.isBefore(start) &&
            !it.date.isAfter(today) &&
            categoryById[it.categoryId]?.kind != CategoryKind.Income
    }

    val budgeted = categories.filter { it.kind == CategoryKind.Expense && (it.monthlyBudget?.minor ?: 0L) > 0L }
    val billsByCategory = recurrings.groupBy { it.categoryId }
    // Еженедельный платёж списывается в периоде несколько раз, годовой — только в своём месяце.
    val chargesByCategory = billsByCategory.mapValues { (_, bills) ->
        bills.flatMap { bill -> bill.dueDates(start, end).map { BillCharge(dayOf(it), bill.amount.minor) } }
    }
    val limitById = budgeted.associate { category ->
        category.id to periodLimit(
            period,
            category.monthlyBudget!!.minor,
            billsByCategory[category.id].orEmpty(),
            chargesByCategory[category.id].orEmpty(),
        )
    }
    val budget = limitById.values.sum()
    val budgetedCharges = budgeted.flatMap { chargesByCategory[it.id].orEmpty() }
    val pace = (1..days).map { day -> expectedBy(day, budget, budgetedCharges) }

    val spentByDay = LongArray(elapsed)
    expenses.forEach { spentByDay[dayOf(it.date) - 1] -= it.amount.minor }
    val cumulative = spentByDay.runningReduce { total, day -> total + day }
    val spent = cumulative.last()

    val spentByCategory = expenses
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> -list.sumOf { it.amount.minor } }
    val budgets = budgeted
        .map { category ->
            val limit = limitById.getValue(category.id)
            val used = spentByCategory[category.id] ?: 0L
            val expected = expectedBy(elapsed, limit, chargesByCategory[category.id].orEmpty())
            BudgetRingUi(
                categoryId = category.id,
                emoji = category.emoji,
                progress = (used.toFloat() / limit).coerceAtMost(1f),
                status = when {
                    used > limit -> BudgetStatus.Over
                    used > expected -> BudgetStatus.Warning
                    else -> BudgetStatus.OnTrack
                },
            )
        }
        .sortedWith(compareBy<BudgetRingUi> { it.status.ordinal }.thenByDescending { it.progress })

    // «To review» от периода не зависит: как и раньше, только обычное окно загрузки.
    val windowStart = transactionsWindowStart(today)
    val unreviewed = transactions.filter { !it.reviewed && !it.date.isBefore(windowStart) }
    val latestDate = unreviewed.maxByOrNull { it.date.toEpochDay() }?.date
    val toReview = latestDate?.let { date ->
        ReviewGroupUi(
            label = if (date == today) "So far today" else dayLabel(date, today),
            rows = unreviewed.filter { it.date == date }.map { ReviewRowUi(it, categoryById[it.categoryId]) },
        )
    }

    return DashboardUiState(
        spending = SpendingLineUi(
            left = Money(budget - spent),
            budget = Money(budget),
            dailyCumulative = cumulative,
            pace = pace,
            paceDelta = Money(pace[elapsed - 1] - spent),
        ),
        toReview = toReview,
        reviewCount = unreviewed.size,
        budgets = budgets,
        period = period,
        periodDates = periodLabel(start, end),
    )
}

/**
 * Лимит категории на календарный период: за целые месяцы — месячный бюджет, умноженный на их число; за неделю
 * и день — выпадающие на них регулярные платежи и доля остального бюджета (12/52 и 12/365). Так неделя с арендой
 * не выглядит перерасходом, а неделя без неё не обещает денег на аренду. Хотя бы цент: иначе доля потраченного
 * делится на ноль.
 */
private fun periodLimit(period: TransactionPeriod, monthly: Long, bills: List<Recurring>, charges: List<BillCharge>): Long {
    val limit = when (period) {
        TransactionPeriod.Day, TransactionPeriod.Week -> {
            val rest = (monthly - bills.sumOf { it.monthlyCost() }).coerceAtLeast(0L)
            val (times, per) = if (period == TransactionPeriod.Day) 12L to 365L else 12L to 52L
            charges.sumOf { it.cents } + (rest * times + per / 2) / per
        }
        TransactionPeriod.Month -> monthly
        TransactionPeriod.Quarter -> monthly * 3
        TransactionPeriod.HalfYear -> monthly * 6
        TransactionPeriod.Year -> monthly * 12
    }
    return limit.coerceAtLeast(1L)
}

/** Сколько платёж стоит в среднем за месяц: еженедельный — 52 раза в год, ежегодный — раз в год. */
private fun Recurring.monthlyCost(): Long = when (schedule) {
    is RecurringSchedule.Weekly -> amount.minor * 52 / 12
    is RecurringSchedule.Monthly -> amount.minor
    is RecurringSchedule.Yearly -> amount.minor / 12
}
