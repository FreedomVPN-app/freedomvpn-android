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
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Freedom VPN: push notification about a new personal offer from the bot (e.g. "-30% until 24.11"),
 * so users who signed in with Google / Yandex — and never see the bot's Telegram messages — learn
 * about it. Every 6 hours, only when signed in; each offer is announced once.
 */
object OfferNotifier {
    private const val WORK_NAME = "freedom_offer_check"
    private const val CHANNEL_ID = "freedom_offers"
    private const val NOTIFICATION_ID = 7302
    private const val KEY_NOTIFIED = "freedom_offers_notified"
    private const val MAX_REMEMBERED = 50

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<Worker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Offers to announce: active, unclaimed, with a discount, and not announced before. */
    internal fun newOffers(offers: List<CabinetApi.PromoOffer>, notified: Set<Int>): List<CabinetApi.PromoOffer> =
        offers.filter { it.isActive && !it.isClaimed && (it.discountPercent ?: 0) > 0 && it.id !in notified }

    internal fun parseNotified(raw: String?): Set<Int> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    internal fun encodeNotified(ids: Collection<Int>): String =
        ids.toList().takeLast(MAX_REMEMBERED).joinToString(",")

    /** Offers already shown on the account screen need no push. */
    fun markSeen(offers: List<CabinetApi.PromoOffer>) {
        if (offers.isEmpty()) return
        val notified = parseNotified(MmkvManager.decodeSettingsString(KEY_NOTIFIED))
        val ids = offers.map { it.id }.filter { it !in notified }
        if (ids.isNotEmpty()) MmkvManager.encodeSettings(KEY_NOTIFIED, encodeNotified(notified + ids))
    }

    /** Blocking (network). */
    internal fun check(context: Context) {
        if (!AccountManager.isLoggedIn()) return
        val offers = AccountManager.withToken { CabinetApi.promoOffers(it) }
        val notified = parseNotified(MmkvManager.decodeSettingsString(KEY_NOTIFIED))
        val fresh = newOffers(offers, notified)
        if (fresh.isEmpty()) return
        // One notification for the best current offer.
        val best = fresh.maxBy { it.discountPercent ?: 0 }
        if (notify(context, best)) {
            MmkvManager.encodeSettings(KEY_NOTIFIED, encodeNotified(notified + fresh.map { it.id }))
        }
    }

    private fun notify(context: Context, offer: CabinetApi.PromoOffer): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        NotificationHelper.ensureNotificationChannel(
            context = context,
            channelId = CHANNEL_ID,
            channelNameRes = R.string.freedom_offer_channel,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        )
        val percent = offer.discountPercent ?: 0
        val until = offer.expiresAt?.take(10)?.split('-')?.takeIf { it.size == 3 }
            ?.let { "${it[2]}.${it[1]}" }
        val text = if (until != null) {
            context.getString(R.string.freedom_offer_push_text_until, percent, until)
        } else {
            context.getString(R.string.freedom_offer_push_text, percent)
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            LoginActivity.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.freedom_offer_push_title, percent))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "Freedom: offer notification not allowed: ${e.message}")
            false
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            try {
                check(applicationContext)
                Result.success()
            } catch (e: CabinetApi.ApiException) {
                // 401: signed out or session expired — nothing to do until the next sign-in.
                LogUtil.w(AppConfig.TAG, "Freedom: offer check failed: ${e.code}")
                Result.success()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: offer check failed", e)
                Result.retry()
            }
        }
    }
}
