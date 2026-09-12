package com.artemkhateev.finance.ui.format

import com.artemkhateev.finance.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputFiltersTest {

    @Test
    fun `amount accepts comma and dot`() {
        assertEquals(Money(1_250), parseAmount("12,50"))
        assertEquals(Money(1_250), parseAmount("12.5"))
        assertEquals(Money(1_200), parseAmount("12."))
    }

    @Test
    fun `malformed amount is rejected`() {
        assertNull(parseAmount(""))
        assertNull(parseAmount("abc"))
        assertNull(parseAmount("1.234"))
    }

    @Test
    fun `input keeps digits, one separator and two decimals`() {
        assertEquals("12.34", sanitizeAmountInput("1a2,345"))
        assertEquals("12.34", sanitizeAmountInput("12.3.4"))
        assertEquals("0.5", sanitizeAmountInput(",5"))
    }

    @Test
    fun `amount for editing drops trailing zeros`() {
        assertEquals("1200", amountInputText(Money(120_000)))
        assertEquals("45.9", amountInputText(Money(4_590)))
        assertEquals("0", amountInputText(Money.Zero))
    }

    @Test
    fun `quantity keeps fractions down to a millionth`() {
        assertEquals(50_000L, parseQuantity("0.05"))
        assertEquals(1_500_000L, parseQuantity("1,5"))
        assertNull(parseQuantity("0.0000001"))
        assertEquals("0.123456", sanitizeQuantityInput("0.1234567"))
    }

    @Test
    fun `quantity for editing drops trailing zeros`() {
        assertEquals("42", quantityText(42_000_000))
        assertEquals("0.05", quantityText(50_000))
        assertEquals("850", quantityText(850_000_000))
    }

    @Test
    fun `emoji field keeps only the last typed symbol`() {
        assertEquals("🍔", lastGrapheme("🥑🍔"))
        assertEquals("a", lastGrapheme("a"))
        assertEquals("", lastGrapheme(""))
    }
}
