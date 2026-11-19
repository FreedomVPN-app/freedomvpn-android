package com.v2ray.ang.freedom

import android.content.Intent
import android.content.pm.PackageManager
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
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Freedom VPN: receives https://<cabinet>/auth/oauth/callback?code=..&state=.. (Android App Link)
 * after Google / Yandex sign-in.
 *
 * - state started by this app -> exchange the code via the cabinet API, save the session and
 *   continue in LoginActivity (subscription setup);
 * - any other state (the user signs in to the web cabinet in a browser on this phone) ->
 *   hand the URL over to the browser untouched.
 */
class OAuthCallbackActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val code = uri?.getQueryParameter("code")
        val stateParam = uri?.getQueryParameter("state")
        val provider = AccountManager.matchPendingOAuth(stateParam)

        if (uri == null) {
            finish()
            return
        }
        if (provider == null) {
            // Sign-in started in the web cabinet in a browser on this phone. Handing the URL back to
            // the browser opens a new tab that has lost the cabinet's sign-in state ("OAuth session
            // is not for account linking"), so finish the sign-in here and show the cabinet in the app.
            if (code.isNullOrEmpty() || stateParam.isNullOrEmpty()) {
                forwardToBrowser(uri)
                finish()
                return
            }
            finishForeignSignIn(uri, code, stateParam)
            return
        }
        if (code.isNullOrEmpty()) {
            // user cancelled at Google / Yandex (error=access_denied)
            AccountManager.clearPendingOAuth()
            startActivity(LoginActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                val auth = CabinetApi.oauthCallback(
                    provider = provider,
                    code = code,
                    state = stateParam.orEmpty(),
                    deviceId = uri.getQueryParameter("device_id"),
                )
                AccountManager.saveSession(
                    auth,
                    if (provider == FreedomConfig.PROVIDER_GOOGLE) "Google" else "Яндекс"
                )
                true
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: oauth callback failed", e)
                false
            } finally {
                AccountManager.clearPendingOAuth()
            }
            withContext(Dispatchers.Main) {
                if (!ok) toastError(R.string.freedom_login_failed)
                startActivity(
                    LoginActivity.intent(this@OAuthCallbackActivity)
                        .putExtra(LoginActivity.EXTRA_SETUP_AFTER_LOGIN, ok)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
            }
        }
    }

    /** The state belongs to the server either way; the provider is unknown, so try both. */
    private fun finishForeignSignIn(uri: Uri, code: String, state: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            var signedIn = false
            for (provider in listOf(FreedomConfig.PROVIDER_GOOGLE, FreedomConfig.PROVIDER_YANDEX)) {
                try {
                    val auth = CabinetApi.oauthCallback(provider, code, state, uri.getQueryParameter("device_id"))
                    AccountManager.saveSession(auth, if (provider == FreedomConfig.PROVIDER_GOOGLE) "Google" else "Яндекс")
                    signedIn = true
                    break
                } catch (e: Exception) {
                    LogUtil.w(AppConfig.TAG, "Freedom: browser sign-in via $provider not accepted: ${e.message}")
                }
            }
            withContext(Dispatchers.Main) {
                if (signedIn) {
                    startActivity(CabinetActivity.intent(this@OAuthCallbackActivity))
                } else {
                    forwardToBrowser(uri)
                }
                finish()
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    /** Open the URL in the default browser, bypassing this app's App Link. */
    private fun forwardToBrowser(uri: Uri) {
        try {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
            val browser = packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            if (!browser.isNullOrEmpty() && browser != packageName && browser != "android") {
                intent.setPackage(browser)
            } else {
                // no default browser chosen: let the user pick, excluding nothing we can control
                startActivity(Intent.createChooser(intent, null))
                return
            }
            startActivity(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Freedom: cannot forward oauth callback to browser", e)
        }
    }
}
