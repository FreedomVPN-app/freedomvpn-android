package com.v2ray.ang.freedom

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Freedom VPN: state of the "Invite a friend" screen. */
sealed interface InviteUiState {
    data object Loading : InviteUiState
    data object Error : InviteUiState
    data class Ready(
        val link: String,
        val qr: Bitmap?,
        val totalReferrals: Int,
        val activeReferrals: Int,
    ) : InviteUiState
}

/**
 * Loads the user's invite link from the cabinet (GET /referral) and draws its QR code.
 * New owner: no other screen loads or keeps the referral data.
 */
class InviteViewModel : ViewModel() {
    private val _state = MutableStateFlow<InviteUiState>(InviteUiState.Loading)
    val state: StateFlow<InviteUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.value = InviteUiState.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                val info = withContext(Dispatchers.IO) { AccountManager.withToken { CabinetApi.referralInfo(it) } }
                val link = inviteLink(info)
                if (link == null) {
                    InviteUiState.Error
                } else {
                    val qr = withContext(Dispatchers.Default) { QRCodeDecoder.createQRCode(link) }
                    InviteUiState.Ready(link, qr, info.totalReferrals, info.activeReferrals)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "Freedom: invite link not loaded", e)
                InviteUiState.Error
            }
        }
    }
}

/** The cabinet link opens without Telegram (often blocked without a VPN); the bot link is the fallback. */
internal fun inviteLink(info: CabinetApi.ReferralInfo): String? =
    info.referralLink?.trim()?.takeIf { it.startsWith("https://") }
        ?: info.botReferralLink?.trim()?.takeIf { it.startsWith("https://") }

/** Freedom VPN: invite a friend — QR code of the referral link, share, copy, details in the cabinet. */
class InviteActivity : BaseComponentActivity() {
    private val viewModel: InviteViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        Scaffold(
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.freedom_invite),
                    onBackClick = { onBackPressedDispatcher.onBackPressed() },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val s = state) {
                    InviteUiState.Loading -> {
                        Spacer(Modifier.height(48.dp))
                        CircularProgressIndicator()
                    }
                    InviteUiState.Error -> {
                        Spacer(Modifier.height(48.dp))
                        Text(stringResource(R.string.freedom_invite_error), textAlign = TextAlign.Center)
                        Button(onClick = { viewModel.load() }) { Text(stringResource(R.string.freedom_invite_retry)) }
                        TextButton(onClick = { openCabinetReferral() }) { Text(stringResource(R.string.freedom_invite_more)) }
                    }
                    is InviteUiState.Ready -> ReadyContent(s)
                }
            }
        }
    }

    @Composable
    private fun ReadyContent(s: InviteUiState.Ready) {
        Text(
            stringResource(R.string.freedom_invite_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        val qrLabel = stringResource(R.string.freedom_invite_qr)
        Surface(
            shape = RoundedCornerShape(24.dp),
            // QR codes need dark modules on a light ground to scan, in both themes.
            color = Color.White,
            modifier = Modifier
                .size(280.dp)
                .semantics { contentDescription = qrLabel },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                s.qr?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
            }
        }
        Text(
            s.link,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { share(s.link) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.freedom_invite_share))
        }
        OutlinedButton(onClick = { copy(s.link) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.freedom_invite_copy))
        }
        Text(
            stringResource(R.string.freedom_invite_stats, s.totalReferrals, s.activeReferrals),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = { openCabinetReferral() }) { Text(stringResource(R.string.freedom_invite_more)) }
    }

    private fun share(link: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.freedom_invite_share_text, link))
        startActivity(Intent.createChooser(send, getString(R.string.freedom_invite_share)))
    }

    private fun copy(link: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Freedom VPN", link))
        Toast.makeText(this, R.string.freedom_invite_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openCabinetReferral() {
        startActivity(CabinetActivity.intent(this, FreedomConfig.CABINET_REFERRAL_PATH))
    }

    companion object {
        fun intent(context: Context) = Intent(context, InviteActivity::class.java)
    }
}
