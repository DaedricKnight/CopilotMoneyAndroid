package com.artemkhateev.finance.feature.investments

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.cost
import com.artemkhateev.finance.data.model.portfolioValueByDay
import com.artemkhateev.finance.data.model.value
import com.artemkhateev.finance.ui.format.percentTenths
import com.artemkhateev.finance.ui.format.shortDate
import java.time.LocalDate

enum class InvestmentRange(val label: String, val months: Long) {
    OneMonth("1M", 1),
    ThreeMonths("3M", 3),
    OneYear("1Y", 12),
}

data class HoldingRowUi(
    val holding: Holding,
    val value: Money,
    val gain: Money,
    /** Прибыль к цене покупки в десятых процента. */
    val gainTenths: Int?,
)

data class AllocationSliceUi(val assetClass: AssetClass, val value: Money, val share: Float)

data class InvestmentsUiState(
    val range: InvestmentRange,
    val value: Money,
    /** Прибыль за всё время — к цене покупки. */
    val gain: Money,
    val gainTenths: Int?,
    /** Изменение стоимости с начала графика. */
    val rangeChange: Money,
    val rangeChangeTenths: Int?,
    /** Стоимость на конец каждого дня графика; последняя точка — сегодняшняя. */
    val history: List<Long>,
    val firstLabel: String,
    val lastLabel: String,
    /** Классы активов, крупные сначала. */
    val allocation: List<AllocationSliceUi>,
    /** Позиции, дорогие сначала. */
    val holdings: List<HoldingRowUi>,
)

fun buildInvestments(
    today: LocalDate,
    range: InvestmentRange,
    accounts: List<Account>,
    holdings: List<Holding>,
    snapshots: List<PortfolioSnapshot>,
): InvestmentsUiState {
    // Позиции удалённого счёта не считаются: в облаке их удаление могло и не дойти.
    val accountIds = accounts.map { it.id }.toSet()
    val counted = holdings.filter { it.accountId in accountIds }
    val value = counted.sumOf { it.value.minor }
    val cost = counted.sumOf { it.cost.minor }

    // График начинается с начала периода или с первого снимка, если история короче периода.
    val rangeStart = today.minusMonths(range.months)
    val firstRecorded = snapshots.filter { it.date.isBefore(today) }.minByOrNull { it.date.toEpochDay() }?.date
    val start = when {
        firstRecorded == null -> today
        firstRecorded.isAfter(rangeStart) -> firstRecorded
        else -> rangeStart
    }
    val daily = portfolioValueByDay(start, today, snapshots, value)
    // Одной точкой линию не нарисовать — без истории показываем ровную.
    val history = if (daily.size < 2) listOf(value, value) else daily
    val startValue = history.first()

    val rows = counted
        .map { HoldingRowUi(it, it.value, it.value - it.cost, percentTenths(it.value.minor - it.cost.minor, it.cost.minor)) }
        .sortedByDescending { it.value.minor }
    val allocation = counted
        .groupBy { it.assetClass }
        .map { (assetClass, ofClass) ->
            val classValue = ofClass.sumOf { it.value.minor }
            AllocationSliceUi(assetClass, Money(classValue), if (value == 0L) 0f else classValue.toFloat() / value)
        }
        .sortedByDescending { it.value.minor }

    return InvestmentsUiState(
        range = range,
        value = Money(value),
        gain = Money(value - cost),
        gainTenths = percentTenths(value - cost, cost),
        rangeChange = Money(value - startValue),
        rangeChangeTenths = percentTenths(value - startValue, startValue),
        history = history,
        firstLabel = shortDate(start),
        lastLabel = shortDate(today),
        allocation = allocation,
        holdings = rows,
    )
}
