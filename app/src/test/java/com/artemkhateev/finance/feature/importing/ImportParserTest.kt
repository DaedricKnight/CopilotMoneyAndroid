package com.artemkhateev.finance.feature.importing

import com.artemkhateev.finance.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ImportParserTest {

    @Test
    fun `export with a type column gets signs from it`() {
        val csv = """
            account;category;currency;amount;type;note;date
            Cash;Groceries;EUR;12.40;Expenses;;2026-08-21 18:25:43
            Cash;Salary;EUR;2000;Income;August;2026-08-01 09:00:00
        """.trimIndent()
        val rows = (parseRecords(csv) as ParsedFile.Rows).rows

        assertEquals(ImportRow(LocalDate.of(2026, 8, 21), Money(-1_240), "Groceries", "Groceries", "Cash"), rows[0])
        assertEquals(ImportRow(LocalDate.of(2026, 8, 1), Money(200_000), "August", "Salary", "Cash"), rows[1])
    }

    @Test
    fun `amounts in local formats`() {
        assertEquals(Money(-1_240), parseSignedAmount("-12,40"))
        assertEquals(Money(123_450), parseSignedAmount("1 234,50 €"))
        assertEquals(Money(-123_456), parseSignedAmount("−1,234.56"))
        assertEquals(Money(123_400), parseSignedAmount("1,234"))
        assertNull(parseSignedAmount("abc"))
    }

    @Test
    fun `dates in iso and dotted formats`() {
        assertEquals(LocalDate.of(2026, 8, 14), parseDate("2026-08-14T10:00:00Z"))
        assertEquals(LocalDate.of(2026, 8, 14), parseDate("14.08.2026"))
        assertNull(parseDate("Aug 14"))
    }

    @Test
    fun `file without an amount column is refused`() {
        assertEquals(ParsedFile.Failure("The file needs an amount column"), parseRecords("date,note\n2026-08-14,Lunch"))
    }

    @Test
    fun `unreadable rows are counted, not imported`() {
        val parsed = parseRecords("date,amount\n2026-08-14,-5\nsoon,-6\n2026-08-15,0") as ParsedFile.Rows

        assertEquals(1, parsed.rows.size)
        assertEquals(2, parsed.unreadable)
    }
}
