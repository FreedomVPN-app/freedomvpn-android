package com.v2ray.ang.freedom

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GeoUpdaterTest {
    @TempDir
    lateinit var dir: File

    private val roscomUrl = "https://github.com/hydraponique/roscomvpn-geosite/releases/latest/download/geosite.dat"

    private fun file(name: String, size: Int, vararg markers: String): File {
        val body = ByteArray(size) { 0x11 }
        var pos = 100
        for (m in markers) {
            m.toByteArray().copyInto(body, pos)
            pos += m.length + 10
        }
        return File(dir, name).apply { writeBytes(body) }
    }

    @Test
    fun acceptsCompleteRoscomGeosite() {
        val f = file("geosite.dat", 40_000, "CATEGORY-RU", "WHITELIST", "YOUTUBE")
        assertTrue(GeoUpdater.isUsable(f, AppConfig.GEOSITE_DAT, roscomUrl))
    }

    @Test
    fun rejectsRoscomGeositeWithoutRequiredSection() {
        val f = file("geosite.dat", 40_000, "CATEGORY-RU", "YOUTUBE")
        assertFalse(GeoUpdater.isUsable(f, AppConfig.GEOSITE_DAT, roscomUrl))
    }

    @Test
    fun rejectsTruncatedFile() {
        val f = file("geoip.dat", 1_000, "DIRECT", "PRIVATE")
        assertFalse(GeoUpdater.isUsable(f, AppConfig.GEOIP_DAT, roscomUrl))
    }

    @Test
    fun otherSourcesOnlyNeedSize() {
        val f = file("geosite.dat", 40_000)
        assertTrue(GeoUpdater.isUsable(f, AppConfig.GEOSITE_DAT, "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"))
    }

    @Test
    fun roscomUrlsPointToTheirRepositories() {
        assertEquals(
            "https://github.com/hydraponique/roscomvpn-geoip/releases/latest/download/geoip.dat",
            AppConfig.geoFileUrl(AppConfig.GEO_SOURCE_ROSCOMVPN, AppConfig.GEOIP_DAT),
        )
        assertEquals(roscomUrl, AppConfig.geoFileUrl(AppConfig.GEO_SOURCE_ROSCOMVPN, AppConfig.GEOSITE_DAT))
        assertEquals(
            "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
            AppConfig.geoFileUrl("Loyalsoldier/v2ray-rules-dat", AppConfig.GEOIP_DAT),
        )
    }
}
