package com.artemkhateev.finance.feature.recurrings

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringFrequency
import com.artemkhateev.finance.data.model.RecurringSchedule
import com.artemkhateev.finance.ui.format.amountInputText
import com.artemkhateev.finance.ui.format.parseAmount
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * Форма регулярного платежа. Сумма хранится текстом — так, как её набирают. День недели, число и месяц
 * хранятся отдельно от частоты: при переключении частоты выбранное не теряется.
 */
data class RecurringDraft(
    /** Пустой — новый платёж: id выдаст репозиторий. */
    val id: String = "",
    val name: String = "",
    val emoji: String = "🔁",
    val amountText: String = "",
    val frequency: RecurringFrequency = RecurringFrequency.Monthly,
    val dayOfWeek: DayOfWeek? = null,
    val dayOfMonth: Int? = null,
    val month: Month? = null,
    val categoryId: String? = null,
) {
    val amount: Money? get() = parseAmount(amountText)?.takeIf { it.minor > 0 }

    /** Расписание по выбранной частоте; null — день не выбран или такой даты не бывает. */
    val schedule: RecurringSchedule?
        get() = when (frequency) {
            RecurringFrequency.Weekly -> dayOfWeek?.let { RecurringSchedule.Weekly(it) }
            RecurringFrequency.Monthly -> dayOfMonth?.takeIf { it in 1..31 }?.let { RecurringSchedule.Monthly(it) }
            RecurringFrequency.Yearly -> {
                val chosenMonth = month
                val day = dayOfMonth
                if (chosenMonth != null && day != null && day in 1..chosenMonth.maxLength()) {
                    RecurringSchedule.Yearly(chosenMonth, day)
                } else {
                    null
                }
            }
        }

    /** Почему сохранить нельзя; null — можно. */
    fun problem(): String? = when {
        name.isBlank() -> "Name the payment"
        emoji.isBlank() -> "Pick an emoji"
        amount == null -> "Amount should be more than zero"
        schedule == null -> "Pick the day it's charged"
        else -> null
    }

    fun toRecurring(): Recurring = Recurring(
        id = id,
        name = name.trim(),
        emoji = emoji,
        amount = requireNotNull(amount) { "Recurring draft is not valid: ${problem()}" },
        schedule = requireNotNull(schedule) { "Recurring draft is not valid: ${problem()}" },
        categoryId = categoryId,
    )

    companion object {
        /** Платёж обычно заводят в день списания: сегодняшний день подставлен для любой частоты. */
        fun startingOn(today: LocalDate) =
            RecurringDraft(dayOfWeek = today.dayOfWeek, dayOfMonth = today.dayOfMonth, month = today.month)

        fun from(recurring: Recurring): RecurringDraft {
            val draft = RecurringDraft(
                id = recurring.id,
                name = recurring.name,
                emoji = recurring.emoji,
                amountText = amountInputText(recurring.amount),
                frequency = recurring.schedule.frequency,
                categoryId = recurring.categoryId,
            )
            return when (val schedule = recurring.schedule) {
                is RecurringSchedule.Weekly -> draft.copy(dayOfWeek = schedule.dayOfWeek)
                is RecurringSchedule.Monthly -> draft.copy(dayOfMonth = schedule.dayOfMonth)
                is RecurringSchedule.Yearly -> draft.copy(month = schedule.month, dayOfMonth = schedule.dayOfMonth)
            }
        }
    }
}
