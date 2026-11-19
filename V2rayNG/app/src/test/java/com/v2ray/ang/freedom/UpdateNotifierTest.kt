package com.v2ray.ang.freedom

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateNotifierTest {
    @Test
    fun notifiesAboutVersionNotAnnouncedYet() {
        assertTrue(UpdateNotifier.shouldNotify("0.9.10", null))
        assertTrue(UpdateNotifier.shouldNotify("0.9.11", "0.9.10"))
    }

    @Test
    fun doesNotRepeatAnnouncedVersion() {
        assertFalse(UpdateNotifier.shouldNotify("0.9.10", "0.9.10"))
    }

    @Test
    fun ignoresMissingVersion() {
        assertFalse(UpdateNotifier.shouldNotify(null, null))
        assertFalse(UpdateNotifier.shouldNotify(" ", "0.9.9"))
    }
}
