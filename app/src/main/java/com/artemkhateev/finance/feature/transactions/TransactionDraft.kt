package com.artemkhateev.finance.feature.transactions

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import java.math.BigDecimal
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
            amountText = BigDecimal.valueOf(transaction.amount.abs().minor, 2).toPlainString(),
            merchant = transaction.merchant,
            note = transaction.note.orEmpty(),
            categoryId = transaction.categoryId,
            accountId = transaction.accountId,
            date = transaction.date,
            reviewed = transaction.reviewed,
        )
    }
}

private val amountPattern = Regex("""\d{1,9}([.,]\d{0,2})?""")

/** "12", "12.5", "12,50" → центы. Всё, что не похоже на сумму, — null. */
fun parseAmount(text: String): Money? {
    val trimmed = text.trim()
    if (!amountPattern.matches(trimmed)) return null
    return Money(BigDecimal(trimmed.replace(',', '.')).movePointRight(2).toLong())
}

/** Фильтр ввода суммы: цифры, один разделитель (всегда точка) и не больше двух знаков после него. */
fun sanitizeAmountInput(raw: String): String {
    val result = StringBuilder()
    var separatorSeen = false
    var decimals = 0
    for (ch in raw) {
        when {
            ch in '0'..'9' && !separatorSeen -> if (result.length < 9) result.append(ch)
            ch in '0'..'9' && decimals < 2 -> {
                result.append(ch)
                decimals++
            }
            (ch == '.' || ch == ',') && !separatorSeen -> {
                if (result.isEmpty()) result.append('0')
                result.append('.')
                separatorSeen = true
            }
        }
    }
    return result.toString()
}
