package com.artemkhateev.finance.feature.accounts

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountByName
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.transactionsWindowStart
import com.artemkhateev.finance.ui.format.shortDate
import java.time.LocalDate

data class AccountRowUi(
    val account: Account,
    /** Остаток для показа: у кредитных карт — сумма долга без минуса. */
    val displayBalance: Money,
    /** «Demo Bank •• 2124»; пустая строка, если ни банка, ни цифр нет. */
    val subtitle: String,
)

data class AccountGroupUi(
    val title: String,
    /** Группа долгов: её итог вычитается из чистого капитала. */
    val liability: Boolean,
    val total: Money,
    val accounts: List<AccountRowUi>,
)

data class AccountsUiState(
    val netWorth: Money,
    val assets: Money,
    /** Сколько должны, положительным числом. */
    val liabilities: Money,
    /** Чистый капитал на конец каждого дня загруженного окна, в центах. */
    val history: List<Long>,
    /** Изменение чистого капитала с начала окна. */
    val change: Money,
    val firstLabel: String,
    val lastLabel: String,
    val groups: List<AccountGroupUi>,
)

/** Порядок групп: сначала деньги, потом долги. */
private val GroupOrder = listOf(
    AccountType.Checking to "Cash & checking",
    AccountType.Savings to "Savings",
    AccountType.Investment to "Investments",
    AccountType.CreditCard to "Credit cards",
)

fun buildAccounts(today: LocalDate, accounts: List<Account>, transactions: List<Transaction>): AccountsUiState {
    val assets = accounts.filter { it.type != AccountType.CreditCard }.sumOf { it.balance.minor }
    val liabilities = -accounts.filter { it.type == AccountType.CreditCard }.sumOf { it.balance.minor }
    val history = netWorthHistory(today, accounts, transactions)

    val groups = GroupOrder.mapNotNull { (type, title) ->
        val ofType = accounts.filter { it.type == type }.sortedWith(AccountByName)
        if (ofType.isEmpty()) return@mapNotNull null
        val liability = type == AccountType.CreditCard
        val total = ofType.sumOf { it.balance.minor }
        AccountGroupUi(
            title = title,
            liability = liability,
            total = Money(if (liability) -total else total),
            accounts = ofType.map { account ->
                AccountRowUi(
                    account = account,
                    displayBalance = if (liability) -account.balance else account.balance,
                    subtitle = listOfNotNull(account.institution.ifBlank { null }, account.mask?.let { "•• $it" })
                        .joinToString(" "),
                )
            },
        )
    }

    return AccountsUiState(
        netWorth = Money(assets - liabilities),
        assets = Money(assets),
        liabilities = Money(liabilities),
        history = history,
        change = Money(history.last() - history.first()),
        firstLabel = shortDate(transactionsWindowStart(today)),
        lastLabel = shortDate(today),
        groups = groups,
    )
}

/**
 * Истории остатков нет, поэтому идём назад от текущих: капитал на конец дня — это нынешний
 * минус всё, что случилось после. Транзакции удалённых счетов на остатки не влияют и не учитываются.
 */
fun netWorthHistory(today: LocalDate, accounts: List<Account>, transactions: List<Transaction>): List<Long> {
    val start = transactionsWindowStart(today)
    val accountIds = accounts.map { it.id }.toSet()
    val movedByDay = transactions
        .filter { it.accountId in accountIds && !it.date.isBefore(start) && !it.date.isAfter(today) }
        .groupBy { it.date }
        .mapValues { (_, list) -> list.sumOf { it.amount.minor } }
    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()

    val history = LongArray(days.size)
    var endOfDay = accounts.sumOf { it.balance.minor }
    for (index in days.indices.reversed()) {
        history[index] = endOfDay
        endOfDay -= movedByDay[days[index]] ?: 0L
    }
    return history.toList()
}
