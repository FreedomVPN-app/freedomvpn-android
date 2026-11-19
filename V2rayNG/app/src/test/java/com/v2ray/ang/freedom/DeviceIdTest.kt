package com.v2ray.ang.freedom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceIdTest {
    // Remnawave ignores x-hwid values that do not match this pattern.
    private val remnawaveHwid = Regex("^[a-zA-Z0-9=-]{10,64}$")

    @Test
    fun hwidMatchesRemnawaveFormat() {
        val hwid = DeviceId.hwid("a1b2c3d4e5f60718", "sub.example.com")
        assertEquals(32, hwid.length)
        assertTrue(remnawaveHwid.matches(hwid))
    }

    @Test
    fun hwidIsStableForSameDeviceAndHost() {
        assertEquals(
            DeviceId.hwid("a1b2c3d4e5f60718", "sub.example.com"),
            DeviceId.hwid("a1b2c3d4e5f60718", "SUB.Example.com"),
        )
    }

    @Test
    fun hwidDiffersBetweenProvidersAndDevices() {
        val base = DeviceId.hwid("a1b2c3d4e5f60718", "sub.example.com")
        assertNotEquals(base, DeviceId.hwid("a1b2c3d4e5f60718", "other.example.net"))
        assertNotEquals(base, DeviceId.hwid("ffffffffffffffff", "sub.example.com"))
    }

    @Test
    fun deviceModelDoesNotRepeatManufacturer() {
        assertEquals("Xiaomi 23113RKC6G", DeviceId.deviceModel("Xiaomi", "23113RKC6G"))
        assertEquals("Samsung SM-S918B", DeviceId.deviceModel("samsung", "Samsung SM-S918B"))
        assertEquals("Pixel 8", DeviceId.deviceModel(null, "Pixel 8"))
    }

    @Test
    fun headerValueKeepsPrintableAsciiOnly() {
        assertEquals("Redmi Note", DeviceId.headerValue("Redmi Note™\n"))
        assertEquals("", DeviceId.headerValue(null))
    }

    @Test
    fun hostOfParsesSubscriptionUrls() {
        assertEquals("sub.example.com", DeviceId.hostOf("https://sub.example.com/abc123#Freedom%20VPN"))
        assertNull(DeviceId.hostOf("not a url"))
        assertNull(DeviceId.hostOf(null))
    }
}
