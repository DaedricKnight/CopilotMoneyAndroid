package com.artemkhateev.finance.feature.goals

import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.GoalContribution
import com.artemkhateev.finance.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalsStateTest {

    private val today = LocalDate.of(2026, 9, 15)

    private fun goal(id: String, target: Long, targetDate: LocalDate?, name: String = id) =
        Goal(id, name, "🎯", CategoryTone.Teal, Money(target), targetDate, startDate = LocalDate.of(2026, 1, 1))

    private fun put(goalId: String, cents: Long, date: LocalDate = LocalDate.of(2026, 9, 1)) =
        GoalContribution("c-$goalId-$cents-$date", goalId, Money(cents), date)

    // План от 1 января до 1 января: к 15 сентября прошло 257 дней из 365, по плану отложено €704.10.
    private val laptop = goal("laptop", 100_000, LocalDate.of(2027, 1, 1))

    @Test
    fun `saved is the sum of contributions and withdrawals`() {
        val row = buildGoals(today, listOf(goal("fund", 100_000, null)), listOf(put("fund", 30_000), put("fund", -5_000)))
            .active.single()

        assertEquals(Money(25_000), row.saved)
        assertEquals(Money(75_000), row.left)
        assertEquals(0.25f, row.progress, 0.0001f)
        assertEquals(GoalStatus.Open, row.status)
        assertNull(row.monthlyNeeded)
    }

    @Test
    fun `goal ahead of an even plan is on track`() {
        val row = buildGoals(today, listOf(laptop), listOf(put("laptop", 80_000))).active.single()

        assertEquals(GoalStatus.OnTrack, row.status)
        assertEquals(Money(5_000), row.monthlyNeeded) // €200 на 4 месяца до 1 января
        assertNull(row.behindBy)
    }

    @Test
    fun `goal short of an even plan is behind`() {
        val row = buildGoals(today, listOf(laptop), listOf(put("laptop", 50_000))).active.single()

        assertEquals(GoalStatus.Behind, row.status)
        assertEquals(Money(20_410), row.behindBy)
        assertEquals(Money(12_500), row.monthlyNeeded)
    }

    @Test
    fun `monthly amount rounds up to the cent`() {
        val gift = goal("gift", 10_000, LocalDate.of(2026, 12, 15))
        assertEquals(Money(3_334), buildGoals(today, listOf(gift), emptyList()).active.single().monthlyNeeded)
    }

    @Test
    fun `months left round up and never drop below one`() {
        assertEquals(4L, monthsLeft(today, LocalDate.of(2026, 12, 31)))
        assertEquals(3L, monthsLeft(today, LocalDate.of(2026, 12, 15)))
        assertEquals(1L, monthsLeft(today, LocalDate.of(2026, 9, 20)))
        assertEquals(1L, monthsLeft(today, today))
    }

    @Test
    fun `reached goals move to completed and passed dates are overdue`() {
        val goals = listOf(goal("done", 10_000, LocalDate.of(2026, 8, 1)), goal("late", 10_000, LocalDate.of(2026, 9, 1)))
        val state = buildGoals(today, goals, listOf(put("done", 10_000, LocalDate.of(2026, 7, 1))))

        assertEquals(GoalStatus.Done, state.completed.single().status)
        assertEquals("late", state.active.single().goal.id)
        assertEquals(GoalStatus.Overdue, state.active.single().status)
        assertNull(state.active.single().monthlyNeeded)
    }

    @Test
    fun `summary counts goals in progress and this month's contributions`() {
        val goals = listOf(
            goal("trip", 100_000, LocalDate.of(2027, 3, 15)),
            goal("fund", 500_000, null),
            goal("gift", 10_000, LocalDate.of(2026, 12, 15)),
            goal("done", 5_000, null),
        )
        val contributions = listOf(
            put("trip", 40_000, LocalDate.of(2026, 8, 20)),
            put("trip", 10_000, LocalDate.of(2026, 9, 2)),
            put("fund", 20_000, LocalDate.of(2026, 9, 10)),
            put("done", 5_000, LocalDate.of(2026, 9, 12)),
            put("gone", 7_000, LocalDate.of(2026, 9, 12)),
        )
        val state = buildGoals(today, goals, contributions)

        assertEquals(listOf("gift", "trip", "fund"), state.active.map { it.goal.id })
        assertEquals(Money(70_000), state.saved)
        assertEquals(Money(610_000), state.target)
        assertEquals(Money(35_000), state.savedThisMonth)
        // Поездка: €500 на 6 месяцев, подарок: €100 на 3.
        assertEquals(Money(8_334 + 3_334), state.monthlyNeeded)
    }
}
