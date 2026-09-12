package com.artemkhateev.finance.feature.transactions

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TransactionDraftTest {

    private val day = LocalDate.of(2026, 9, 12)

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
    fun `expense is stored as a negative amount`() {
        val transaction = TransactionDraft(amountText = "15.99", merchant = "  Bakery ", accountId = "cash", date = day)
            .toTransaction()
        assertEquals(Money(-1_599), transaction?.amount)
        assertEquals("Bakery", transaction?.merchant)
        assertNull(transaction?.note)
    }

    @Test
    fun `draft without merchant or with zero amount is not valid`() {
        assertFalse(TransactionDraft(amountText = "10", accountId = "cash", date = day).isValid)
        assertFalse(TransactionDraft(amountText = "0", merchant = "Shop", accountId = "cash", date = day).isValid)
    }

    @Test
    fun `editing without changes gives back the same transaction`() {
        val income = Transaction("m-1", "checking", "Salary", Money(485_000), day, "income", note = "September", reviewed = true)
        assertEquals(income, TransactionDraft.from(income).toTransaction())
    }
}
