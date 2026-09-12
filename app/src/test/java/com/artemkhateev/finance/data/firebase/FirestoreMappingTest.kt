package com.artemkhateev.finance.data.firebase

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FirestoreMappingTest {

    @Test
    fun `category survives a round trip`() {
        val category = Category("food", "Food", "🥑", CategoryTone.Green, monthlyBudget = Money(30_000))
        assertEquals(category, categoryFrom(category.id, category.toMap()))
    }

    @Test
    fun `account survives a round trip`() {
        val account = Account("card", "Credit Card", "Demo Card", AccountType.CreditCard, Money(-64_237), mask = "4412")
        assertEquals(account, accountFrom(account.id, account.toMap()))
    }

    @Test
    fun `transaction keeps date and cents`() {
        val transaction = Transaction("t1", "card", "Shop", Money(-1_599), LocalDate.of(2026, 9, 12), "food", reviewed = true)
        assertEquals(transaction, transactionFrom(transaction.id, transaction.toMap()))
    }

    @Test
    fun `recurring reads a day stored as long`() {
        val bill = Recurring("r", "Rent", "🏠", Money(120_000), dayOfMonth = 1, categoryId = "rent")
        // Firestore отдаёт любые целые числа как Long.
        assertEquals(bill, recurringFrom(bill.id, bill.toMap() + ("dayOfMonth" to 1L)))
    }

    @Test
    fun `unknown tone falls back instead of dropping the category`() {
        assertEquals(CategoryTone.Gray, categoryFrom("x", mapOf("name" to "X", "tone" to "Neon"))?.tone)
    }

    @Test
    fun `document without required fields is skipped`() {
        assertNull(transactionFrom("t", mapOf("merchant" to "Shop")))
    }
}
