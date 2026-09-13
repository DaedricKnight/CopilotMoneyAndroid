package com.artemkhateev.finance.feature.recurrings

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.sumOfMoney
import com.artemkhateev.finance.ui.format.ordinalDay
import java.time.LocalDate

data class RecurringTileUi(
    val recurring: Recurring,
    val tone: CategoryTone?,
    val paid: Boolean,
    /** Фактическое списание, если оно найдено; иначе ожидаемая сумма. */
    val amount: Money,
    val dueLabel: String,
)

data class RecurringsUiState(
    val leftToPay: Money,
    val paidSoFar: Money,
    /** Доля оплаченного от суммы всех платежей месяца, 0..1. */
    val progress: Float,
    val thisMonth: List<RecurringTileUi>,
)

fun buildRecurrings(
    today: LocalDate,
    recurrings: List<Recurring>,
    categories: List<Category>,
    transactions: List<Transaction>,
): RecurringsUiState {
    val monthStart = today.withDayOfMonth(1)
    val spentThisMonth = transactions.filter {
        it.amount.minor < 0 && !it.date.isBefore(monthStart) && !it.date.isAfter(today)
    }
    val toneById = categories.associate { it.id to it.tone }
    val matched = mutableSetOf<String>()

    val tiles = recurrings.sortedBy { it.dayOfMonth }.map { recurring ->
        // Платёж внесён, если в этом месяце есть расход с тем же названием или той же суммой
        // в той же категории. Одна транзакция закрывает только один платёж.
        val payment = spentThisMonth.firstOrNull { transaction ->
            transaction.id !in matched && (
                transaction.merchant.equals(recurring.name, ignoreCase = true) ||
                    (transaction.categoryId == recurring.categoryId && transaction.amount.abs() == recurring.amount)
                )
        }
        payment?.let { matched += it.id }
        RecurringTileUi(
            recurring = recurring,
            tone = recurring.categoryId?.let { toneById[it] },
            paid = payment != null,
            amount = payment?.amount?.abs() ?: recurring.amount,
            // В коротком месяце платёж на 29–31-е приходится на его последний день.
            dueLabel = ordinalDay(minOf(recurring.dayOfMonth, today.lengthOfMonth())),
        )
    }

    val paid = tiles.filter { it.paid }.sumOfMoney { it.amount }
    val left = tiles.filterNot { it.paid }.sumOfMoney { it.amount }
    val total = paid.minor + left.minor
    return RecurringsUiState(
        leftToPay = left,
        paidSoFar = paid,
        progress = if (total == 0L) 0f else paid.minor.toFloat() / total,
        thisMonth = tiles,
    )
}
