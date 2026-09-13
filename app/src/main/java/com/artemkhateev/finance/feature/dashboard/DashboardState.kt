package com.artemkhateev.finance.feature.dashboard

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.dueDates
import com.artemkhateev.finance.data.model.sumOfMoney
import com.artemkhateev.finance.ui.format.dayLabel
import java.time.LocalDate

/** Порядок значим: кольца бюджетов идут от проблемных к спокойным. */
enum class BudgetStatus { Over, Warning, OnTrack }

data class SpendingLineUi(
    /** Остаток бюджета месяца; отрицательный — бюджет превышен. */
    val left: Money,
    val budget: Money,
    /** Накопленные расходы по дням месяца по сегодняшний включительно, в центах. */
    val dailyCumulative: List<Long>,
    /** Ожидаемые накопленные расходы на каждый день месяца, в центах. */
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
)

/** Одно списание регулярного платежа в этом месяце: день и сумма в центах. */
private data class BillCharge(val day: Int, val cents: Long)

fun buildDashboard(
    today: LocalDate,
    categories: List<Category>,
    transactions: List<Transaction>,
    recurrings: List<Recurring> = emptyList(),
): DashboardUiState {
    val categoryById = categories.associateBy { it.id }
    val monthStart = today.withDayOfMonth(1)
    val daysInMonth = today.lengthOfMonth()
    val monthEnd = today.withDayOfMonth(daysInMonth)

    // Ожидаемые расходы к концу дня: регулярные платежи — в дни списания, остаток
    // лимита — равномерно. Иначе аренда первого числа весь месяц выглядит перерасходом.
    fun expectedBy(day: Int, limit: Long, charges: List<BillCharge>): Long {
        val billsTotal = charges.sumOf { it.cents }
        val billsDue = charges.filter { it.day <= day }.sumOf { it.cents }
        return billsDue + (limit - billsTotal).coerceAtLeast(0L) * day / daysInMonth
    }

    // Расходы текущего месяца: поступления и доходные категории не в счёт.
    val expenses = transactions.filter {
        it.amount.minor < 0 &&
            !it.date.isBefore(monthStart) &&
            !it.date.isAfter(today) &&
            categoryById[it.categoryId]?.kind != CategoryKind.Income
    }

    val budgeted = categories.filter { it.kind == CategoryKind.Expense && (it.monthlyBudget?.minor ?: 0L) > 0L }
    val budget = budgeted.sumOfMoney { it.monthlyBudget!! }
    // Еженедельный платёж списывается в месяце несколько раз, годовой — только в своём месяце.
    val chargesByCategory = recurrings
        .groupBy { it.categoryId }
        .mapValues { (_, bills) ->
            bills.flatMap { bill -> bill.dueDates(monthStart, monthEnd).map { BillCharge(it.dayOfMonth, bill.amount.minor) } }
        }
    val budgetedCharges = budgeted.flatMap { chargesByCategory[it.id].orEmpty() }
    val pace = (1..daysInMonth).map { day -> expectedBy(day, budget.minor, budgetedCharges) }

    val spentByDay = LongArray(today.dayOfMonth)
    expenses.forEach { spentByDay[it.date.dayOfMonth - 1] -= it.amount.minor }
    val cumulative = spentByDay.runningReduce { total, day -> total + day }
    val spent = cumulative.last()

    val spentByCategory = expenses
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> -list.sumOf { it.amount.minor } }
    val budgets = budgeted
        .map { category ->
            val limit = category.monthlyBudget!!.minor
            val used = spentByCategory[category.id] ?: 0L
            val expected = expectedBy(today.dayOfMonth, limit, chargesByCategory[category.id].orEmpty())
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

    val unreviewed = transactions.filter { !it.reviewed }
    val latestDate = unreviewed.maxByOrNull { it.date.toEpochDay() }?.date
    val toReview = latestDate?.let { date ->
        ReviewGroupUi(
            label = if (date == today) "So far today" else dayLabel(date, today),
            rows = unreviewed.filter { it.date == date }.map { ReviewRowUi(it, categoryById[it.categoryId]) },
        )
    }

    return DashboardUiState(
        spending = SpendingLineUi(
            left = budget - Money(spent),
            budget = budget,
            dailyCumulative = cumulative,
            pace = pace,
            paceDelta = Money(pace[today.dayOfMonth - 1] - spent),
        ),
        toReview = toReview,
        reviewCount = unreviewed.size,
        budgets = budgets,
    )
}
