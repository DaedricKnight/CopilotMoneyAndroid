package com.artemkhateev.finance.feature.recurrings

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringSchedule
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.data.model.dueDates
import com.artemkhateev.finance.data.model.sumOfMoney
import com.artemkhateev.finance.ui.format.longDate
import com.artemkhateev.finance.ui.format.monthShort
import com.artemkhateev.finance.ui.format.ordinalDay
import com.artemkhateev.finance.ui.format.periodLabel
import com.artemkhateev.finance.ui.format.shortDate
import com.artemkhateev.finance.ui.format.weekdayName
import com.artemkhateev.finance.ui.format.weekdayShort
import java.time.LocalDate
import java.time.Month

data class RecurringTileUi(
    val recurring: Recurring,
    val tone: CategoryTone?,
    /** Все списания периода уже найдены. */
    val paid: Boolean,
    /** Сумма списания; у единственного в периоде — фактическая, если оно найдено. */
    val amount: Money,
    val dueLabel: String,
    /** Сколько раз платёж списывается в периоде: у еженедельного за месяц — 4 или 5. */
    val dueCount: Int,
    /** Сколько из этих списаний уже найдено среди расходов. */
    val paidCount: Int,
    /** Сколько по платежу уже ушло в периоде. */
    val paidAmount: Money,
    /** Сколько по платежу ещё предстоит в периоде. */
    val leftAmount: Money,
)

/** Платёж, который в этом периоде не списывается: следующее списание — после него. */
data class UpcomingRecurringUi(
    val recurring: Recurring,
    val nextDue: LocalDate,
    val dueLabel: String,
    /** «Weekly», «Monthly» или «Yearly». */
    val frequency: String,
)

data class RecurringsUiState(
    val leftToPay: Money,
    val paidSoFar: Money,
    /** Доля оплаченного от суммы всех списаний периода, 0..1. */
    val progress: Float,
    /** Платежи со списаниями в периоде, по дате первого. */
    val inPeriod: List<RecurringTileUi>,
    /** Платежи без списаний в периоде, ближайшие сначала. */
    val later: List<UpcomingRecurringUi>,
    /** Текущий календарный период: сегодня, эта неделя, месяц, квартал, полугодие или год. */
    val period: TransactionPeriod = TransactionPeriod.Month,
    /** Даты периода: «Sep 1 – Sep 30». */
    val periodDates: String = "",
)

fun buildRecurrings(
    today: LocalDate,
    recurrings: List<Recurring>,
    categories: List<Category>,
    transactions: List<Transaction>,
    period: TransactionPeriod = TransactionPeriod.Month,
): RecurringsUiState {
    val range = period.calendar(today)
    val start = range.start
    val end = range.endInclusive
    val spentInPeriod = transactions.filter {
        it.amount.minor < 0 && !it.date.isBefore(start) && !it.date.isAfter(today)
    }
    val toneById = categories.associate { it.id to it.tone }
    val matched = mutableSetOf<String>()
    val (dueInPeriod, notInPeriod) = recurrings
        .map { it to it.dueDates(start, end) }
        .partition { (_, dates) -> dates.isNotEmpty() }

    val tiles = dueInPeriod.sortedBy { (_, dates) -> dates.first().toEpochDay() }.map { (recurring, dates) ->
        // Списание внесено, если в периоде есть расход с тем же названием или той же суммой
        // в той же категории. Одна транзакция закрывает одно списание, у еженедельного их несколько.
        val payments = spentInPeriod
            .filter { it.id !in matched && it.pays(recurring) }
            .take(dates.size)
        matched += payments.map { it.id }
        val paidAmount = Money(-payments.sumOf { it.amount.minor })
        RecurringTileUi(
            recurring = recurring,
            tone = recurring.categoryId?.let { toneById[it] },
            paid = payments.size == dates.size,
            amount = if (dates.size == 1 && payments.size == 1) paidAmount else recurring.amount,
            dueLabel = dueLabel(recurring.schedule, dates, payments.size),
            dueCount = dates.size,
            paidCount = payments.size,
            paidAmount = paidAmount,
            leftAmount = Money((dates.size - payments.size) * recurring.amount.minor),
        )
    }

    val later = notInPeriod
        .mapNotNull { (recurring, _) ->
            // Следующее списание после периода — в пределах года: реже, чем раз в год, платежей нет.
            recurring.dueDates(end.plusDays(1), end.plusYears(1)).firstOrNull()?.let { next ->
                UpcomingRecurringUi(
                    recurring = recurring,
                    nextDue = next,
                    dueLabel = if (next.year == today.year) shortDate(next) else longDate(next),
                    frequency = recurring.schedule.frequency(),
                )
            }
        }
        .sortedBy { it.nextDue.toEpochDay() }

    val paid = tiles.sumOfMoney { it.paidAmount }
    val left = tiles.sumOfMoney { it.leftAmount }
    val total = paid.minor + left.minor
    return RecurringsUiState(
        leftToPay = left,
        paidSoFar = paid,
        progress = if (total == 0L) 0f else paid.minor.toFloat() / total,
        inPeriod = tiles,
        later = later,
        period = period,
        periodDates = periodLabel(start, end),
    )
}

/** Расписание словами — подпись под выбором дня в форме платежа. */
fun RecurringSchedule.describe(): String = when (this) {
    is RecurringSchedule.Weekly -> "Every ${weekdayName(dayOfWeek)}"
    is RecurringSchedule.Monthly ->
        if (dayOfMonth > 28) {
            "Every month on the ${ordinalDay(dayOfMonth)}, or on the last day of shorter months"
        } else {
            "Every month on the ${ordinalDay(dayOfMonth)}"
        }
    is RecurringSchedule.Yearly ->
        if (month == Month.FEBRUARY && dayOfMonth == 29) {
            "Every year on Feb 29, or Feb 28 when it isn't a leap year"
        } else {
            "Every year on ${monthShort(month)} $dayOfMonth"
        }
}

private fun Transaction.pays(recurring: Recurring): Boolean =
    merchant.equals(recurring.name, ignoreCase = true) ||
        (categoryId == recurring.categoryId && amount.abs() == recurring.amount)

/** Подпись плитки: число месяца, дата годового или день недели и сколько списаний уже прошло. */
private fun dueLabel(schedule: RecurringSchedule, dates: List<LocalDate>, paidCount: Int): String = when (schedule) {
    is RecurringSchedule.Weekly -> "${weekdayShort(schedule.dayOfWeek)} · $paidCount/${dates.size}"
    // За квартал и дольше у ежемесячного несколько списаний: видно, сколько уже прошло.
    is RecurringSchedule.Monthly ->
        if (dates.size == 1) ordinalDay(dates.first().dayOfMonth) else "${ordinalDay(schedule.dayOfMonth)} · $paidCount/${dates.size}"
    is RecurringSchedule.Yearly -> shortDate(dates.first())
}

private fun RecurringSchedule.frequency(): String = when (this) {
    is RecurringSchedule.Weekly -> "Weekly"
    is RecurringSchedule.Monthly -> "Monthly"
    is RecurringSchedule.Yearly -> "Yearly"
}
