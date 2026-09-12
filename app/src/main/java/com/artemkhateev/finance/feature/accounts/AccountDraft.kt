package com.artemkhateev.finance.feature.accounts

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.ui.format.amountInputText
import com.artemkhateev.finance.ui.format.parseAmount

/**
 * Форма счёта. Остаток вводится без знака: у кредитной карты это сумма долга,
 * и в счёт она сохраняется отрицательной.
 */
data class AccountDraft(
    /** Пустой — новый счёт: id выдаст репозиторий. */
    val id: String = "",
    val name: String = "",
    val institution: String = "",
    val type: AccountType = AccountType.Checking,
    val balanceText: String = "",
    val mask: String = "",
) {
    /** Остаток со знаком; пустое поле — ноль, неразборчивый текст — null. */
    val balance: Money?
        get() {
            if (balanceText.isBlank()) return Money.Zero
            val amount = parseAmount(balanceText) ?: return null
            return if (type == AccountType.CreditCard) -amount else amount
        }

    /** Почему сохранить нельзя; null — можно. */
    fun problem(existing: List<Account>): String? = when {
        name.isBlank() -> "Name the account"
        existing.any { it.id != id && it.name.equals(name.trim(), ignoreCase = true) } ->
            "An account with this name already exists"
        balance == null -> "Balance should be an amount"
        else -> null
    }

    fun toAccount() = Account(
        id = id,
        name = name.trim(),
        institution = institution.trim(),
        type = type,
        balance = balance ?: Money.Zero,
        mask = mask.ifBlank { null },
    )

    companion object {
        fun from(account: Account) = AccountDraft(
            id = account.id,
            name = account.name,
            institution = account.institution,
            type = account.type,
            balanceText = amountInputText(account.balance.abs()),
            mask = account.mask.orEmpty(),
        )
    }
}
