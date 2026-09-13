package com.artemkhateev.finance.feature.importing

/**
 * Разбирает CSV в строки полей. Разделитель — запятая, точка с запятой или табуляция: берётся тот,
 * которого больше в заголовке. Поле в кавычках может содержать разделитель, перевод строки и "" как
 * кавычку. Пустые строки пропускаются.
 */
internal fun parseCsv(text: String): List<List<String>> {
    val source = text.removePrefix("﻿")
    val delimiter = detectDelimiter(source)
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false

    fun endRow() {
        row += field.toString()
        field.clear()
        if (row.any { it.isNotBlank() }) rows += row
        row = mutableListOf()
    }

    var i = 0
    while (i < source.length) {
        val c = source[i]
        when {
            quoted && c == '"' && source.getOrNull(i + 1) == '"' -> {
                field.append('"')
                i++
            }
            c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
            !quoted && c == delimiter -> {
                row += field.toString()
                field.clear()
            }
            !quoted && (c == '\n' || c == '\r') -> {
                if (c == '\r' && source.getOrNull(i + 1) == '\n') i++
                endRow()
            }
            else -> field.append(c)
        }
        i++
    }
    endRow()
    return rows
}

private fun detectDelimiter(text: String): Char {
    val header = text.lineSequence().firstOrNull().orEmpty()
    return listOf(',', ';', '\t').maxByOrNull { delimiter -> header.count { it == delimiter } } ?: ','
}
