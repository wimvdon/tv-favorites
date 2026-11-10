package com.example.template.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class TapCounterTest {

    @Test
    fun `starts at zero`() {
        assertEquals(0, TapCounter().count)
    }

    @Test
    fun `tap adds one`() {
        assertEquals(2, TapCounter().tap().tap().count)
    }

    @Test
    fun `reset goes back to zero`() {
        assertEquals(0, TapCounter(5).reset().count)
    }

    @Test
    fun `tap stops at the maximum`() {
        assertEquals(TapCounter.MAX, TapCounter(TapCounter.MAX).tap().count)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative count is rejected`() {
        TapCounter(-1)
    }
}
