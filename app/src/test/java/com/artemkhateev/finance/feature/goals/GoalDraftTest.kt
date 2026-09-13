package com.artemkhateev.finance.feature.goals

import com.artemkhateev.finance.data.model.CategoryTone
import com.artemkhateev.finance.data.model.Goal
import com.artemkhateev.finance.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalDraftTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val draft = GoalDraft(
        name = " Trip to Japan ",
        emoji = "🗾",
        tone = CategoryTone.Pink,
        targetText = "4000",
        savedText = "250.5",
        targetDate = LocalDate.of(2027, 6, 1),
    )

    @Test
    fun `new goal starts its plan today`() {
        val expected = Goal("g1", "Trip to Japan", "🗾", CategoryTone.Pink, Money(400_000), LocalDate.of(2027, 6, 1), today)

        assertEquals(expected, draft.toGoal("g1", today))
        assertEquals(Money(25_050), draft.saved)
    }

    @Test
    fun `edited goal keeps its id and plan start`() {
        val saved = draft.toGoal("g1", LocalDate.of(2026, 1, 10))
        val edited = GoalDraft.from(saved).copy(targetText = "4500").toGoal("unused", today)

        assertEquals("g1", edited.id)
        assertEquals(LocalDate.of(2026, 1, 10), edited.startDate)
        assertEquals(Money(450_000), edited.target)
    }

    @Test
    fun `problems explain what is missing`() {
        assertEquals("Name the goal", draft.copy(name = " ").problem())
        assertEquals("Target should be more than zero", draft.copy(targetText = "0").problem())
        assertEquals("Saved amount should be an amount", draft.copy(savedText = "1.234").problem())
        assertNull(draft.problem())
    }

    @Test
    fun `withdrawal cannot take more than is saved`() {
        val withdraw = ContributionDraft(ContributionKind.Withdraw, "60")

        assertEquals("Only €50.00 is saved", withdraw.problem(Money(5_000)))
        assertNull(withdraw.problem(Money(6_000)))
        assertEquals(Money(-6_000), withdraw.toContribution("g1", today).amount)
        assertEquals(Money(6_000), withdraw.copy(kind = ContributionKind.Add).toContribution("g1", today).amount)
    }
}
