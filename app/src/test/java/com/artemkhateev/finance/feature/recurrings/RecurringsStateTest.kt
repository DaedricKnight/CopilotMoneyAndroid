package com.artemkhateev.finance.feature.recurrings

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringSchedule
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

class RecurringsStateTest {

    private val today = LocalDate.of(2026, 9, 12)
    private val rent = Recurring("r-rent", "Rent", "🏠", Money(120_000), RecurringSchedule.Monthly(1), categoryId = "rent")
    private val gym = Recurring("r-gym", "Gym", "🏋️", Money(3_900), RecurringSchedule.Monthly(20), categoryId = "subscriptions")

    private fun spend(id: String, merchant: String, cents: Long, date: LocalDate, category: String? = null) =
        Transaction(id, "acc", merchant, Money(-cents), date, category)

    @Test
    fun `bill with a matching payment this month is paid`() {
        val state = buildRecurrings(today, listOf(gym, rent), emptyList(), listOf(spend("t1", "rent", 120_000, today.withDayOfMonth(1))))

        assertEquals(listOf("r-rent", "r-gym"), state.thisMonth.map { it.recurring.id })
        assertEquals(listOf(true, false), state.thisMonth.map { it.paid })
        assertEquals(Money(120_000), state.paidSoFar)
        assertEquals(Money(3_900), state.leftToPay)
        assertEquals("1st", state.thisMonth.first().dueLabel)
    }

    @Test
    fun `same amount in the same category also counts as payment`() {
        val payment = spend("t1", "FitClub", 3_900, today.withDayOfMonth(10), category = "subscriptions")
        assertTrue(buildRecurrings(today, listOf(gym), emptyList(), listOf(payment)).thisMonth.single().paid)
    }

    @Test
    fun `one payment closes only one bill`() {
        val twin = rent.copy(id = "r-rent-2", schedule = RecurringSchedule.Monthly(2))
        val state = buildRecurrings(today, listOf(rent, twin), emptyList(), listOf(spend("t1", "Rent", 120_000, today.withDayOfMonth(1))))
        assertEquals(1, state.thisMonth.count { it.paid })
    }

    @Test
    fun `payment past the end of a short month is due on its last day`() {
        val internet = Recurring("r-net", "Internet", "📶", Money(2_999), RecurringSchedule.Monthly(31))
        assertEquals("30th", buildRecurrings(today, listOf(internet), emptyList(), emptyList()).thisMonth.single().dueLabel)
    }

    @Test
    fun `last month payment does not count`() {
        val august = spend("t1", "Rent", 120_000, LocalDate.of(2026, 8, 1))
        assertFalse(buildRecurrings(today, listOf(rent), emptyList(), listOf(august)).thisMonth.single().paid)
    }

    @Test
    fun `weekly payment counts every charge of the month`() {
        // Субботы сентября 2026 года — 5, 12, 19 и 26-е.
        val veggies = Recurring("r-veg", "Veggie box", "🥕", Money(1_850), RecurringSchedule.Weekly(DayOfWeek.SATURDAY), categoryId = "groceries")
        val payments = listOf(
            spend("t1", "Veggie box", 1_850, LocalDate.of(2026, 9, 5)),
            spend("t2", "Veggie box", 1_850, LocalDate.of(2026, 9, 12)),
        )
        val state = buildRecurrings(today, listOf(veggies), emptyList(), payments)
        val tile = state.thisMonth.single()

        assertEquals(4, tile.dueCount)
        assertEquals(2, tile.paidCount)
        assertFalse(tile.paid)
        assertEquals("Sat · 2/4", tile.dueLabel)
        assertEquals(Money(3_700), state.paidSoFar)
        assertEquals(Money(3_700), state.leftToPay)
    }

    @Test
    fun `yearly payment of another month waits in later`() {
        val cloud = Recurring("r-cloud", "Cloud storage", "☁️", Money(9_999), RecurringSchedule.Yearly(Month.MARCH, 14))
        val domain = cloud.copy(id = "r-domain", name = "Domain", schedule = RecurringSchedule.Yearly(Month.SEPTEMBER, 20))
        val state = buildRecurrings(today, listOf(cloud, domain), emptyList(), emptyList())

        assertEquals(listOf("r-domain"), state.thisMonth.map { it.recurring.id })
        assertEquals("Sep 20", state.thisMonth.single().dueLabel)
        assertEquals(LocalDate.of(2027, 3, 14), state.later.single().nextDue)
        assertEquals("Mar 14, 2027", state.later.single().dueLabel)
        assertEquals(Money(9_999), state.leftToPay)
    }
}
