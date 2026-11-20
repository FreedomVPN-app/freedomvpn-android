package com.v2ray.ang.freedom

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Freedom VPN: minimal client for the Bedolaga cabinet API (the same API the web cabinet uses).
 * Base URL: [FreedomConfig.CABINET_API_URL], e.g. https://cabinet.example.com/api/cabinet
 *
 * All calls are blocking: call them from Dispatchers.IO.
 */
object CabinetApi {

    /** [errorCode]: machine-readable `detail.code` when the cabinet sends one (e.g. promo code errors). */
    class ApiException(val code: Int, message: String, val errorCode: String? = null) : IOException(message)

    data class PromoOffer(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("discount_percent") val discountPercent: Int? = null,
        @SerializedName("effect_type") val effectType: String? = null,
        @SerializedName("expires_at") val expiresAt: String? = null,
        @SerializedName("is_active") val isActive: Boolean = false,
        @SerializedName("is_claimed") val isClaimed: Boolean = false,
    )

    /** GET /cabinet/referral: the user's invite links and how many people joined through them. */
    data class ReferralInfo(
        @SerializedName("referral_code") val referralCode: String? = null,
        @SerializedName("referral_link") val referralLink: String? = null,
        @SerializedName("bot_referral_link") val botReferralLink: String? = null,
        @SerializedName("total_referrals") val totalReferrals: Int = 0,
        @SerializedName("active_referrals") val activeReferrals: Int = 0,
    )

    data class ActiveDiscount(
        @SerializedName("discount_percent") val discountPercent: Int = 0,
        @SerializedName("expires_at") val expiresAt: String? = null,
        @SerializedName("is_active") val isActive: Boolean = false,
    )

    data class PromocodeResult(
        @SerializedName("success") val success: Boolean = false,
        @SerializedName("error") val error: String? = null,
        @SerializedName("bonus_description") val bonusDescription: String? = null,
    )

    data class AuthResult(
        @SerializedName("access_token") val accessToken: String = "",
        @SerializedName("refresh_token") val refreshToken: String = "",
        @SerializedName("expires_in") val expiresIn: Long = 0,
    )

    data class DeepLinkToken(
        @SerializedName("token") val token: String = "",
        @SerializedName("bot_username") val botUsername: String = "",
        @SerializedName("expires_in") val expiresIn: Long = 0,
    )

    data class OAuthAuthorize(
        @SerializedName("authorize_url") val authorizeUrl: String = "",
        @SerializedName("state") val state: String = "",
    )

    data class SubscriptionData(
        @SerializedName("subscription_url") val subscriptionUrl: String? = null,
        @SerializedName("is_active") val isActive: Boolean = false,
        @SerializedName("is_expired") val isExpired: Boolean = false,
        @SerializedName("is_trial") val isTrial: Boolean = false,
        @SerializedName("end_date") val endDate: String? = null,
        @SerializedName("days_left") val daysLeft: Int = 0,
        @SerializedName("time_left_display") val timeLeftDisplay: String? = null,
        @SerializedName("traffic_limit_gb") val trafficLimitGb: Int = 0,
        @SerializedName("traffic_used_gb") val trafficUsedGb: Double = 0.0,
        @SerializedName("device_limit") val deviceLimit: Int = 0,
        @SerializedName("tariff_name") val tariffName: String? = null,
    )

    data class SubscriptionStatus(
        @SerializedName("has_subscription") val hasSubscription: Boolean = false,
        @SerializedName("subscription") val subscription: SubscriptionData? = null,
    )

    data class TrialInfo(
        @SerializedName("is_available") val isAvailable: Boolean = false,
        @SerializedName("duration_days") val durationDays: Int = 0,
        @SerializedName("traffic_limit_gb") val trafficLimitGb: Int = 0,
        @SerializedName("device_limit") val deviceLimit: Int = 0,
        @SerializedName("requires_payment") val requiresPayment: Boolean = false,
        @SerializedName("reason_unavailable") val reasonUnavailable: String? = null,
    )

    data class TariffPeriod(
        @SerializedName("days") val days: Int = 0,
        @SerializedName("label") val label: String? = null,
        @SerializedName("price_kopeks") val priceKopeks: Long = 0,
        @SerializedName("price_label") val priceLabel: String? = null,
        @SerializedName("price_per_month_label") val pricePerMonthLabel: String? = null,
        @SerializedName("is_highlighted") val isHighlighted: Boolean = false,
    )

