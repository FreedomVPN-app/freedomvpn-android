package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FreedomHomeTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_795_000_000_000L

    @Test
    fun partialDayCountsAsOne() {
        assertEquals(1, freedomDaysLeft(now + 3 * 60 * 60 * 1000, now))
    }

    @Test
    fun wholeDaysAreExact() {
        assertEquals(28, freedomDaysLeft(now + 28 * day, now))
        assertEquals(29, freedomDaysLeft(now + 28 * day + 1, now))
    }

    @Test
    fun expiredOrNowIsZero() {
        assertEquals(0, freedomDaysLeft(now, now))
        assertEquals(0, freedomDaysLeft(now - day, now))
    }
}
