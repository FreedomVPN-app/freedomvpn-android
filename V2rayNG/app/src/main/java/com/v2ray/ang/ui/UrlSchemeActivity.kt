package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

/**
 * Freedom VPN: handles freedomvpn://install-sub?url=<encoded subscription url>#<name>
 * (and install-config, and plain-text shares).
 *
 * Differences from upstream v2rayNG:
 * - the main screen opens only after the import has finished, so the servers are already there;
 * - opening the same subscription link again refreshes it instead of failing;
 * - if no valid server is selected, the first server of the subscription is selected,
 *   so the user only needs to press the connect button.
 */
class UrlSchemeActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = try {
            extractLink(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
            null
        }
        if (link == null) {
            openMainAndFinish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val success = try {
                importLink(link)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to import link", e)
                false
            }
            withContext(Dispatchers.Main) {
                applicationContext.toast(
                    if (success) R.string.import_subscription_success else R.string.import_subscription_failure
                )
                openMainAndFinish()
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    private fun openMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    /** Returns the decoded link (with #name fragment if given) or null if there is nothing to import. */
    private fun extractLink(intent: Intent): String? {
        val raw: String?
        var fragment: String? = null
        when (intent.action) {
            Intent.ACTION_SEND -> {
                raw = if ("text/plain" == intent.type) intent.getStringExtra(Intent.EXTRA_TEXT) else null
            }

            Intent.ACTION_VIEW -> {
                val uri: Uri? = intent.data
                when (uri?.host) {
                    "install-config", "install-sub" -> {
                        raw = uri?.getQueryParameter("url")
                        fragment = uri?.fragment
                    }

                    else -> {
                        toastError(R.string.toast_failure)
                        raw = null
                    }
                }
            }

            else -> raw = null
        }
        if (raw.isNullOrEmpty()) {
            return null
        }
        var decodedUrl = URLDecoder.decode(raw, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null && uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
            decodedUrl += "#${fragment}"
        }
        LogUtil.i(AppConfig.TAG, decodedUrl)
        return decodedUrl
    }

    /** Runs on a background thread. Returns true if something was imported or refreshed. */
    private fun importLink(link: String): Boolean {
        val baseUrl = link.substringBefore('#').trim()

        // Same subscription opened again from the bot: refresh it instead of reporting a failure.
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
        SubscriptionUpdater.sync(applicationContext, forceReschedule = true)
        return success
    }

    private fun selectFirstServerIfNeeded(subId: String) {
        val selected = MmkvManager.getSelectServer()
        val selectedIsValid = !selected.isNullOrEmpty() &&
                MmkvManager.decodeAllServerList().contains(selected)
        if (selectedIsValid) {
            return
        }
        MmkvManager.decodeServerList(subId).firstOrNull()?.let { first ->
            MmkvManager.setSelectServer(first)
        }
    }
}
