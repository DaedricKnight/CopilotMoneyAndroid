package com.artemkhateev.finance.feature.investments

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.holdingValue
import com.artemkhateev.finance.ui.format.amountInputText
import com.artemkhateev.finance.ui.format.parseAmount
import com.artemkhateev.finance.ui.format.parseQuantity
import com.artemkhateev.finance.ui.format.percentTenths
import com.artemkhateev.finance.ui.format.quantityText
import java.time.LocalDate

/** Форма позиции. Числа хранятся текстом — так, как их набирают. */
data class HoldingDraft(
    /** Пустой — новая позиция: id выдаст репозиторий. */
    val id: String = "",
    val symbol: String = "",
    val name: String = "",
    val assetClass: AssetClass = AssetClass.Fund,
    val quantityText: String = "",
    val priceText: String = "",
    /** Пустая средняя цена — куплено по текущей, прибыли нет. */
    val costText: String = "",
    val accountId: String? = null,
) {
    val quantityMicros: Long? get() = parseQuantity(quantityText)?.takeIf { it > 0 }
    val price: Money? get() = parseAmount(priceText)?.takeIf { it.minor > 0 }
    val cost: Money? get() = if (costText.isBlank()) price else parseAmount(costText)

    /** Стоимость по введённым цифрам — подсказка в форме; null, пока цифр не хватает. */
    val previewValue: Money?
        get() {
            val quantity = quantityMicros ?: return null
            return holdingValue(quantity, price ?: return null)
        }

    /** Прибыль к цене покупки по введённым цифрам. */
    val previewGain: Money?
        get() {
            val value = previewValue ?: return null
            return value - (previewCost ?: return null)
        }

    /** Прибыль в десятых процента. */
    val previewGainTenths: Int?
        get() {
            val gain = previewGain ?: return null
            return percentTenths(gain.minor, (previewCost ?: return null).minor)
        }

    private val previewCost: Money?
        get() {
            val quantity = quantityMicros ?: return null
            return holdingValue(quantity, cost ?: return null)
        }

    /** Почему сохранить нельзя; null — можно. */
    fun problem(): String? = when {
        symbol.isBlank() -> "Enter a ticker or a short name"
        quantityMicros == null -> "Quantity should be more than zero"
        price == null -> "Price should be more than zero"
        cost == null -> "Average cost should be an amount"
        else -> null
    }

    fun toHolding(today: LocalDate, accountId: String, previous: Holding?): Holding {
        val quantity = requireNotNull(quantityMicros) { "Holding draft is not valid: ${problem()}" }
        val unitPrice = requireNotNull(price) { "Holding draft is not valid: ${problem()}" }
        val cleanSymbol = symbol.trim().uppercase()
        return Holding(
            id = id,
            accountId = accountId,
            symbol = cleanSymbol,
            name = name.trim().ifBlank { cleanSymbol },
            assetClass = assetClass,
            quantityMicros = quantity,
            costPerUnit = cost ?: unitPrice,
            price = unitPrice,
            // Дату цены сдвигаем, только когда цену правда поменяли.
            priceUpdated = if (previous != null && previous.price == unitPrice) previous.priceUpdated else today,
        )
    }

    companion object {
        fun from(holding: Holding) = HoldingDraft(
            id = holding.id,
            symbol = holding.symbol,
            name = holding.name,
            assetClass = holding.assetClass,
            quantityText = quantityText(holding.quantityMicros),
            priceText = amountInputText(holding.price),
            costText = amountInputText(holding.costPerUnit),
            accountId = holding.accountId,
        )
    }
}

/** «Brokerage», а если имя занято — «Brokerage 2», «Brokerage 3»…: форма счёта не пропускает одинаковые имена. */
fun freeAccountName(base: String, accounts: List<Account>): String =
    generateSequence(1) { it + 1 }
        .map { if (it == 1) base else "$base $it" }
        .first { name -> accounts.none { it.name.equals(name, ignoreCase = true) } }
