package com.artemkhateev.finance.feature.dashboard

import com.artemkhateev.finance.data.model.Category
import com.artemkhateev.finance.data.model.CategoryKind
import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringSchedule
import com.artemkhateev.finance.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

class DashboardStateTest {

    // В сентябре 30 дней: 15-е — ровно половина месяца.
    private val today = LocalDate.of(2026, 9, 15)
    private val food = Category("food", "Food", "🥑", CategoryTone.Green, monthlyBudget = Money.of(300.0))
    private val income = Category("income", "Income", "💰", CategoryTone.Green, kind = CategoryKind.Income)

    private fun tx(day: Int, amount: Double, category: String = "food", reviewed: Boolean = true) =
        Transaction("t$day/$amount", "acc", "Shop", Money.of(amount), LocalDate.of(2026, 9, day), category, reviewed = reviewed)

    @Test
    fun `spending counts only this month expenses`() {
        val lastMonth = Transaction("old", "acc", "Shop", Money.of(-99.0), LocalDate.of(2026, 8, 31), "food")
        val state = buildDashboard(
            today,
            listOf(food, income),
            listOf(tx(1, 2000.0, category = "income"), lastMonth, tx(2, -40.0), tx(10, -60.0)),
        )

        assertEquals(Money.of(200.0), state.spending.left)
        // Без регулярных платежей темп равномерный: к 15-му ушло бы 150, потрачено 100.
        assertEquals(Money.of(50.0), state.spending.paceDelta)
        assertEquals(15, state.spending.dailyCumulative.size)
        assertEquals(10_000L, state.spending.dailyCumulative.last())
        assertEquals(30, state.spending.pace.size)
    }

    @Test
    fun `category over its limit is marked over`() {
        val state = buildDashboard(today, listOf(food), listOf(tx(3, -350.0)))
        assertEquals(BudgetStatus.Over, state.budgets.single().status)
    }

    @Test
    fun `category ahead of the month pace is a warning`() {
        val state = buildDashboard(today, listOf(food), listOf(tx(3, -200.0)))
        assertEquals(BudgetStatus.Warning, state.budgets.single().status)
    }

    @Test
    fun `bill paid on its due day is not overspending`() {
        val rent = Category("rent", "Rent", "🔑", CategoryTone.Orange, monthlyBudget = Money.of(1000.0))
        val bill = Recurring("r", "Rent", "🏠", Money.of(1000.0), RecurringSchedule.Monthly(1), categoryId = "rent")
        val state = buildDashboard(today, listOf(rent), listOf(tx(1, -1000.0, category = "rent")), listOf(bill))

        assertEquals(BudgetStatus.OnTrack, state.budgets.single().status)
        assertEquals(Money.Zero, state.spending.paceDelta)
    }

    @Test
    fun `weekly bill counts on each of its days`() {
        // Понедельники сентября 2026 года — 7, 14, 21 и 28-е: четыре списания по €50 из бюджета €300.
        val box = Recurring("r", "Veggie box", "🥕", Money.of(50.0), RecurringSchedule.Weekly(DayOfWeek.MONDAY), categoryId = "food")
        val state = buildDashboard(today, listOf(food), listOf(tx(7, -50.0), tx(14, -50.0)), listOf(box))

        // 7-го: одно списание и седьмая часть оставшихся €100 за месяц.
        assertEquals(5_000L + 10_000L * 7 / 30, state.spending.pace[6])
        // К 15-му ожидалось €150 (два списания и половина остатка), потрачено €100.
        assertEquals(Money.of(50.0), state.spending.paceDelta)
        assertEquals(BudgetStatus.OnTrack, state.budgets.single().status)
    }

    @Test
    fun `yearly bill of another month leaves the pace even`() {
        val insurance = Recurring("r", "Insurance", "☂️", Money.of(250.0), RecurringSchedule.Yearly(Month.MARCH, 1), categoryId = "food")
        val state = buildDashboard(today, listOf(food), emptyList(), listOf(insurance))

        assertEquals(15_000L, state.spending.pace[14])
    }

    @Test
    fun `review shows the latest unreviewed day only`() {
        val state = buildDashboard(
            today,
            listOf(food),
            listOf(tx(15, -5.0, reviewed = false), tx(14, -7.0, reviewed = false)),
        )
        assertEquals("So far today", state.toReview?.label)
        assertEquals(1, state.toReview?.rows?.size)
        assertEquals(2, state.reviewCount)
    }
}
