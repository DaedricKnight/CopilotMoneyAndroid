package com.artemkhateev.finance.feature.recurrings

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringSchedule
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.dueDates
import com.artemkhateev.finance.data.model.sumOfMoney
import com.artemkhateev.finance.ui.format.longDate
import com.artemkhateev.finance.ui.format.monthShort
import com.artemkhateev.finance.ui.format.ordinalDay
import com.artemkhateev.finance.ui.format.shortDate
import com.artemkhateev.finance.ui.format.weekdayName
import com.artemkhateev.finance.ui.format.weekdayShort
import java.time.LocalDate
import java.time.Month

data class RecurringTileUi(
    val recurring: Recurring,
    val tone: CategoryTone?,
    /** Все списания этого месяца уже найдены. */
    val paid: Boolean,
    /** Сумма списания; у единственного в месяце — фактическая, если оно найдено. */
    val amount: Money,
    val dueLabel: String,
    /** Сколько раз платёж списывается в этом месяце: у еженедельного — 4 или 5. */
    val dueCount: Int,
    /** Сколько из этих списаний уже найдено среди расходов. */
    val paidCount: Int,
    /** Сколько по платежу уже ушло в этом месяце. */
    val paidAmount: Money,
    /** Сколько по платежу ещё предстоит в этом месяце. */
    val leftAmount: Money,
)

/** Платёж, который в этом месяце не списывается, — годовой другого месяца. */
data class UpcomingRecurringUi(
    val recurring: Recurring,
    val nextDue: LocalDate,
    val dueLabel: String,
)

data class RecurringsUiState(
    val leftToPay: Money,
    val paidSoFar: Money,
    /** Доля оплаченного от суммы всех списаний месяца, 0..1. */
    val progress: Float,
    val thisMonth: List<RecurringTileUi>,
    /** Годовые платежи других месяцев, ближайшие сначала. */
    val later: List<UpcomingRecurringUi>,
)

fun buildRecurrings(
    today: LocalDate,
    recurrings: List<Recurring>,
    categories: List<Category>,
    transactions: List<Transaction>,
): RecurringsUiState {
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val spentThisMonth = transactions.filter {
        it.amount.minor < 0 && !it.date.isBefore(monthStart) && !it.date.isAfter(today)
    }
    val toneById = categories.associate { it.id to it.tone }
    val matched = mutableSetOf<String>()
    val (dueThisMonth, notThisMonth) = recurrings
        .map { it to it.dueDates(monthStart, monthEnd) }
        .partition { (_, dates) -> dates.isNotEmpty() }

    val tiles = dueThisMonth.sortedBy { (_, dates) -> dates.first().toEpochDay() }.map { (recurring, dates) ->
        // Списание внесено, если в этом месяце есть расход с тем же названием или той же суммой
        // в той же категории. Одна транзакция закрывает одно списание, у еженедельного их несколько.
        val payments = spentThisMonth
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

    val later = notThisMonth
        .mapNotNull { (recurring, _) ->
            // Годовой платёж другого месяца: следующее списание — в пределах года.
            recurring.dueDates(monthEnd.plusDays(1), monthEnd.plusYears(1)).firstOrNull()?.let { next ->
                UpcomingRecurringUi(recurring, next, if (next.year == today.year) shortDate(next) else longDate(next))
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
        thisMonth = tiles,
        later = later,
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
    is RecurringSchedule.Monthly -> ordinalDay(dates.first().dayOfMonth)
    is RecurringSchedule.Yearly -> shortDate(dates.first())
}
