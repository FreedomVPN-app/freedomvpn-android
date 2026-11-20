package com.v2ray.ang.handler

import com.v2ray.ang.dto.GitHubRelease
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckerManagerTest {
    private fun asset(name: String) = GitHubRelease.Asset(name, "https://example.com/$name")
    private val assets = listOf(
        asset("FreedomVPN.apk"),
        asset("FreedomVPN_0.9.14_arm64-v8a.apk"),
        asset("FreedomVPN_0.9.14_armeabi-v7a.apk"),
        asset("FreedomVPN_0.9.14_universal.apk"),
    )

    @Test
    fun newerReleaseIsGreater() {
        assertTrue(UpdateCheckerManager.compareVersions("0.9.14", "0.9.13") > 0)
        assertTrue(UpdateCheckerManager.compareVersions("0.9.10", "0.9.9") > 0)
        assertEquals(0, UpdateCheckerManager.compareVersions("0.9.14", "0.9.14"))
    }

    @Test
    fun testBuildIsOlderThanAnyRelease() {
        assertTrue(UpdateCheckerManager.compareVersions("0.9.14", "0.0.30-test") > 0)
    }

    @Test
    fun picksAbiApk() {
        assertEquals("FreedomVPN_0.9.14_arm64-v8a.apk", UpdateCheckerManager.pickDownloadAsset(assets, "arm64-v8a", false)?.name)
    }

    @Test
    fun fallsBackToUniversalForUnknownAbi() {
        assertEquals("FreedomVPN_0.9.14_universal.apk", UpdateCheckerManager.pickDownloadAsset(assets, "x86_64", false)?.name)
    }

    @Test
    fun noApkForOtherFlavor() {
        assertNull(UpdateCheckerManager.pickDownloadAsset(assets, "arm64-v8a", true))
    }
}
