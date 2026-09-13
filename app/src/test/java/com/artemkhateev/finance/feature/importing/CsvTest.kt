package com.artemkhateev.finance.feature.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `quoted fields keep delimiters and doubled quotes`() {
        val csv = "date,note\n2026-08-18,\"Lunch, office \"\"Mare\"\"\"\n"
        assertEquals(
            listOf(listOf("date", "note"), listOf("2026-08-18", "Lunch, office \"Mare\"")),
            parseCsv(csv),
        )
    }

    @Test
    fun `semicolon files with a byte order mark and windows line breaks`() {
        assertEquals(
            listOf(listOf("date", "amount"), listOf("2026-08-21", "-42,10")),
            parseCsv("﻿date;amount\r\n2026-08-21;-42,10\r\n\r\n"),
        )
    }
}
