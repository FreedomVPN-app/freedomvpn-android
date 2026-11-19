package com.v2ray.ang.freedom

/**
 * Freedom VPN: service-specific addresses. Change them here (one place).
 *
 * CABINET_URL must be exactly the CABINET_URL of the Bedolaga bot: Google/Yandex return the user to
 * CABINET_URL/auth/oauth/callback, and the app intercepts that address (Android App Link,
 * needs /.well-known/assetlinks.json on that host).
 */
object FreedomConfig {
    const val CABINET_HOST = "cabinet.topfreedomhub.com"
    const val CABINET_URL = "https://$CABINET_HOST"
    /** Referral section of the cabinet ("Invite a friend"). */
    const val CABINET_REFERRAL_PATH = "/referral"

    /** Bedolaga cabinet API: the web cabinet calls it as <cabinet>/api, the bot mounts it under /cabinet. */
    const val CABINET_API_URL = "$CABINET_URL/api/cabinet"

    const val OAUTH_CALLBACK_PATH = "/auth/oauth/callback"

    const val BOT_USERNAME = "Topfreedom_bot"
    const val BOT_URL = "https://t.me/$BOT_USERNAME"
    const val SUPPORT_URL = "https://t.me/topfreedom_support_bot"

    const val PROVIDER_GOOGLE = "google"
    const val PROVIDER_YANDEX = "yandex"
}
