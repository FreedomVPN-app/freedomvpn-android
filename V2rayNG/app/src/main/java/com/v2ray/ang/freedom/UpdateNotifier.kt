package com.v2ray.ang.freedom

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.checkupdate.CheckUpdateActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Freedom VPN: once a day checks the latest GitHub release (the same check as "Check for update")
 * and posts a notification when a newer version is out. Each version is announced once; tapping
 * opens the update screen, which offers the download.
 */
object UpdateNotifier {
    private const val WORK_NAME = "freedom_update_check"
    private const val CHANNEL_ID = "freedom_updates"
    private const val NOTIFICATION_ID = 7303
    private const val KEY_NOTIFIED = "freedom_update_notified"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<Worker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** True when [latestVersion] has not been announced yet. */
    internal fun shouldNotify(latestVersion: String?, notifiedVersion: String?): Boolean =
        !latestVersion.isNullOrBlank() && latestVersion != notifiedVersion

    private suspend fun check(context: Context) {
        val result = UpdateCheckerManager.checkForUpdate(includePreRelease = false)
        if (!result.hasUpdate) return
        val latest = result.latestVersion
        if (!shouldNotify(latest, MmkvManager.decodeSettingsString(KEY_NOTIFIED))) return
        if (notify(context, latest.orEmpty())) {
            MmkvManager.encodeSettings(KEY_NOTIFIED, latest)
        }
    }

    private fun notify(context: Context, version: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        NotificationHelper.ensureNotificationChannel(
            context = context,
            channelId = CHANNEL_ID,
            channelNameRes = R.string.freedom_update_channel,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        )
        val text = context.getString(R.string.freedom_update_push_text)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, CheckUpdateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.freedom_update_push_title, version))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "Freedom: update notification not allowed", e)
            false
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            check(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // No response from GitHub, or no APK for this device's ABI: try again on the next daily run.
            LogUtil.w(AppConfig.TAG, "Freedom: update check skipped", e)
            Result.success()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Freedom: update check failed", e)
            Result.retry()
        }
    }
}
