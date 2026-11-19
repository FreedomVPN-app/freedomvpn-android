package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.TimeZone

class ExpiryDateFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val dec18 = 1_797_552_000_000L // 2026-12-18T00:00:00Z

    @Test
    fun russianUsesDayMonthYear() {
        assertEquals("18.12.2026", formatExpiryDate(dec18, Locale.forLanguageTag("ru-RU"), utc))
    }

    @Test
    fun englishKeepsFourDigitYear() {
        val text = formatExpiryDate(dec18, Locale.US, utc)
        assertTrue(text.contains("2026"), text)
        assertTrue(text.contains("18"), text)
    }
}