    data class Tariff(
        @SerializedName("id") val id: Int = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("traffic_limit_label") val trafficLimitLabel: String? = null,
        @SerializedName("device_limit") val deviceLimit: Int = 0,
        @SerializedName("is_available") val isAvailable: Boolean = true,
        @SerializedName("is_current") val isCurrent: Boolean = false,
        @SerializedName("periods") val periods: List<TariffPeriod>? = null,
    )

    data class PurchaseOptions(
        @SerializedName("sales_mode") val salesMode: String? = null,
        @SerializedName("tariffs") val tariffs: List<Tariff>? = null,
        @SerializedName("balance_kopeks") val balanceKopeks: Long = 0,
        @SerializedName("balance_label") val balanceLabel: String? = null,
    )

    data class PaymentOption(
        @SerializedName("id") val id: String = "",
        @SerializedName("name") val name: String? = null,
        @SerializedName("description") val description: String? = null,
    )

    data class PaymentMethod(
        @SerializedName("id") val id: String = "",
        @SerializedName("name") val name: String? = null,
        @SerializedName("description") val description: String? = null,
        @SerializedName("min_amount_kopeks") val minAmountKopeks: Long = 0,
        @SerializedName("max_amount_kopeks") val maxAmountKopeks: Long = 0,
        @SerializedName("is_available") val isAvailable: Boolean = true,
        @SerializedName("options") val options: List<PaymentOption>? = null,
    )

    data class TopUp(
        @SerializedName("payment_id") val paymentId: String = "",
        @SerializedName("payment_url") val paymentUrl: String = "",
        @SerializedName("amount_kopeks") val amountKopeks: Long = 0,
    )

    /** Result of buying a tariff period from the balance. */
    sealed interface PurchaseResult {
        data object Done : PurchaseResult
        /** Not enough balance; the bot saved a cart and buys automatically after a top-up. */
        data class NeedTopUp(val missingKopeks: Long) : PurchaseResult
    }

    data class ConnectionLink(
        @SerializedName("subscription_url") val subscriptionUrl: String? = null,
        @SerializedName("display_link") val displayLink: String? = null,
    )

    /** Result of polling a Telegram deep-link login. */
    sealed interface PollResult {
        data object Pending : PollResult
        data object Expired : PollResult
        data class Done(val auth: AuthResult) : PollResult
    }

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private data class Response(val code: Int, val body: String)

