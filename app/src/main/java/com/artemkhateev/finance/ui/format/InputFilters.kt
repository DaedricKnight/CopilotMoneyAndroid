package com.artemkhateev.finance.ui.format

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.QUANTITY_DECIMALS
import java.math.BigDecimal
import java.text.BreakIterator

private val amountPattern = Regex("""\d{1,9}([.,]\d{0,2})?""")
private val quantityPattern = Regex("""\d{1,9}([.,]\d{0,$QUANTITY_DECIMALS})?""")

/** "12", "12.5", "12,50" → центы. Всё, что не похоже на сумму, — null. */
fun parseAmount(text: String): Money? {
    val trimmed = text.trim()
    if (!amountPattern.matches(trimmed)) return null
    return Money(BigDecimal(trimmed.replace(',', '.')).movePointRight(2).toLong())
}

/** "42", "0.05", "1,5" → миллионные доли единицы. Всё, что не похоже на количество, — null. */
fun parseQuantity(text: String): Long? {
    val trimmed = text.trim()
    if (!quantityPattern.matches(trimmed)) return null
    return BigDecimal(trimmed.replace(',', '.')).movePointRight(QUANTITY_DECIMALS).toLong()
}

/** Фильтр ввода суммы: цифры, один разделитель (всегда точка) и не больше двух знаков после него. */
fun sanitizeAmountInput(raw: String): String = sanitizeDecimalInput(raw, maxDecimals = 2)

/** Фильтр ввода количества: как у суммы, но знаков после точки больше — у крипты доли бывают мелкими. */
fun sanitizeQuantityInput(raw: String): String = sanitizeDecimalInput(raw, maxDecimals = QUANTITY_DECIMALS)

private fun sanitizeDecimalInput(raw: String, maxDecimals: Int): String {
    val result = StringBuilder()
    var separatorSeen = false
    var decimals = 0
    for (ch in raw) {
        when {
            ch in '0'..'9' && !separatorSeen -> if (result.length < 9) result.append(ch)
            ch in '0'..'9' && decimals < maxDecimals -> {
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

/** Сумма для поля ввода без лишних нулей: 1200.00 → "1200", 45.90 → "45.9". */
fun amountInputText(amount: Money): String =
    BigDecimal.valueOf(amount.minor, 2).stripTrailingZeros().toPlainString()

/** Количество без лишних нулей — для поля ввода и для подписи позиции: 42, 0.05. */
fun quantityText(quantityMicros: Long): String =
    BigDecimal.valueOf(quantityMicros, QUANTITY_DECIMALS).stripTrailingZeros().toPlainString()

/**
 * Последний символ в том виде, как его видит человек: эмодзи из нескольких кодовых точек не рвётся.
 * Поле эмодзи держит один символ, и новый заменяет прежний.
 */
fun lastGrapheme(text: String): String {
    if (text.isEmpty()) return ""
    val boundaries = BreakIterator.getCharacterInstance()
    boundaries.setText(text)
    val end = boundaries.last()
    val start = boundaries.previous()
    return if (start == BreakIterator.DONE) text else text.substring(start, end)
}
