package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerNameSplitTest {
    @Test
    fun splitsLeadingFlagOrEmoji() {
        assertEquals("🇳🇱" to "Netherlands", splitServerName("🇳🇱 Netherlands"))
        assertEquals("⚡" to "Авто — лучший сервер", splitServerName("⚡ Авто — лучший сервер"))
        assertEquals("📶" to "Белые списки", splitServerName("  📶 Белые списки "))
    }

    @Test
    fun keepsNamesWithoutEmoji() {
        assertEquals(null to "Poland01", splitServerName("Poland01"))
        assertEquals(null to "[auto] NL", splitServerName("[auto] NL"))
        assertEquals(null to "🇳🇱", splitServerName("🇳🇱"))
        assertEquals(null to "", splitServerName(""))
    }
}
