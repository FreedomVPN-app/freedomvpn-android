package com.v2ray.ang.freedom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Freedom VPN: sign in with Telegram, Google or Yandex through the Bedolaga cabinet API.
 * After sign-in the app gets the user's subscription (activating the trial for new users)
 * and imports it, so the user only has to press "connect".
 */
class LoginActivity : BaseComponentActivity() {

    private sealed interface UiState {
        data object Idle : UiState
        data object Starting : UiState
        data class WaitingTelegram(val botLink: String) : UiState
        data object WaitingBrowser : UiState
        data object SettingUp : UiState
        data class Error(val message: String) : UiState
        data class TrialUnavailable(val reason: String?) : UiState
    }

    private var state by mutableStateOf<UiState>(UiState.Idle)
    private var loggedIn by mutableStateOf(false)
    private var telegramJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loggedIn = AccountManager.isLoggedIn()
        if (handleSetupIntent(intent)) return
        // Telegram login was started before the activity was recreated: keep waiting for it.
        AccountManager.pendingTelegramToken()?.let { token ->
            state = UiState.WaitingTelegram(botLink(token))
            startTelegramPolling(token)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSetupIntent(intent)
    }

    /** OAuthCallbackActivity has signed in: fetch and import the subscription. */
    private fun handleSetupIntent(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_SETUP_AFTER_LOGIN, false) != true) return false
        intent.removeExtra(EXTRA_SETUP_AFTER_LOGIN)
        loggedIn = AccountManager.isLoggedIn()
        runSetup()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the browser without the App Link firing: let the user retry.
        if (state == UiState.WaitingBrowser && AccountManager.isLoggedIn().not()) {
            lifecycleScope.launch {
                delay(1500)
                if (state == UiState.WaitingBrowser) state = UiState.Idle
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val current = state
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1565C0)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            if (loggedIn && current == UiState.Idle) {
                AccountContent()
            } else {
                LoginContent(current)
            }
        }
    }

    @Composable
    private fun LoginContent(current: UiState) {
            Text(
                stringResource(R.string.freedom_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            when (current) {
                UiState.Starting, UiState.SettingUp, UiState.WaitingBrowser -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(
                            when (current) {
                                UiState.SettingUp -> R.string.freedom_login_setting_up
                                UiState.WaitingBrowser -> R.string.freedom_login_waiting_browser
                                else -> R.string.freedom_login_starting
                            }
                        ),
                        textAlign = TextAlign.Center,
                    )
                }

                is UiState.WaitingTelegram -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.freedom_login_waiting_telegram), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { openUrl(current.botLink) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.freedom_login_open_bot_again))
                    }
                    TextButton(onClick = { cancelTelegram() }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }

                else -> {
                    if (current is UiState.Error) {
                        Text(
                            stringResource(R.string.freedom_login_error, current.message),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    if (current is UiState.TrialUnavailable) {
                        Text(
                            stringResource(R.string.freedom_login_trial_unavailable) +
                                    (current.reason?.let { "\n($it)" } ?: ""),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { openUrl(FreedomConfig.BOT_URL) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.freedom_open_bot_pay))
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Button(onClick = { startTelegramLogin() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.freedom_login_telegram))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { startOAuth(FreedomConfig.PROVIDER_GOOGLE) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.freedom_login_google))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { startOAuth(FreedomConfig.PROVIDER_YANDEX) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.freedom_login_yandex))
                    }
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = { skipLogin() }) {
                        Text(stringResource(R.string.freedom_login_have_link))
                    }
                }
            }
    }

    @Composable
    private fun AccountContent() {
        val method = AccountManager.loginMethod().orEmpty()
        Text(
            stringResource(R.string.freedom_account_signed_in, method),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { refreshSubscription() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.freedom_account_refresh))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { openUrl(FreedomConfig.BOT_URL) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.freedom_open_bot_pay))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { openUrl(FreedomConfig.SUPPORT_URL) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.freedom_support))
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = {
            AccountManager.logout()
            loggedIn = false
            state = UiState.Idle
        }) {
            Text(stringResource(R.string.freedom_account_logout))
        }
    }

    // ---- Telegram ----

    private fun botLink(token: String) = "${FreedomConfig.BOT_URL}?start=webauth_$token"

    private fun startTelegramLogin() {
        state = UiState.Starting
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dl = CabinetApi.requestDeepLink()
                val bot = dl.botUsername.ifEmpty { FreedomConfig.BOT_USERNAME }
                val link = "https://t.me/$bot?start=webauth_${dl.token}"
                AccountManager.savePendingTelegram(dl.token, if (dl.expiresIn > 0) dl.expiresIn else 600)
                withContext(Dispatchers.Main) {
                    state = UiState.WaitingTelegram(link)
                    openUrl(link)
                    startTelegramPolling(dl.token)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: telegram login request failed", e)
                withContext(Dispatchers.Main) { state = UiState.Error(e.message ?: "network error") }
            }
        }
    }

    private fun startTelegramPolling(token: String) {
        telegramJob?.cancel()
        telegramJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (AccountManager.pendingTelegramToken() != token) {
                    withContext(Dispatchers.Main) { state = UiState.Error(getString(R.string.freedom_login_expired)) }
                    return@launch
                }
                val result = try {
                    CabinetApi.pollDeepLink(token)
                } catch (e: Exception) {
                    LogUtil.w(AppConfig.TAG, "Freedom: poll failed: ${e.message}")
                    CabinetApi.PollResult.Pending
                }
                when (result) {
                    CabinetApi.PollResult.Pending -> delay(2500)
                    CabinetApi.PollResult.Expired -> {
                        AccountManager.clearPendingTelegram()
                        withContext(Dispatchers.Main) {
                            state = UiState.Error(getString(R.string.freedom_login_expired))
                        }
                        return@launch
                    }

                    is CabinetApi.PollResult.Done -> {
                        AccountManager.clearPendingTelegram()
                        onLoggedIn(result.auth, "Telegram")
                        return@launch
                    }
                }
            }
        }
    }

    private fun cancelTelegram() {
        telegramJob?.cancel()
        AccountManager.clearPendingTelegram()
        state = UiState.Idle
    }

    // ---- Google / Yandex ----

    private fun startOAuth(provider: String) {
        state = UiState.Starting
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val auth = CabinetApi.oauthAuthorize(provider)
                AccountManager.savePendingOAuth(provider, auth.state)
                withContext(Dispatchers.Main) {
                    state = UiState.WaitingBrowser
                    openUrl(auth.authorizeUrl)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: oauth authorize failed", e)
                withContext(Dispatchers.Main) { state = UiState.Error(e.message ?: "network error") }
            }
        }
    }

    // ---- after login ----

    /** Background thread. */
    private suspend fun onLoggedIn(auth: CabinetApi.AuthResult, method: String) {
        AccountManager.saveSession(auth, method)
        withContext(Dispatchers.Main) {
            loggedIn = true
            runSetup()
        }
    }

    private fun refreshSubscription() = runSetup()

    /** Get the subscription (trial for new users) and import it. Main thread. */
    private fun runSetup() {
        val token = AccountManager.accessToken().orEmpty()
        state = UiState.SettingUp
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AccountManager.setupSubscription(applicationContext, token)
            withContext(Dispatchers.Main) { handleSetupResult(result) }
        }
    }

    private fun handleSetupResult(result: AccountManager.SetupResult) {
        when (result) {
            AccountManager.SetupResult.Imported -> openMain(this)
            is AccountManager.SetupResult.TrialUnavailable -> state = UiState.TrialUnavailable(result.reason)
            is AccountManager.SetupResult.Failed -> {
                if (result.httpCode == 401) {
                    // session expired: sign in again
                    AccountManager.logout()
                    loggedIn = false
                }
                state = UiState.Error(result.message)
            }
        }
    }

    private fun skipLogin() {
        AccountManager.markLoginSkipped()
        openMain(this)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Freedom: cannot open $url", e)
        }
    }

    companion object {
        fun openMain(activity: android.app.Activity) {
            activity.startActivity(
                Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            activity.finish()
        }

        const val EXTRA_SETUP_AFTER_LOGIN = "freedom_setup_after_login"

        fun intent(context: Context) = Intent(context, LoginActivity::class.java)
    }
}
