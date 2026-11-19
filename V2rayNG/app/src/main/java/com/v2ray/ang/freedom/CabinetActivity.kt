package com.v2ray.ang.freedom

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.util.LogUtil
import org.json.JSONObject

/**
 * Freedom VPN: the web cabinet inside the app, already signed in with the app's session.
 *
 * The cabinet keeps its session as `localStorage.refresh_token`; the app's refresh token is put
 * there before the cabinet starts, so no second sign-in is needed (Google also refuses sign-in
 * inside a WebView). The cabinet rotates the token, so the current value is copied back to the
 * app to keep the app's own session valid. Links to other sites (payments, Telegram) open outside.
 */
class CabinetActivity : BaseComponentActivity() {

    private var webView: WebView? = null
    private var loading by mutableStateOf(true)
    private var canGoBack by mutableStateOf(false)
    private var injected = false

    @Composable
    override fun ScreenContent() {
        BackHandler {
            val wv = webView
            if (wv != null && wv.canGoBack()) wv.goBack() else finish()
        }
        Scaffold(
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.freedom_cabinet),
                    onBackClick = { finish() },
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context -> createWebView(context) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled") // the cabinet is a JavaScript app on our own domain
    private fun createWebView(context: android.content.Context): WebView =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.host == FreedomConfig.CABINET_HOST && uri.path?.startsWith("/auth/oauth") != true) {
                        return false
                    }
                    openOutside(uri)
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    loading = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    canGoBack = view.canGoBack()
                    if (!injected) {
                        injected = true
                        injectSession(view)
                    } else {
                        syncSessionBack(view)
                    }
                }
            }
            webView = this
            val path = intent.getStringExtra(EXTRA_PATH)?.takeIf { it.startsWith("/") }.orEmpty()
            loadUrl(FreedomConfig.CABINET_URL.trimEnd('/') + path)
        }

    /** Gives the cabinet the app's session once, then reloads so the cabinet starts signed in. */
    private fun injectSession(view: WebView) {
        val token = AccountManager.refreshToken()
        if (token.isNullOrEmpty()) return
        val js = """
            (function(){
              var t = ${JSONObject.quote(token)};
              if (localStorage.getItem('refresh_token') !== t) {
                localStorage.setItem('refresh_token', t);
                location.reload();
              }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    /** The cabinet may have rotated the refresh token: keep the app's copy current. */
    private fun syncSessionBack(view: WebView) {
        view.evaluateJavascript("localStorage.getItem('refresh_token')") { raw ->
            val value = raw?.trim()?.removeSurrounding("\"")
            if (!value.isNullOrEmpty() && value != "null") AccountManager.updateRefreshToken(value)
        }
    }

    private fun openOutside(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Freedom: cannot open link from the cabinet", e)
        }
    }

    override fun onPause() {
        webView?.let { syncSessionBack(it) }
        super.onPause()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PATH = "freedom_cabinet_path"

        /** [path]: a cabinet page such as "/referral"; null opens the start page. */
        fun intent(context: android.content.Context, path: String? = null) =
            Intent(context, CabinetActivity::class.java).apply { path?.let { putExtra(EXTRA_PATH, it) } }
    }
}
