package com.artemkhateev.finance.feature.recurrings

import com.artemkhateev.finance.data.model.Money
import com.artemkhateev.finance.data.model.Recurring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurringDraftTest {

    private val draft = RecurringDraft(name = " Internet ", emoji = "📶", amountText = "29.99", dayOfMonth = 25, categoryId = "utilities")

    @Test
    fun `complete draft becomes a recurring payment`() {
        assertEquals(Recurring("", "Internet", "📶", Money(2_999), dayOfMonth = 25, categoryId = "utilities"), draft.toRecurring())
    }

    @Test
    fun `problems explain what is missing`() {
        assertEquals("Name the payment", draft.copy(name = " ").problem())
        assertEquals("Amount should be more than zero", draft.copy(amountText = "0").problem())
        assertEquals("Pick the day it's charged", draft.copy(dayOfMonth = null).problem())
        assertNull(draft.problem())
    }

    @Test
    fun `editing shows the saved values`() {
        val saved = draft.toRecurring().copy(id = "r1")
        assertEquals(draft.copy(id = "r1", name = "Internet"), RecurringDraft.from(saved))
    }
}
