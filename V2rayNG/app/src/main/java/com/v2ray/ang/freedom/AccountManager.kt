package com.v2ray.ang.freedom

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
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

    private const val OAUTH_STATE_TTL_MS = 15 * 60 * 1000L

    // ---- session ----

    fun isLoggedIn(): Boolean = !MmkvManager.decodeSettingsString(KEY_ACCESS).isNullOrEmpty()

    fun loginMethod(): String? = MmkvManager.decodeSettingsString(KEY_LOGIN_METHOD)

    fun accessToken(): String? = MmkvManager.decodeSettingsString(KEY_ACCESS)

    fun refreshToken(): String? = MmkvManager.decodeSettingsString(KEY_REFRESH)

    /** The web cabinet (CabinetActivity) rotated the session: keep the newest refresh token. */
    fun updateRefreshToken(token: String) {
        if (isLoggedIn() && token != refreshToken()) MmkvManager.encodeSettings(KEY_REFRESH, token)
    }

    fun saveSession(auth: CabinetApi.AuthResult, method: String) {
        MmkvManager.encodeSettings(KEY_ACCESS, auth.accessToken)
        MmkvManager.encodeSettings(KEY_REFRESH, auth.refreshToken)
        MmkvManager.encodeSettings(KEY_LOGIN_METHOD, method)
    }

    /**
     * Runs [block] with the access token; on HTTP 401 refreshes the session once and retries.
     * Blocking: call from Dispatchers.IO. Throws ApiException(401) if the session cannot be refreshed.
     */
    fun <T> withToken(block: (String) -> T): T {
        val token = accessToken().orEmpty()
        try {
            return block(token)
        } catch (e: CabinetApi.ApiException) {
            if (e.code != 401) throw e
        }
        val refresh = MmkvManager.decodeSettingsString(KEY_REFRESH)
        if (refresh.isNullOrEmpty()) throw CabinetApi.ApiException(401, "session expired")
        val auth = CabinetApi.refresh(refresh)
        MmkvManager.encodeSettings(KEY_ACCESS, auth.accessToken)
        if (auth.refreshToken.isNotEmpty()) MmkvManager.encodeSettings(KEY_REFRESH, auth.refreshToken)
        return block(auth.accessToken)
    }

    /** URL of the subscription imported from the account, if any. */
    fun subscriptionUrl(): String? = MmkvManager.decodeSettingsString(KEY_SUB_URL)

    fun logout() {
        listOf(KEY_ACCESS, KEY_REFRESH, KEY_LOGIN_METHOD).forEach { MmkvManager.encodeSettings(it, null as String?) }
    }

    /** True if the first-start login screen should be shown. */
    /**
     * Welcome / sign-in screen on start: signed out and no servers at all. The built-in empty
     * Default subscription does not count, and "I have a link" only skips it while servers exist.
     */
    fun shouldShowLogin(): Boolean {
        if (isLoggedIn() || skippedThisRun) return false
        return MmkvManager.decodeAllServerList().isEmpty()
    }

    /** "I have a link": stay on the main screen until the app is restarted. */
    @Volatile
    private var skippedThisRun = false

    fun markLoginSkipped() {
        skippedThisRun = true
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
        /** Signed in, but there is no active subscription: show trial / purchase options. */
        data object NoSubscription : SetupResult
        data class Failed(val message: String, val httpCode: Int = 0) : SetupResult
    }

    /** Everything the account screen shows. */
    data class AccountInfo(
        val subscription: CabinetApi.SubscriptionData?,
        val trial: CabinetApi.TrialInfo?,
        /** Personal offers from the bot (e.g. "-30% for 7 days") not yet claimed. */
        val offers: List<CabinetApi.PromoOffer> = emptyList(),
        /** Claimed discount that the next purchase uses. */
        val discount: CabinetApi.ActiveDiscount? = null,
    ) {
        val hasActive: Boolean get() = subscription?.isActive == true
    }

    /** Blocking. */
    fun loadAccount(): AccountInfo {
        val status = withToken { CabinetApi.subscriptionStatus(it) }
        val sub = if (status.hasSubscription) status.subscription else null
        val trial = if (sub?.isActive == true) null else try {
            withToken { CabinetApi.trialInfo(it) }
        } catch (e: CabinetApi.ApiException) {
            if (e.code == 401) throw e
            LogUtil.w(AppConfig.TAG, "Freedom: trial info unavailable: ${e.code}")
            null
        }
        val offers = optional("promo offers") { withToken { CabinetApi.promoOffers(it) } }
            ?.filter { it.isActive && !it.isClaimed && (it.discountPercent ?: 0) > 0 }
            .orEmpty()
        val discount = optional("active discount") { withToken { CabinetApi.activeDiscount(it) } }
            ?.takeIf { it.isActive && it.discountPercent > 0 }
        OfferNotifier.markSeen(offers)
        return AccountInfo(sub, trial, offers, discount)
    }

    /** Extras that must not break the account screen when the cabinet lacks them. */
    private fun <T> optional(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CabinetApi.ApiException) {
        if (e.code == 401) throw e
        LogUtil.w(AppConfig.TAG, "Freedom: $what unavailable: ${e.code}")
        null
    }

    /**
     * Blocking. Imports the user's subscription into the app if it is active.
     * Does not activate the trial: the user chooses trial or purchase on the account screen.
     */
    fun setupSubscription(context: Context): SetupResult {
        return try {
            val status = withToken { CabinetApi.subscriptionStatus(it) }
            if (!status.hasSubscription || status.subscription?.isActive != true) {
                return SetupResult.NoSubscription
            }
            importCurrent(context, status.subscription.subscriptionUrl)
        } catch (e: CabinetApi.ApiException) {
            LogUtil.e(AppConfig.TAG, "Freedom: subscription setup failed", e)
            SetupResult.Failed(e.message ?: "HTTP ${e.code}", e.code)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Freedom: subscription setup failed", e)
            SetupResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Blocking. Activates the free trial and imports it. */
    fun activateTrial(context: Context): SetupResult {
        return try {
            withToken { CabinetApi.activateTrial(it) }
            val status = withToken { CabinetApi.subscriptionStatus(it) }
            importCurrent(context, status.subscription?.subscriptionUrl)
        } catch (e: CabinetApi.ApiException) {
            LogUtil.e(AppConfig.TAG, "Freedom: trial activation failed", e)
            SetupResult.Failed(e.message ?: "HTTP ${e.code}", e.code)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Freedom: trial activation failed", e)
            SetupResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun importCurrent(context: Context, subscriptionUrl: String?): SetupResult {
        var url = subscriptionUrl
        if (url.isNullOrEmpty()) {
            val link = withToken { CabinetApi.connectionLink(it) }
            url = link.subscriptionUrl ?: link.displayLink
        }
        if (url.isNullOrEmpty() || !url.startsWith("http")) {
            return SetupResult.Failed("subscription link is hidden or not ready")
        }
        MmkvManager.encodeSettings(KEY_SUB_URL, url)
        return if (FreedomImporter.importSubscription(context, "$url#${FreedomImporter.SUBSCRIPTION_NAME}")) {
            SetupResult.Imported
        } else {
            SetupResult.Failed("import failed")
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

        val subs = MmkvManager.decodeSubscriptions()
        val subId = subs.firstOrNull { it.subscription.url.substringBefore('#').trim() == baseUrl }?.guid
        if (subId != null) {
            // An earlier account's subscription (another sign-in) would show a second "Freedom VPN" tab.
            subs.filter {
                it.guid != subId && it.subscription.remarks == SUBSCRIPTION_NAME &&
                    it.subscription.url.substringBefore('#').trim() != baseUrl
            }.forEach { SettingsManager.removeSubscriptionWithDefault(it.guid) }
            showSubscription(subId)
        }
        SubscriptionUpdater.sync(context.applicationContext, forceReschedule = true)
        return success
    }

    /** Opens the account's tab on the main screen and selects its first server ("⚡ Авто") unless one of its servers is already selected. */
    private fun showSubscription(subId: String) {
        MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, subId)
        val servers = MmkvManager.decodeServerList(subId)
        val selected = MmkvManager.getSelectServer()
        if (selected.isNullOrEmpty() || selected !in servers) {
            servers.firstOrNull()?.let { MmkvManager.setSelectServer(it) }
        }
    }

    const val SUBSCRIPTION_NAME = "Freedom VPN"
}
