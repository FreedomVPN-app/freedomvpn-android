package com.v2ray.ang.freedom

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

/**
 * Freedom VPN: device headers for subscription requests (the Happ / Remnawave "HWID" standard), so the
 * panel's HWID device limit can count devices. Without x-hwid a panel with the limit enabled answers 404.
 *
 * New owner because nothing else in the app owns a per-device identity: the XUDP base key in Utils is a
 * different value with a different purpose.
 *
 * x-hwid is not the raw device id: it is a hash of ANDROID_ID (already unique per signing key and user)
 * and the subscription host, so two subscription providers cannot match the same phone by its HWID.
 */
object DeviceId {
    const val HEADER_HWID = "x-hwid"
    const val HEADER_DEVICE_OS = "x-device-os"
    const val HEADER_VER_OS = "x-ver-os"
    const val HEADER_DEVICE_MODEL = "x-device-model"

    private const val KEY_FALLBACK_ID = "freedom_device_id"
    private const val HWID_LENGTH = 32

    /** Headers for a subscription request to [url]; empty if the url has no host. */
    fun subscriptionHeaders(url: String?): Map<String, String> {
        val host = hostOf(url) ?: return emptyMap()
        return mapOf(
            HEADER_HWID to hwid(deviceSecret(), host),
            HEADER_DEVICE_OS to "Android",
            HEADER_VER_OS to headerValue(Build.VERSION.RELEASE),
            HEADER_DEVICE_MODEL to headerValue(deviceModel(Build.MANUFACTURER, Build.MODEL)),
        ).filterValues { it.isNotEmpty() }
    }

    /** 32 hex chars: fits Remnawave's x-hwid format ^[a-zA-Z0-9=-]{10,64}$ and is stable per device and host. */
    fun hwid(deviceSecret: String, host: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("freedomvpn-hwid|$deviceSecret|${host.lowercase()}".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(HWID_LENGTH)
    }

    /** "Xiaomi 23113RKC6G"; the manufacturer is not repeated when the model already starts with it. */
    fun deviceModel(manufacturer: String?, model: String?): String {
        val m = model.orEmpty().trim()
        val brand = manufacturer.orEmpty().trim()
        return when {
            brand.isEmpty() -> m
            m.startsWith(brand, ignoreCase = true) -> m
            else -> "$brand $m".trim()
        }
    }

    /** OkHttp rejects non-ASCII header values: keep printable ASCII only. */
    fun headerValue(value: String?): String =
        value.orEmpty().filter { it in ' '..'~' }.trim().take(64)

    fun hostOf(url: String?): String? = try {
        URI(url?.trim().orEmpty()).host?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    /** ANDROID_ID survives reinstalls (Android 8+); a stored random id covers devices that return none. */
    @SuppressLint("HardwareIds") // used only as hash input for the HWID device limit, see the class comment
    private fun deviceSecret(): String {
        val androidId = try {
            Settings.Secure.getString(AngApplication.application.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Freedom: ANDROID_ID unavailable for subscription HWID, using stored device id", e)
            null
        }
        if (!androidId.isNullOrBlank()) return androidId
        MmkvManager.decodeSettingsString(KEY_FALLBACK_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        return UUID.randomUUID().toString().also { MmkvManager.encodeSettings(KEY_FALLBACK_ID, it) }
    }
}
