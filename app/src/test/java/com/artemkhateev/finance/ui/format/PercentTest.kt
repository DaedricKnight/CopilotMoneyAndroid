package com.artemkhateev.finance.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PercentTest {

    @Test
    fun `share is counted in tenths of a percent`() {
        assertEquals(108, percentTenths(108, 1_000))
        assertEquals(-50, percentTenths(-5, 100))
        assertNull(percentTenths(5, 0))
    }

    @Test
    fun `percent text carries the sign only when asked`() {
        assertEquals("+10.8%", percentText(108))
        assertEquals("-0.5%", percentText(-5))
        assertEquals("0.0%", percentText(0))
        assertEquals("10.8%", percentText(-108, signed = false))
    }
}