    private fun call(
        method: String,
        path: String,
        body: Any? = null,
        accessToken: String? = null,
    ): Response {
        val url = FreedomConfig.CABINET_API_URL.trimEnd('/') + path
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", AppConfig.FREEDOM_SUB_USER_AGENT)
        if (!accessToken.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        when (method) {
            "GET" -> builder.get()
            else -> builder.method(method, gson.toJson(body ?: JsonObject()).toRequestBody(jsonType))
        }
        client.newCall(builder.build()).execute().use { response ->
            return Response(response.code, response.body.string())
        }
    }

    private fun errorCode(r: Response): String? = try {
        gson.fromJson(r.body, JsonObject::class.java)?.get("detail")
            ?.takeIf { it.isJsonObject }?.asJsonObject?.get("code")?.asString
    } catch (_: Exception) {
        null
    }

    private fun errorDetail(r: Response): String {
        return try {
            gson.fromJson(r.body, JsonObject::class.java)?.get("detail")?.let {
                when {
                    it.isJsonPrimitive -> it.asString
                    it.isJsonObject && it.asJsonObject.has("message") -> it.asJsonObject.get("message").asString
                    else -> it.toString()
                }
            } ?: "HTTP ${r.code}"
        } catch (_: Exception) {
            "HTTP ${r.code}"
        }
    }

    private inline fun <reified T> parseOk(r: Response): T {
        if (r.code !in 200..299) {
            LogUtil.w(AppConfig.TAG, "Cabinet API error ${r.code}: ${r.body.take(300)}")
            throw ApiException(r.code, errorDetail(r))
        }
        return gson.fromJson(r.body, T::class.java) ?: throw ApiException(r.code, "Empty response")
    }

    // ---- Telegram (deep link to the bot) ----

    fun requestDeepLink(): DeepLinkToken = parseOk(call("POST", "/auth/deeplink/request"))

    fun pollDeepLink(token: String): PollResult {
        val body = JsonObject().apply { addProperty("token", token) }
        val r = call("POST", "/auth/deeplink/poll", body)
        return when (r.code) {
            202 -> PollResult.Pending
            410 -> PollResult.Expired
            else -> PollResult.Done(parseOk(r))
        }
    }

    fun refresh(refreshToken: String): AuthResult {
        val body = JsonObject().apply { addProperty("refresh_token", refreshToken) }
        return parseOk(call("POST", "/auth/refresh", body))
    }

    // ---- OAuth: google, yandex ----

    fun oauthAuthorize(provider: String): OAuthAuthorize =
        parseOk(call("GET", "/auth/oauth/$provider/authorize"))

    fun oauthCallback(provider: String, code: String, state: String, deviceId: String?): AuthResult {
        val body = JsonObject().apply {
            addProperty("code", code)
            addProperty("state", state)
            if (!deviceId.isNullOrEmpty()) addProperty("device_id", deviceId)
        }
        return parseOk(call("POST", "/auth/oauth/$provider/callback", body))
    }

    // ---- Subscription ----

    fun subscriptionStatus(accessToken: String): SubscriptionStatus =
        parseOk(call("GET", "/subscription", accessToken = accessToken))

    fun trialInfo(accessToken: String): TrialInfo =
        parseOk(call("GET", "/subscription/trial", accessToken = accessToken))

    fun activateTrial(accessToken: String) {
        parseOk<JsonObject>(call("POST", "/subscription/trial", JsonObject(), accessToken))
    }

    fun connectionLink(accessToken: String): ConnectionLink =
        parseOk(call("GET", "/subscription/connection-link", accessToken = accessToken))

    // ---- Purchase ----

    fun purchaseOptions(accessToken: String): PurchaseOptions =
        parseOk(call("GET", "/subscription/purchase-options", accessToken = accessToken))

    /** Buys [periodDays] of [tariffId] from the balance (tariffs sales mode). */
    fun purchaseTariff(accessToken: String, tariffId: Int, periodDays: Int): PurchaseResult {
        val body = JsonObject().apply {
            addProperty("tariff_id", tariffId)
            addProperty("period_days", periodDays)
        }
        val r = call("POST", "/subscription/purchase-tariff", body, accessToken)
        if (r.code == 402) {
            val missing = try {
                gson.fromJson(r.body, JsonObject::class.java)
                    ?.getAsJsonObject("detail")?.get("missing_amount")?.asLong ?: 0L
            } catch (_: Exception) {
                0L
            }
            return PurchaseResult.NeedTopUp(missing)
        }
        parseOk<JsonObject>(r)
        return PurchaseResult.Done
    }

    // ---- Promo codes and personal offers ----

    fun promoOffers(accessToken: String): List<PromoOffer> {
        val r = call("GET", "/promo/offers", accessToken = accessToken)
        if (r.code !in 200..299) throw ApiException(r.code, errorDetail(r), errorCode(r))
        return gson.fromJson(r.body, Array<PromoOffer>::class.java)?.toList().orEmpty()
    }

    fun referralInfo(accessToken: String): ReferralInfo =
        parseOk(call("GET", "/referral", accessToken = accessToken))

    fun activeDiscount(accessToken: String): ActiveDiscount =
        parseOk(call("GET", "/promo/active-discount", accessToken = accessToken))

    fun claimOffer(accessToken: String, offerId: Int) {
        val body = JsonObject().apply { addProperty("offer_id", offerId) }
        parseOk<JsonObject>(call("POST", "/promo/claim", body, accessToken))
    }

    /** Throws [ApiException] with [ApiException.errorCode] (not_found, expired, used, ...) on rejection. */
    fun activatePromocode(accessToken: String, code: String): PromocodeResult {
        val body = JsonObject().apply { addProperty("code", code) }
        val r = call("POST", "/promocode/activate", body, accessToken)
        if (r.code !in 200..299) throw ApiException(r.code, errorDetail(r), errorCode(r))
        val result = gson.fromJson(r.body, PromocodeResult::class.java) ?: PromocodeResult()
        if (!result.success) throw ApiException(r.code, result.error ?: "promo code rejected", result.error)
        return result
    }

    fun paymentMethods(accessToken: String): List<PaymentMethod> {
        val r = call("GET", "/balance/payment-methods", accessToken = accessToken)
        if (r.code !in 200..299) {
            LogUtil.w(AppConfig.TAG, "Cabinet API error ${r.code}: ${r.body.take(300)}")
            throw ApiException(r.code, errorDetail(r))
        }
        return gson.fromJson(r.body, Array<PaymentMethod>::class.java)?.toList().orEmpty()
    }

    fun topUp(accessToken: String, amountKopeks: Long, method: String, option: String?): TopUp {
        val body = JsonObject().apply {
            addProperty("amount_kopeks", amountKopeks)
            addProperty("payment_method", method)
            if (!option.isNullOrEmpty()) addProperty("payment_option", option)
        }
        return parseOk(call("POST", "/balance/topup", body, accessToken))
    }
}
