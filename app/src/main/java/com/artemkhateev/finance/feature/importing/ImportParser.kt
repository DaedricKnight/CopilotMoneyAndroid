package com.artemkhateev.finance.feature.importing

import com.artemkhateev.finance.data.model.Money
import java.math.BigDecimal
import java.time.LocalDate

/** Строка файла, из которой получится транзакция. */
data class ImportRow(
    val date: LocalDate,
    /** Со знаком: расход отрицательный. */
    val amount: Money,
    val merchant: String,
    val category: String?,
    val account: String?,
)

sealed interface ParsedFile {
    data class Rows(val rows: List<ImportRow>, val unreadable: Int) : ParsedFile
    data class Failure(val message: String) : ParsedFile
}

// Колонки узнаются по заголовку без учёта регистра. Названия — как в выгрузках популярных приложений.
private val DateColumns = listOf("date", "datetime", "date time", "transaction date", "time")
private val AmountColumns = listOf("amount", "sum", "value")
private val CategoryColumns = listOf("category", "category name")
private val AccountColumns = listOf("account", "account name", "wallet")
private val MerchantColumns = listOf("merchant", "payee", "description", "title", "name", "note", "notes")
private val TypeColumns = listOf("type")

/**
 * Разбирает выгрузку записей. Обязательны дата и сумма; знак суммы берётся из неё самой или из колонки
 * type (Expense/Income). Название — первое непустое из merchant, payee, description, note…, иначе категория.
 */
fun parseRecords(text: String): ParsedFile {
    val table = parseCsv(text)
    if (table.size < 2) return ParsedFile.Failure("The file has no records")
    val header = table.first().map { it.trim().lowercase() }
    fun column(names: List<String>): Int? = names.firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } }

    val dateColumn = column(DateColumns) ?: return ParsedFile.Failure("The file needs a date column")
    val amountColumn = column(AmountColumns) ?: return ParsedFile.Failure("The file needs an amount column")
    val categoryColumn = column(CategoryColumns)
    val accountColumn = column(AccountColumns)
    val typeColumn = column(TypeColumns)
    val merchantColumns = MerchantColumns.mapNotNull { name -> header.indexOf(name).takeIf { it >= 0 } }

    var unreadable = 0
    val rows = table.drop(1).mapNotNull { cells ->
        fun cell(index: Int?): String = index?.let { cells.getOrNull(it) }?.trim().orEmpty()

        val date = parseDate(cell(dateColumn))
        val amount = parseSignedAmount(cell(amountColumn))
        if (date == null || amount == null || amount.minor == 0L) {
            unreadable++
            return@mapNotNull null
        }
        val type = cell(typeColumn).lowercase()
        val signed = when {
            type.startsWith("expense") && amount.minor > 0 -> -amount
            type.startsWith("income") && amount.minor < 0 -> -amount
            else -> amount
        }
        val category = cell(categoryColumn).ifBlank { null }
        ImportRow(
            date = date,
            amount = signed,
            merchant = merchantColumns.map { cell(it) }.firstOrNull { it.isNotBlank() } ?: category ?: "Imported",
            category = category,
            account = cell(accountColumn).ifBlank { null },
        )
    }
    if (rows.isEmpty()) return ParsedFile.Failure("No records could be read")
    return ParsedFile.Rows(rows, unreadable)
}

private val IsoDate = Regex("""^(\d{4})-(\d{2})-(\d{2})""")
private val DottedDate = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})""")

/** «2026-09-03», «2026-09-03 18:25:43», «2026-09-03T10:00:00Z», «03.09.2026». */
internal fun parseDate(text: String): LocalDate? = runCatching {
    IsoDate.find(text)?.let { match ->
        val (year, month, day) = match.destructured
        return@runCatching LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    }
    DottedDate.find(text)?.let { match ->
        val (day, month, year) = match.destructured
        return@runCatching LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    }
    null
}.getOrNull()

/**
 * «-12.40», «−12,40», «1 234,50 €», «-1,234.56» → центы со знаком. Десятичный разделитель — последняя
 * точка или запятая, если после неё одна-две цифры; иначе разделители считаются разрядными.
 */
internal fun parseSignedAmount(text: String): Money? = runCatching {
    val kept = text.replace('−', '-').filter { it.isDigit() || it == '-' || it == '.' || it == ',' }
    val negative = '-' in kept
    val cleaned = kept.replace("-", "")
    if (cleaned.none { it.isDigit() }) return@runCatching null
    val separator = maxOf(cleaned.lastIndexOf('.'), cleaned.lastIndexOf(','))
    val decimals = if (separator >= 0) cleaned.length - separator - 1 else 0
    val (integerPart, fraction) = if (separator >= 0 && decimals in 1..2) {
        cleaned.substring(0, separator) to cleaned.substring(separator + 1)
    } else {
        cleaned to ""
    }
    val integerDigits = integerPart.filter { it.isDigit() }.ifEmpty { "0" }
    val cents = BigDecimal("$integerDigits.${fraction.padEnd(2, '0')}").movePointRight(2).longValueExact()
    Money(if (negative) -cents else cents)
}.getOrNull()
