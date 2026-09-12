package com.artemkhateev.finance.ui.format

import com.artemkhateev.finance.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `groups thousands and keeps cents`() {
        assertEquals("€4,120.50", MoneyFormatter.format(Money.of(4120.5)))
    }

    @Test
    fun `rounds half up when cents are hidden`() {
        assertEquals("€478", MoneyFormatter.format(Money(47_750), cents = false))
    }

    @Test
    fun `explicit sign marks both directions`() {
        assertEquals("+€15.00", MoneyFormatter.format(Money(1_500), sign = SignStyle.Always))
        assertEquals("-€75.00", MoneyFormatter.format(Money(-7_500), sign = SignStyle.Always))
    }

    @Test
    fun `compact labels shorten thousands`() {
        assertEquals("€3K", MoneyFormatter.compact(Money(300_000)))
        assertEquals("€1.2K", MoneyFormatter.compact(Money(123_456)))
        assertEquals("€10K", MoneyFormatter.compact(Money(1_000_000)))
        assertEquals("-€762", MoneyFormatter.compact(Money(-76_200)))
        assertEquals("€0", MoneyFormatter.compact(Money.Zero))
    }
}
