package com.artemkhateev.finance.feature.transactions

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.ui.format.amountInputText
import com.artemkhateev.finance.ui.format.parseAmount
import java.time.LocalDate

enum class EntryKind { Expense, Income }

/** Форма добавления и правки транзакции. Сумма хранится текстом — так, как её набирают. */
data class TransactionDraft(
    /** Пустой — новая транзакция: id выдаст репозиторий. */
    val id: String = "",
    val kind: EntryKind = EntryKind.Expense,
    val amountText: String = "",
    val merchant: String = "",
    val note: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val date: LocalDate,
    /** Введённое вручную смотреть в «To review» незачем. */
    val reviewed: Boolean = true,
) {
    val amount: Money? get() = parseAmount(amountText)?.takeIf { it.minor > 0 }

    val isValid: Boolean get() = amount != null && merchant.isNotBlank() && accountId != null

    fun toTransaction(): Transaction? {
        val value = amount ?: return null
        val account = accountId ?: return null
        if (merchant.isBlank()) return null
        return Transaction(
            id = id,
            accountId = account,
            merchant = merchant.trim(),
            amount = if (kind == EntryKind.Expense) -value else value,
            date = date,
            categoryId = categoryId,
            note = note.trim().ifEmpty { null },
            reviewed = reviewed,
        )
    }

    companion object {
        fun from(transaction: Transaction) = TransactionDraft(
            id = transaction.id,
            kind = if (transaction.amount.minor > 0) EntryKind.Income else EntryKind.Expense,
            amountText = amountInputText(transaction.amount.abs()),
            merchant = transaction.merchant,
            note = transaction.note.orEmpty(),
            categoryId = transaction.categoryId,
            accountId = transaction.accountId,
            date = transaction.date,
            reviewed = transaction.reviewed,
        )
    }
}
