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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit

/**
 * Freedom VPN: reminds the user that the account subscription is about to end.
 *
 * Uses the expiry the panel publishes in the subscription headers (refreshed by the regular
 * subscription auto-update), so it works without network access of its own. One notification
 * per stage (3 days, 1 day, expired) for a given expiry date.
 */
object ExpiryReminder {
    private const val WORK_NAME = "freedom_expiry_reminder"
    private const val CHANNEL_ID = "freedom_subscription"
    private const val NOTIFICATION_ID = 7301
    private const val KEY_LAST_STAGE = "freedom_expiry_notified"
    private const val DAY_SECONDS = 24 * 60 * 60L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<Worker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Stage for the remaining time: 3 (≤3 days), 1 (≤1 day), 0 (expired); null = nothing to say. */
    internal fun stageFor(expireSeconds: Long, nowSeconds: Long): Int? {
        if (expireSeconds <= 0) return null
        val left = expireSeconds - nowSeconds
        return when {
            left <= 0 -> 0
            left <= DAY_SECONDS -> 1
            left <= 3 * DAY_SECONDS -> 3
            else -> null
        }
    }

    /** Whole days left, rounded up, at least 1 while not expired. */
    internal fun daysLeft(expireSeconds: Long, nowSeconds: Long): Int =
        ((expireSeconds - nowSeconds + DAY_SECONDS - 1) / DAY_SECONDS).toInt().coerceAtLeast(1)

    internal fun check(context: Context) {
        val expire = accountExpiry() ?: return
        val now = System.currentTimeMillis() / 1000
        val stage = stageFor(expire, now) ?: return
        val marker = "$expire:$stage"
        if (MmkvManager.decodeSettingsString(KEY_LAST_STAGE) == marker) return
        if (notify(context, stage, daysLeft(expire, now))) {
            MmkvManager.encodeSettings(KEY_LAST_STAGE, marker)
        }
    }

    /** Expiry (epoch seconds) of the subscription imported from the account, if known. */
    private fun accountExpiry(): Long? {
        val accountUrl = AccountManager.subscriptionUrl()?.substringBefore('#')?.trim()
        val sub = MmkvManager.decodeSubscriptions().firstOrNull {
            val url = it.subscription.url.substringBefore('#').trim()
            (accountUrl != null && url == accountUrl) || it.subscription.remarks == FreedomImporter.SUBSCRIPTION_NAME
        } ?: return null
        return sub.subscription.usageInfo?.expire?.takeIf { it > 0 }
    }

    private fun notify(context: Context, stage: Int, days: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        NotificationHelper.ensureNotificationChannel(
            context = context,
            channelId = CHANNEL_ID,
            channelNameRes = R.string.freedom_expiry_channel,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        )
        val title = context.getString(
            if (stage == 0) R.string.freedom_expiry_title_ended else R.string.freedom_expiry_title
        )
        val text = if (stage == 0) {
            context.getString(R.string.freedom_expiry_text_ended)
        } else {
            context.resources.getQuantityString(R.plurals.freedom_expiry_text, days, days)
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            LoginActivity.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "Freedom: expiry notification not allowed: ${e.message}")
            false
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            try {
                check(applicationContext)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: expiry check failed", e)
            }
            return Result.success()
        }
    }
}
