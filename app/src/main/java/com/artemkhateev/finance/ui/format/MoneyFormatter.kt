package com.artemkhateev.finance.ui.format

import com.artemkhateev.finance.data.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class SignStyle { None, Negative, Always }

/** Части суммы по отдельности: в дизайне символ валюты мельче цифр и приподнят. */
data class MoneyParts(val sign: String, val symbol: String, val digits: String) {
    override fun toString() = sign + symbol + digits
}

object MoneyFormatter {
    /** Валюта отображения — одна на приложение, пока нет мультивалютных счетов. */
    const val CURRENCY_SYMBOL = "€"

    fun parts(amount: Money, cents: Boolean = true, sign: SignStyle = SignStyle.None): MoneyParts {
        val format = DecimalFormat(if (cents) "#,##0.00" else "#,##0", DecimalFormatSymbols(Locale.US))
        format.roundingMode = RoundingMode.HALF_UP
        val digits = format.format(BigDecimal.valueOf(kotlin.math.abs(amount.minor), 2))
        val signText = when (sign) {
            SignStyle.None -> ""
            SignStyle.Negative -> if (amount.minor < 0) "-" else ""
            SignStyle.Always -> if (amount.minor < 0) "-" else "+"
        }
        return MoneyParts(signText, CURRENCY_SYMBOL, digits)
    }

    fun format(amount: Money, cents: Boolean = true, sign: SignStyle = SignStyle.None): String =
        parts(amount, cents, sign).toString()
}
