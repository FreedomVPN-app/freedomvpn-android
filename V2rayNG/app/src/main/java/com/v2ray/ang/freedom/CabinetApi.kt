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

    class ApiException(val code: Int, message: String) : IOException(message)

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
        @SerializedName("is_trial") val isTrial: Boolean = false,
        @SerializedName("end_date") val endDate: String? = null,
    )

    data class SubscriptionStatus(
        @SerializedName("has_subscription") val hasSubscription: Boolean = false,
        @SerializedName("subscription") val subscription: SubscriptionData? = null,
    )

    data class TrialInfo(
        @SerializedName("is_available") val isAvailable: Boolean = false,
        @SerializedName("reason_unavailable") val reasonUnavailable: String? = null,
    )

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

    private fun errorDetail(r: Response): String {
        return try {
            gson.fromJson(r.body, JsonObject::class.java)?.get("detail")?.let {
                if (it.isJsonPrimitive) it.asString else it.toString()
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
}
