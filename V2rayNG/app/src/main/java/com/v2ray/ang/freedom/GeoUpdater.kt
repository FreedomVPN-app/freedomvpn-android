package com.v2ray.ang.freedom

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Freedom VPN: keeps the RoscomVPN geo databases (geosite.dat, geoip.dat) fresh without a new
 * app release. Weekly WorkManager job; a downloaded file replaces the current one only after
 * [isUsable] confirms it is complete and has the sections the panel templates use.
 * Runs only while RoscomVPN is the selected geo source (the default).
 */
object GeoUpdater {
    private const val WORK_NAME = "freedom_geo_update"
    private const val MIN_SIZE_BYTES = 16 * 1024L

    /** Sections the RU panel templates reference; a RoscomVPN file without them is not used. */
    private val REQUIRED = mapOf(
        AppConfig.GEOSITE_DAT to listOf("CATEGORY-RU", "WHITELIST", "YOUTUBE"),
        AppConfig.GEOIP_DAT to listOf("DIRECT", "PRIVATE"),
    )

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<Worker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Whether a downloaded geo file may replace the current one: not truncated, and for RoscomVPN
     * downloads it contains every required section name.
     */
    fun isUsable(file: File, fileName: String, url: String?): Boolean {
        if (!file.isFile || file.length() < MIN_SIZE_BYTES) return false
        if (url == null || !url.contains("roscomvpn")) return true
        val required = REQUIRED[fileName] ?: return true
        return containsAll(file.readBytes(), required)
    }

    internal fun containsAll(data: ByteArray, names: List<String>): Boolean =
        names.all { indexOf(data, it.toByteArray(Charsets.US_ASCII)) >= 0 }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        if (pattern.isEmpty() || data.size < pattern.size) return -1
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    /** Blocking. Returns how many files were updated. */
    fun update(context: Context): Int {
        val source = MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES)
            ?: AppConfig.GEO_FILES_SOURCES.first()
        if (source != AppConfig.GEO_SOURCE_ROSCOMVPN) return 0
        val dir = File(Utils.userAssetPath(context))
        var updated = 0
        for (name in listOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT)) {
            if (download(dir, name, AppConfig.geoFileUrl(source, name))) updated++
        }
        return updated
    }

    private fun download(dir: File, name: String, url: String): Boolean {
        val temp = File(dir, "$name.freedom_tmp")
        val target = File(dir, name)
        // Direct first; through the app's local proxy if the direct request fails (VPN in proxy mode).
        val ports = listOf(0, SettingsManager.getHttpPort()).distinct()
        for (port in ports) {
            temp.delete()
            val ok = try {
                HttpUtil.downloadToFile(
                    UrlContentRequest(
                        url = url,
                        timeout = 30000,
                        httpPort = port,
                        proxyUsername = SettingsManager.getSocksUsername(),
                        proxyPassword = SettingsManager.getSocksPassword(),
                    ),
                    temp,
                )
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "Freedom: geo download failed ($name, port=$port): ${e.message}")
                false
            }
            if (!ok) continue
            if (!isUsable(temp, name, url)) {
                LogUtil.w(AppConfig.TAG, "Freedom: downloaded $name failed the check, keeping the current file")
                temp.delete()
                return false
            }
            if (temp.renameTo(target)) {
                LogUtil.i(AppConfig.TAG, "Freedom: $name updated (${target.length()} bytes)")
                return true
            }
            temp.delete()
            return false
        }
        temp.delete()
        return false
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            return try {
                update(applicationContext)
                Result.success()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: geo update failed", e)
                Result.retry()
            }
        }
    }
}
