package com.artemkhateev.finance.feature.accounts

import com.artemkhateev.finance.data.model.Account
import com.artemkhateev.finance.data.model.AccountType
import com.artemkhateev.finance.data.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDraftTest {

    @Test
    fun `credit card debt is stored as a negative balance`() {
        val account = AccountDraft(name = "Visa", type = AccountType.CreditCard, balanceText = "642.37").toAccount()
        assertEquals(Money(-64_237), account.balance)
    }

    @Test
    fun `empty balance means zero and blank digits are dropped`() {
        val account = AccountDraft(name = "Cash").toAccount()
        assertEquals(Money.Zero, account.balance)
        assertNull(account.mask)
    }

    @Test
    fun `name must be unique ignoring case`() {
        val cash = Account("cash", "Cash", "", AccountType.Checking, Money.Zero)

        assertEquals("An account with this name already exists", AccountDraft(name = "cash").problem(listOf(cash)))
        // Своё же имя при правке — не дубликат.
        assertNull(AccountDraft.from(cash).problem(listOf(cash)))
    }

    @Test
    fun `editing without changes keeps the account`() {
        val card = Account("card", "Visa", "Demo Card", AccountType.CreditCard, Money(-64_237), mask = "4412")
        assertEquals(card, AccountDraft.from(card).toAccount())
    }
}
