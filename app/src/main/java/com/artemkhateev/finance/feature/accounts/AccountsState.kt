package com.artemkhateev.finance.feature.accounts

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountByName
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.Transaction
import com.artemkhateev.finance.data.model.TransactionPeriod
import com.artemkhateev.finance.data.model.portfolioValueByDay
import com.artemkhateev.finance.data.model.value
import com.artemkhateev.finance.ui.format.longDate
import com.artemkhateev.finance.ui.format.shortDate
import java.time.LocalDate

data class AccountRowUi(
    /** Счёт как он хранится: форма правки берёт данные отсюда. */
    val account: Account,
    /** Остаток для показа: у кредитных карт — сумма долга без минуса, у счёта с позициями — их стоимость. */
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
    /** Чистый капитал на конец каждого дня с конца дня перед периодом по сегодня, в центах. */
    val history: List<Long>,
    /** Изменение чистого капитала за период. */
    val change: Money,
    val firstLabel: String,
    val lastLabel: String,
    val groups: List<AccountGroupUi>,
    /** Счета, чья стоимость считается по позициям: остаток у них вручную не вводится. */
    val holdingAccountIds: Set<String>,
    /** Период графика: последние день, неделя, месяц… по сегодня. */
    val period: TransactionPeriod = TransactionPeriod.Month,
)

/** Порядок групп: сначала деньги, потом долги. */
private val GroupOrder = listOf(
    AccountType.Checking to "Cash & checking",
    AccountType.Savings to "Savings",
    AccountType.Investment to "Investments",
    AccountType.CreditCard to "Credit cards",
)

fun buildAccounts(
    today: LocalDate,
    accounts: List<Account>,
    transactions: List<Transaction>,
    holdings: List<Holding> = emptyList(),
    portfolioHistory: List<PortfolioSnapshot> = emptyList(),
    period: TransactionPeriod = TransactionPeriod.Month,
): AccountsUiState {
    // График начинается с конца дня перед периодом: изменение за день — это сегодня против вчерашнего вечера.
    val from = period.start(today).minusDays(1)
    // Счёт с позициями стоит столько, сколько его позиции, а не сколько когда-то ввели в остаток.
    val holdingsValue = holdings.groupBy { it.accountId }.mapValues { (_, list) -> list.sumOf { it.value.minor } }
    val holdingAccountIds = accounts.map { it.id }.filter { it in holdingsValue }.toSet()
    fun valueOf(account: Account): Long = holdingsValue[account.id] ?: account.balance.minor

    val assets = accounts.filter { it.type != AccountType.CreditCard }.sumOf { valueOf(it) }
    val liabilities = -accounts.filter { it.type == AccountType.CreditCard }.sumOf { valueOf(it) }

    val cashHistory = netWorthHistory(from, today, accounts.filterNot { it.id in holdingAccountIds }, transactions)
    // Прошлую стоимость позиций знают только снимки портфеля. Позиций не осталось — их история
    // уходит целиком, как у любого удалённого счёта.
    val history = if (holdingAccountIds.isEmpty()) {
        cashHistory
    } else {
        val holdingsNow = holdingAccountIds.sumOf { holdingsValue.getValue(it) }
        val invested = portfolioValueByDay(from, today, portfolioHistory, holdingsNow)
        cashHistory.zip(invested) { cash, investments -> cash + investments }
    }

    val groups = GroupOrder.mapNotNull { (type, title) ->
        val ofType = accounts.filter { it.type == type }.sortedWith(AccountByName)
        if (ofType.isEmpty()) return@mapNotNull null
        val liability = type == AccountType.CreditCard
        val total = ofType.sumOf { valueOf(it) }
        AccountGroupUi(
            title = title,
            liability = liability,
            total = Money(if (liability) -total else total),
            accounts = ofType.map { account ->
                AccountRowUi(
                    account = account,
                    displayBalance = Money(if (liability) -valueOf(account) else valueOf(account)),
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
        // Начало в прошлом году — с годом, иначе «since Sep 24» не отличить от сегодняшнего.
        firstLabel = if (from.year != today.year) longDate(from) else shortDate(from),
        lastLabel = shortDate(today),
        groups = groups,
        holdingAccountIds = holdingAccountIds,
        period = period,
    )
}

/**
 * Истории остатков нет, поэтому идём назад от текущих: капитал на конец дня с [start] по [today] — это нынешний
 * минус всё, что случилось после. Транзакции удалённых счетов на остатки не влияют и не учитываются.
 */
fun netWorthHistory(start: LocalDate, today: LocalDate, accounts: List<Account>, transactions: List<Transaction>): List<Long> {
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
