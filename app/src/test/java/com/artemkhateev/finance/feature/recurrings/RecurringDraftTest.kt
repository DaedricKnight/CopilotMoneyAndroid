package com.artemkhateev.finance.feature.recurrings

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import com.artemkhateev.finance.data.model.RecurringFrequency
import com.artemkhateev.finance.data.model.RecurringSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

class RecurringDraftTest {

    private val draft = RecurringDraft(name = " Internet ", emoji = "📶", amountText = "29.99", dayOfMonth = 25, categoryId = "utilities")

    @Test
    fun `complete draft becomes a recurring payment`() {
        assertEquals(
            Recurring("", "Internet", "📶", Money(2_999), RecurringSchedule.Monthly(25), categoryId = "utilities"),
            draft.toRecurring(),
        )
    }

    @Test
    fun `weekly and yearly drafts use their own day`() {
        val weekly = draft.copy(frequency = RecurringFrequency.Weekly, dayOfWeek = DayOfWeek.FRIDAY)
        val yearly = draft.copy(frequency = RecurringFrequency.Yearly, month = Month.MARCH, dayOfMonth = 14)

        assertEquals(RecurringSchedule.Weekly(DayOfWeek.FRIDAY), weekly.toRecurring().schedule)
        assertEquals(RecurringSchedule.Yearly(Month.MARCH, 14), yearly.toRecurring().schedule)
    }

    @Test
    fun `new form starts on today in every frequency`() {
        // 13 сентября 2026 года — воскресенье.
        val fresh = RecurringDraft.startingOn(LocalDate.of(2026, 9, 13))

        assertEquals(RecurringFrequency.Monthly, fresh.frequency)
        assertEquals(DayOfWeek.SUNDAY, fresh.dayOfWeek)
        assertEquals(13, fresh.dayOfMonth)
        assertEquals(Month.SEPTEMBER, fresh.month)
    }

    @Test
    fun `problems explain what is missing`() {
        assertEquals("Name the payment", draft.copy(name = " ").problem())
        assertEquals("Amount should be more than zero", draft.copy(amountText = "0").problem())
        assertEquals("Pick the day it's charged", draft.copy(dayOfMonth = null).problem())
        // 30 февраля не бывает.
        assertEquals(
            "Pick the day it's charged",
            draft.copy(frequency = RecurringFrequency.Yearly, month = Month.FEBRUARY, dayOfMonth = 30).problem(),
        )
        assertNull(draft.problem())
    }

    @Test
    fun `editing shows the saved values`() {
        val saved = draft.toRecurring().copy(id = "r1")
        val yearly = saved.copy(schedule = RecurringSchedule.Yearly(Month.MARCH, 14))

        assertEquals(draft.copy(id = "r1", name = "Internet"), RecurringDraft.from(saved))
        assertEquals(
            draft.copy(id = "r1", name = "Internet", frequency = RecurringFrequency.Yearly, month = Month.MARCH, dayOfMonth = 14),
            RecurringDraft.from(yearly),
        )
    }

    @Test
    fun `schedule reads as a sentence`() {
        assertEquals("Every Friday", RecurringSchedule.Weekly(DayOfWeek.FRIDAY).describe())
        assertEquals("Every month on the 31st, or on the last day of shorter months", RecurringSchedule.Monthly(31).describe())
        assertEquals("Every year on Mar 14", RecurringSchedule.Yearly(Month.MARCH, 14).describe())
        assertEquals("Every year on Feb 29, or Feb 28 when it isn't a leap year", RecurringSchedule.Yearly(Month.FEBRUARY, 29).describe())
    }
}
