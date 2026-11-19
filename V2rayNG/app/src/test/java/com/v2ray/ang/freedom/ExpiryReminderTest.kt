package com.v2ray.ang.freedom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExpiryReminderTest {
    private val day = 24 * 60 * 60L
    private val now = 1_800_000_000L

    @Test
    fun stageForEachWindow() {
        assertNull(ExpiryReminder.stageFor(0, now))
        assertNull(ExpiryReminder.stageFor(now + 5 * day, now))
        assertEquals(3, ExpiryReminder.stageFor(now + 3 * day, now))
        assertEquals(3, ExpiryReminder.stageFor(now + 2 * day, now))
        assertEquals(1, ExpiryReminder.stageFor(now + day, now))
        assertEquals(1, ExpiryReminder.stageFor(now + 60, now))
        assertEquals(0, ExpiryReminder.stageFor(now, now))
        assertEquals(0, ExpiryReminder.stageFor(now - day, now))
    }

    @Test
    fun daysLeftRoundsUp() {
        assertEquals(3, ExpiryReminder.daysLeft(now + 2 * day + 1, now))
        assertEquals(1, ExpiryReminder.daysLeft(now + 60, now))
        assertEquals(1, ExpiryReminder.daysLeft(now - 60, now))
    }
}
