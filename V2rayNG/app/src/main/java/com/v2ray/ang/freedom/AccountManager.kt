package com.v2ray.ang.freedom

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil

/**
 * Freedom VPN: account state (cabinet tokens, pending logins) and the
 * "login -> trial if needed -> subscription link -> import" chain.
 */
object AccountManager {

    private const val KEY_ACCESS = "freedom_access_token"
    private const val KEY_REFRESH = "freedom_refresh_token"
    private const val KEY_LOGIN_METHOD = "freedom_login_method"
    private const val KEY_SUB_URL = "freedom_sub_url"
    private const val KEY_TG_TOKEN = "freedom_tg_token"
    private const val KEY_TG_EXPIRES = "freedom_tg_expires"
    private const val KEY_OAUTH_PROVIDER = "freedom_oauth_provider"
    private const val KEY_OAUTH_STATE = "freedom_oauth_state"
    private const val KEY_OAUTH_TIME = "freedom_oauth_time"
    private const val KEY_LOGIN_SKIPPED = "freedom_login_skipped"

    private const val OAUTH_STATE_TTL_MS = 15 * 60 * 1000L

    // ---- session ----

    fun isLoggedIn(): Boolean = !MmkvManager.decodeSettingsString(KEY_ACCESS).isNullOrEmpty()

    fun loginMethod(): String? = MmkvManager.decodeSettingsString(KEY_LOGIN_METHOD)

    fun accessToken(): String? = MmkvManager.decodeSettingsString(KEY_ACCESS)

    fun saveSession(auth: CabinetApi.AuthResult, method: String) {
        MmkvManager.encodeSettings(KEY_ACCESS, auth.accessToken)
        MmkvManager.encodeSettings(KEY_REFRESH, auth.refreshToken)
        MmkvManager.encodeSettings(KEY_LOGIN_METHOD, method)
    }

    fun logout() {
        listOf(KEY_ACCESS, KEY_REFRESH, KEY_LOGIN_METHOD).forEach { MmkvManager.encodeSettings(it, null as String?) }
    }

    /** True if the first-start login screen should be shown. */
    fun shouldShowLogin(): Boolean {
        if (isLoggedIn()) return false
        if (MmkvManager.decodeSettingsBool(KEY_LOGIN_SKIPPED, false)) return false
        return MmkvManager.decodeSubscriptions().isEmpty() && MmkvManager.decodeAllServerList().isEmpty()
    }

    fun markLoginSkipped() {
        MmkvManager.encodeSettings(KEY_LOGIN_SKIPPED, true)
    }

    // ---- pending Telegram login (survives activity recreation) ----

    fun savePendingTelegram(token: String, expiresInSec: Long) {
        MmkvManager.encodeSettings(KEY_TG_TOKEN, token)
        MmkvManager.encodeSettings(KEY_TG_EXPIRES, System.currentTimeMillis() + expiresInSec * 1000)
    }

    fun pendingTelegramToken(): String? {
        val token = MmkvManager.decodeSettingsString(KEY_TG_TOKEN)
        val expires = MmkvManager.decodeSettingsLong(KEY_TG_EXPIRES, 0L)
        return if (!token.isNullOrEmpty() && System.currentTimeMillis() < expires) token else null
    }

    fun clearPendingTelegram() {
        MmkvManager.encodeSettings(KEY_TG_TOKEN, null as String?)
    }

    // ---- pending OAuth login ----

    fun savePendingOAuth(provider: String, state: String) {
        MmkvManager.encodeSettings(KEY_OAUTH_PROVIDER, provider)
        MmkvManager.encodeSettings(KEY_OAUTH_STATE, state)
        MmkvManager.encodeSettings(KEY_OAUTH_TIME, System.currentTimeMillis())
    }

    /** Returns the provider if [state] is the one this app started recently, else null. */
    fun matchPendingOAuth(state: String?): String? {
        if (state.isNullOrEmpty()) return null
        val saved = MmkvManager.decodeSettingsString(KEY_OAUTH_STATE)
        val time = MmkvManager.decodeSettingsLong(KEY_OAUTH_TIME, 0L)
        if (saved != state || System.currentTimeMillis() - time > OAUTH_STATE_TTL_MS) return null
        return MmkvManager.decodeSettingsString(KEY_OAUTH_PROVIDER)
    }

    fun clearPendingOAuth() {
        MmkvManager.encodeSettings(KEY_OAUTH_STATE, null as String?)
    }

    // ---- subscription ----

    sealed interface SetupResult {
        data object Imported : SetupResult
        data class TrialUnavailable(val reason: String?) : SetupResult
        data class Failed(val message: String, val httpCode: Int = 0) : SetupResult
    }

    /**
     * Runs on a background thread after a successful login:
     * gets the subscription (activating the trial if the user has none), imports it into the app
     * and selects the first server.
     */
    fun setupSubscription(context: Context, accessToken: String): SetupResult {
        return try {
            var status = CabinetApi.subscriptionStatus(accessToken)
            if (!status.hasSubscription || status.subscription == null) {
                val trial = CabinetApi.trialInfo(accessToken)
                if (!trial.isAvailable) {
                    return SetupResult.TrialUnavailable(trial.reasonUnavailable)
                }
                CabinetApi.activateTrial(accessToken)
                status = CabinetApi.subscriptionStatus(accessToken)
            }

            var url = status.subscription?.subscriptionUrl
            if (url.isNullOrEmpty()) {
                val link = CabinetApi.connectionLink(accessToken)
                url = link.subscriptionUrl ?: link.displayLink
            }
            if (url.isNullOrEmpty() || !url.startsWith("http")) {
                return SetupResult.Failed("subscription link is hidden or not ready")
            }

            MmkvManager.encodeSettings(KEY_SUB_URL, url)
            if (FreedomImporter.importSubscription(context, "$url#Freedom VPN")) {
                SetupResult.Imported
            } else {
                SetupResult.Failed("import failed")
            }
        } catch (e: CabinetApi.ApiException) {
            LogUtil.e(AppConfig.TAG, "Freedom: subscription setup failed", e)
            SetupResult.Failed(e.message ?: "HTTP ${e.code}", e.code)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Freedom: subscription setup failed", e)
            SetupResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}

/** Freedom VPN: import a subscription link, refresh it if it is already there, select a server. */
object FreedomImporter {

    /** Blocking. [link] may carry a #name fragment. Returns true if something was imported or refreshed. */
    fun importSubscription(context: Context, link: String): Boolean {
        val baseUrl = link.substringBefore('#').trim()

        val existing = MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.url.substringBefore('#').trim() == baseUrl }
        val success = if (existing != null) {
            AngConfigManager.updateConfigViaSub(existing).successCount > 0
        } else {
            val (count, countSub) = AngConfigManager.importBatchConfig(link, "", false)
            count + countSub > 0
        }

        val subId = MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.url.substringBefore('#').trim() == baseUrl }
            ?.guid
        if (subId != null) {
            selectFirstServerIfNeeded(subId)
        }
        SubscriptionUpdater.sync(context.applicationContext, forceReschedule = true)
        return success
    }

    private fun selectFirstServerIfNeeded(subId: String) {
        val selected = MmkvManager.getSelectServer()
        val selectedIsValid = !selected.isNullOrEmpty() &&
                MmkvManager.decodeAllServerList().contains(selected)
        if (selectedIsValid) return
        MmkvManager.decodeServerList(subId).firstOrNull()?.let { MmkvManager.setSelectServer(it) }
    }
}
