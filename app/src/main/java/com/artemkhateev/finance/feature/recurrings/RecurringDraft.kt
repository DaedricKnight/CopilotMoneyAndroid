package com.artemkhateev.finance.feature.recurrings

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.ui.format.amountInputText
import com.artemkhateev.finance.ui.format.parseAmount

/** Форма регулярного платежа. Сумма хранится текстом — так, как её набирают. */
data class RecurringDraft(
    /** Пустой — новый платёж: id выдаст репозиторий. */
    val id: String = "",
    val name: String = "",
    val emoji: String = "🔁",
    val amountText: String = "",
    val dayOfMonth: Int? = null,
    val categoryId: String? = null,
) {
    val amount: Money? get() = parseAmount(amountText)?.takeIf { it.minor > 0 }

    /** Почему сохранить нельзя; null — можно. */
    fun problem(): String? = when {
        name.isBlank() -> "Name the payment"
        emoji.isBlank() -> "Pick an emoji"
        amount == null -> "Amount should be more than zero"
        dayOfMonth == null || dayOfMonth !in 1..31 -> "Pick the day it's charged"
        else -> null
    }

    fun toRecurring(): Recurring = Recurring(
        id = id,
        name = name.trim(),
        emoji = emoji,
        amount = requireNotNull(amount) { "Recurring draft is not valid: ${problem()}" },
        dayOfMonth = requireNotNull(dayOfMonth) { "Recurring draft is not valid: ${problem()}" },
        categoryId = categoryId,
    )

    companion object {
        fun from(recurring: Recurring) = RecurringDraft(
            id = recurring.id,
            name = recurring.name,
            emoji = recurring.emoji,
            amountText = amountInputText(recurring.amount),
            dayOfMonth = recurring.dayOfMonth,
            categoryId = recurring.categoryId,
        )
    }
}
