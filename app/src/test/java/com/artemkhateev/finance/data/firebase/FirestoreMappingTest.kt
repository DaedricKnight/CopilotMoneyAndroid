package com.artemkhateev.finance.data.firebase

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.AssetClass
import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Holding
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.PortfolioSnapshot
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringSchedule
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

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
        val bill = Recurring("r", "Rent", "🏠", Money(120_000), RecurringSchedule.Monthly(1), categoryId = "rent")
        // Firestore отдаёт любые целые числа как Long.
        assertEquals(bill, recurringFrom(bill.id, bill.toMap() + ("dayOfMonth" to 1L)))
    }

    @Test
    fun `weekly and yearly schedules survive a round trip`() {
        val weekly = Recurring("w", "Veggie box", "🥕", Money(1_850), RecurringSchedule.Weekly(DayOfWeek.SATURDAY))
        val yearly = Recurring("y", "Cloud storage", "☁️", Money(9_999), RecurringSchedule.Yearly(Month.FEBRUARY, 29))

        assertEquals(weekly, recurringFrom(weekly.id, weekly.toMap()))
        assertEquals(yearly, recurringFrom(yearly.id, yearly.toMap()))
    }

    @Test
    fun `recurring saved before frequencies is monthly`() {
        val legacy = mapOf("name" to "Rent", "emoji" to "🏠", "amount" to 120_000L, "dayOfMonth" to 1L)
        assertEquals(RecurringSchedule.Monthly(1), recurringFrom("r", legacy)?.schedule)
    }

    @Test
    fun `unknown frequency skips the document`() {
        val future = mapOf("name" to "Rent", "amount" to 120_000L, "dayOfMonth" to 1L, "frequency" to "Biweekly")
        assertNull(recurringFrom("r", future))
    }

    @Test
    fun `holding keeps a fractional quantity and prices`() {
        val holding = Holding(
            "h1", "brokerage", "BTC", "Bitcoin", AssetClass.Crypto,
            quantityMicros = 50_000, costPerUnit = Money(3_850_000), price = Money(5_420_000),
            priceUpdated = LocalDate.of(2026, 9, 12),
        )
        assertEquals(holding, holdingFrom(holding.id, holding.toMap()))
    }

    @Test
    fun `holding without a name shows its ticker`() {
        val data = mapOf(
            "symbol" to "VWCE",
            "quantityMicros" to 1_000_000L,
            "costPerUnit" to 9_810L,
            "price" to 12_135L,
            "priceUpdated" to "2026-09-12",
        )
        assertEquals("VWCE", holdingFrom("h", data)?.name)
    }

    @Test
    fun `snapshot takes its date from the document id when the field is missing`() {
        assertEquals(
            PortfolioSnapshot(LocalDate.of(2026, 9, 12), Money(100_000)),
            snapshotFrom("2026-09-12", mapOf("value" to 100_000L)),
        )
    }

    @Test
    fun `goal keeps an optional target date`() {
        val dated = Goal("g1", "Trip", "🗾", CategoryTone.Pink, Money(400_000), LocalDate.of(2027, 6, 1), LocalDate.of(2026, 4, 1))
        val open = dated.copy(targetDate = null)

        assertEquals(dated, goalFrom(dated.id, dated.toMap()))
        assertEquals(open, goalFrom(open.id, open.toMap()))
    }

    @Test
    fun `withdrawal keeps its sign`() {
        val entry = GoalContribution("gc1", "g1", Money(-5_000), LocalDate.of(2026, 9, 12))
        assertEquals(entry, contributionFrom(entry.id, entry.toMap()))
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
