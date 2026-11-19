package com.v2ray.ang.freedom

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
 * Freedom VPN: sign in / register with Telegram, Google or Yandex through the Bedolaga cabinet API,
 * then the account screen: subscription status, free trial, buying or extending a subscription.
 * An active subscription is imported into the app, so the user only has to press "connect".
 */
class LoginActivity : BaseComponentActivity() {

    private sealed interface UiState {
        /** First screen: "I'm new" or "I already have an account". */
        data object Idle : UiState
        /** Sign-in methods; [register] = the user said they are new. */
        data class Methods(val register: Boolean) : UiState
        data object Starting : UiState
        data class WaitingTelegram(val botLink: String) : UiState
        data object WaitingBrowser : UiState
        data class Busy(val textRes: Int) : UiState
        data class Error(val message: String) : UiState
        data class Account(
            val info: AccountManager.AccountInfo,
            val notice: String? = null,
            val noticeIsError: Boolean = true,
        ) : UiState
        data class Plans(val options: CabinetApi.PurchaseOptions, val info: AccountManager.AccountInfo) : UiState
        data class PayMethods(
            val tariff: CabinetApi.Tariff,
            val period: CabinetApi.TariffPeriod,
            val missingKopeks: Long,
            val methods: List<CabinetApi.PaymentMethod>,
            val info: AccountManager.AccountInfo,
        ) : UiState
        data class WaitingPayment(val paymentUrl: String, val previousEnd: String?, val info: AccountManager.AccountInfo) : UiState
    }

    private var state by mutableStateOf<UiState>(UiState.Idle)
    /** The user chose "I'm new": after signing in, start the free trial right away. */
    private var registerFlow = false
    private var telegramJob: Job? = null
    private var paymentJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleSetupIntent(intent)) return
        // Telegram login was started before the activity was recreated: keep waiting for it.
        val pendingTg = AccountManager.pendingTelegramToken()
        if (pendingTg != null && !AccountManager.isLoggedIn()) {
            state = UiState.WaitingTelegram(botLink(FreedomConfig.BOT_USERNAME, pendingTg))
            startTelegramPolling(pendingTg)
        } else if (AccountManager.isLoggedIn()) {
            loadAccount()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSetupIntent(intent)
    }

    /** OAuthCallbackActivity has signed in: continue with the subscription. */
    private fun handleSetupIntent(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_SETUP_AFTER_LOGIN, false) != true) return false
        intent.removeExtra(EXTRA_SETUP_AFTER_LOGIN)
        afterLogin()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the browser without the App Link firing: let the user retry.
        if (state == UiState.WaitingBrowser && !AccountManager.isLoggedIn()) {
            lifecycleScope.launch {
                delay(1500)
                if (state == UiState.WaitingBrowser) state = UiState.Methods(registerFlow)
            }
        }
        // Back from the payment page: check right away.
        (state as? UiState.WaitingPayment)?.let { checkPaymentNow(it) }
    }

    @Composable
    override fun ScreenContent() {
        val current = state
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0E1626)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            when (current) {
                is UiState.Account -> AccountContent(current)
                is UiState.Plans -> PlansContent(current)
                is UiState.PayMethods -> PayMethodsContent(current)
                is UiState.WaitingPayment -> WaitingPaymentContent(current)
                else -> LoginContent(current)
            }
        }
    }

    // ---- screens ----

    @Composable
    private fun Progress(text: String) {
        Spacer(Modifier.height(12.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center)
    }

    @Composable
    private fun LoginContent(current: UiState) {
        when (current) {
            UiState.Starting -> Progress(stringResource(R.string.freedom_login_starting))
            UiState.WaitingBrowser -> Progress(stringResource(R.string.freedom_login_waiting_browser))
            is UiState.Busy -> Progress(stringResource(current.textRes))

            is UiState.WaitingTelegram -> {
                Progress(stringResource(R.string.freedom_login_waiting_telegram))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { openTelegram(current.botLink) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.freedom_login_open_bot_again))
                }
                TextButton(onClick = { cancelTelegram() }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }

            is UiState.Methods -> MethodsContent(current.register, null)
            is UiState.Error -> {
                if (AccountManager.isLoggedIn()) {
                    ErrorText(current.message)
                    Button(onClick = { loadAccount() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.freedom_retry))
                    }
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = { logout() }) { Text(stringResource(R.string.freedom_account_logout)) }
                } else {
                    MethodsContent(registerFlow, current.message)
                }
            }

            else -> WelcomeContent()
        }
    }

    @Composable
    private fun ErrorText(message: String) {
        Text(
            stringResource(R.string.freedom_login_error, message),
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun WelcomeContent() {
        Text(
            stringResource(R.string.freedom_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { registerFlow = true; state = UiState.Methods(register = true) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.freedom_welcome_new))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.freedom_welcome_new_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { registerFlow = false; state = UiState.Methods(register = false) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.freedom_welcome_existing))
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { skipLogin() }) {
            Text(stringResource(R.string.freedom_login_have_link))
        }
    }

    @Composable
    private fun MethodsContent(register: Boolean, error: String?) {
        Text(
            stringResource(if (register) R.string.freedom_register_title else R.string.freedom_signin_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (register) R.string.freedom_register_subtitle else R.string.freedom_signin_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        error?.let { ErrorText(it) }
        val tg = if (register) R.string.freedom_register_telegram else R.string.freedom_login_telegram
        val google = if (register) R.string.freedom_register_google else R.string.freedom_login_google
        val yandex = if (register) R.string.freedom_register_yandex else R.string.freedom_login_yandex
        Button(onClick = { startTelegramLogin() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(tg))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { startOAuth(FreedomConfig.PROVIDER_GOOGLE) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(google))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { startOAuth(FreedomConfig.PROVIDER_YANDEX) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(yandex))
        }
        if (!register) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.freedom_signin_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { skipLogin() }) {
                Text(stringResource(R.string.freedom_login_have_link))
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { state = UiState.Idle }) { Text(stringResource(R.string.freedom_back)) }
    }

    @Composable
    private fun InfoCard(content: @Composable () -> Unit) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }

    @Composable
    private fun SubscriptionCard(info: AccountManager.AccountInfo) {
        val sub = info.subscription
        InfoCard {
            Text(stringResource(R.string.freedom_sub_title), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            if (sub == null || (!sub.isActive && sub.endDate == null)) {
                Text(stringResource(R.string.freedom_sub_none), style = MaterialTheme.typography.titleMedium)
                return@InfoCard
            }
            val title = when {
                sub.isTrial -> stringResource(R.string.freedom_sub_trial)
                !sub.tariffName.isNullOrEmpty() -> sub.tariffName.orEmpty()
                else -> stringResource(R.string.freedom_sub_paid)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (sub.isActive) {
                val left = sub.timeLeftDisplay?.takeIf { it.isNotBlank() } ?: sub.daysLeft.toString()
                Text(stringResource(R.string.freedom_sub_until, formatDate(sub.endDate), left))
                val traffic = if (sub.trafficLimitGb > 0) {
                    stringResource(R.string.freedom_sub_traffic, "%.1f".format(sub.trafficUsedGb), sub.trafficLimitGb)
                } else {
                    stringResource(R.string.freedom_sub_traffic_unlimited)
                }
                Text(traffic)
                if (sub.deviceLimit > 0) Text(stringResource(R.string.freedom_sub_devices, sub.deviceLimit))
            } else {
                Text(
                    stringResource(R.string.freedom_sub_expired, formatDate(sub.endDate)),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    @Composable
    private fun AccountContent(current: UiState.Account) {
        val info = current.info
        val method = AccountManager.loginMethod().orEmpty()
        Text(
            stringResource(R.string.freedom_account_signed_in, method),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        SubscriptionCard(info)
        current.notice?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = if (current.noticeIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
        }
        info.discount?.let { d ->
            Spacer(Modifier.height(10.dp))
            DiscountBadge(d)
        }
        info.offers.forEach { offer ->
            Spacer(Modifier.height(10.dp))
            OfferCard(offer, onClaim = { claimOffer(info, offer) })
        }
        Spacer(Modifier.height(16.dp))

        val trial = info.trial
        if (info.hasActive) {
            Button(onClick = { importAndOpenMain() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.freedom_account_connect))
            }
            Spacer(Modifier.height(10.dp))
        } else if (trial?.isAvailable == true && !trial.requiresPayment) {
            Button(onClick = { startTrial() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.freedom_trial_button, trial.durationDays))
            }
            val details = buildString {
                append(
                    if (trial.trafficLimitGb > 0) getString(R.string.freedom_trial_traffic, trial.trafficLimitGb)
                    else getString(R.string.freedom_sub_traffic_unlimited)
                )
                if (trial.deviceLimit > 0) append(" · ").append(getString(R.string.freedom_sub_devices, trial.deviceLimit))
            }
            Text(details, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
        }

        val buyText = if (info.subscription != null && !info.subscription.isTrial) {
            R.string.freedom_buy_extend
        } else {
            R.string.freedom_buy
        }
        if (info.hasActive || trial?.isAvailable == true) {
            OutlinedButton(onClick = { loadPlans(info) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(buyText))
            }
        } else {
            Button(onClick = { loadPlans(info) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(buyText))
            }
        }
        Spacer(Modifier.height(16.dp))
        PromoCodeField(onApply = { code -> activatePromocode(info, code) })
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { openUrl(FreedomConfig.SUPPORT_URL) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.freedom_support))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { loadAccount() }) { Text(stringResource(R.string.freedom_refresh)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { logout() }) { Text(stringResource(R.string.freedom_account_logout)) }
        }
    }

    @Composable
    private fun DiscountBadge(discount: CabinetApi.ActiveDiscount) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (discount.expiresAt != null) {
                    stringResource(R.string.freedom_discount_active_until, discount.discountPercent, formatDate(discount.expiresAt))
                } else {
                    stringResource(R.string.freedom_discount_active, discount.discountPercent)
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }

    @Composable
    private fun OfferCard(offer: CabinetApi.PromoOffer, onClaim: () -> Unit) {
        InfoCard {
            Text(stringResource(R.string.freedom_offer_title), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.freedom_offer_text, offer.discountPercent ?: 0, formatDate(offer.expiresAt)),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onClaim, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.freedom_offer_claim))
            }
        }
    }

    @Composable
    private fun PromoCodeField(onApply: (String) -> Unit) {
        var code by rememberSaveable { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(64) },
                label = { Text(stringResource(R.string.freedom_promo_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onApply(code.trim()) }, enabled = code.isNotBlank()) {
                Text(stringResource(R.string.freedom_promo_apply))
            }
        }
    }

    @Composable
    private fun PlansContent(current: UiState.Plans) {
        val opts = current.options
        Text(stringResource(R.string.freedom_plans_title), style = MaterialTheme.typography.titleMedium)
        opts.balanceLabel?.let {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.freedom_balance, it), style = MaterialTheme.typography.bodySmall)
        }
        current.info.discount?.let {
            Spacer(Modifier.height(8.dp))
            DiscountBadge(it)
        }
        Spacer(Modifier.height(12.dp))
        val tariffs = opts.tariffs.orEmpty().filter { it.isAvailable && !it.periods.isNullOrEmpty() }
        if (opts.salesMode != "tariffs" || tariffs.isEmpty()) {
            Text(stringResource(R.string.freedom_plans_in_bot), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { openTelegram(FreedomConfig.BOT_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.freedom_open_bot_pay))
            }
        } else {
            tariffs.forEach { tariff ->
                if (tariffs.size > 1 || !tariff.description.isNullOrBlank()) {
                    Text(tariff.name.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    tariff.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                val features = buildList {
                    tariff.trafficLimitLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (tariff.deviceLimit > 0) add(getString(R.string.freedom_sub_devices, tariff.deviceLimit))
                }
                if (features.isNotEmpty()) {
                    Text(features.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }
                tariff.periods.orEmpty().forEach { period ->
                    PeriodRow(period, current.info.discount?.discountPercent ?: 0) { buy(tariff, period, current.info) }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        TextButton(onClick = { state = UiState.Account(current.info) }) {
            Text(stringResource(R.string.freedom_back))
        }
    }

    @Composable
    private fun PeriodRow(period: CabinetApi.TariffPeriod, discountPercent: Int, onClick: () -> Unit) {
        val highlighted = period.isHighlighted
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        period.label ?: getString(R.string.freedom_days, period.days),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    period.pricePerMonthLabel?.takeIf { period.days > 31 }?.let {
                        Text(stringResource(R.string.freedom_per_month, it), style = MaterialTheme.typography.bodySmall)
                    }
                }
                val fullPrice = period.priceLabel ?: formatRub(period.priceKopeks)
                if (discountPercent in 1..99) {
                    // The personal discount is applied by the bot at purchase time.
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            fullPrice,
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                        )
                        Text(
                            formatRub(period.priceKopeks * (100 - discountPercent) / 100),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(fullPrice, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    @Composable
    private fun PayMethodsContent(current: UiState.PayMethods) {
        Text(stringResource(R.string.freedom_pay_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                R.string.freedom_pay_summary,
                current.period.label ?: getString(R.string.freedom_days, current.period.days),
                formatRub(current.missingKopeks),
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        val methods = current.methods.filter { it.isAvailable }
        if (methods.isEmpty()) {
            Text(stringResource(R.string.freedom_pay_none), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { openTelegram(FreedomConfig.BOT_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.freedom_open_bot_pay))
            }
        }
        methods.forEach { method ->
            val opts = method.options.orEmpty()
            if (opts.isEmpty()) {
                OutlinedButton(onClick = { pay(current, method, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(method.name ?: method.id)
                }
                Spacer(Modifier.height(8.dp))
            } else {
                opts.forEach { opt ->
                    OutlinedButton(onClick = { pay(current, method, opt.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(listOfNotNull(opt.name ?: method.name, opt.description?.takeIf { d -> d.isNotBlank() && d != opt.name })
                            .joinToString(" — "))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.freedom_pay_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        TextButton(onClick = { loadPlans(current.info) }) { Text(stringResource(R.string.freedom_back)) }
    }

    @Composable
    private fun WaitingPaymentContent(current: UiState.WaitingPayment) {
        Progress(stringResource(R.string.freedom_pay_waiting))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { openUrl(current.paymentUrl) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.freedom_pay_open_again))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { checkPaymentNow(current) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.freedom_pay_check))
        }
        TextButton(onClick = {
            paymentJob?.cancel()
            state = UiState.Account(current.info)
        }) { Text(stringResource(R.string.freedom_back)) }
    }

    // ---- Telegram ----

    private fun botLink(bot: String, token: String) = "https://t.me/$bot?start=webauth_$token"

    private fun startTelegramLogin() {
        state = UiState.Starting
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dl = CabinetApi.requestDeepLink()
                val bot = dl.botUsername.ifEmpty { FreedomConfig.BOT_USERNAME }
                val link = botLink(bot, dl.token)
                AccountManager.savePendingTelegram(dl.token, if (dl.expiresIn > 0) dl.expiresIn else 600)
                withContext(Dispatchers.Main) {
                    state = UiState.WaitingTelegram(link)
                    openTelegram(link)
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
                    CabinetApi.PollResult.Pending -> delay(2000)
                    CabinetApi.PollResult.Expired -> {
                        AccountManager.clearPendingTelegram()
                        withContext(Dispatchers.Main) {
                            state = UiState.Error(getString(R.string.freedom_login_expired))
                        }
                        return@launch
                    }

                    is CabinetApi.PollResult.Done -> {
                        AccountManager.clearPendingTelegram()
                        AccountManager.saveSession(result.auth, "Telegram")
                        withContext(Dispatchers.Main) { afterLogin() }
                        return@launch
                    }
                }
            }
        }
    }

    private fun cancelTelegram() {
        telegramJob?.cancel()
        AccountManager.clearPendingTelegram()
        state = UiState.Methods(registerFlow)
    }

    /**
     * Opens the bot directly in the Telegram app (tg://), because t.me itself is often
     * unreachable without a VPN; falls back to the https link (browser or another client).
     */
    private fun openTelegram(httpsLink: String) {
        val uri = Uri.parse(httpsLink)
        val bot = uri.pathSegments.firstOrNull()
        val start = uri.getQueryParameter("start")
        if (!bot.isNullOrEmpty()) {
            val tg = buildString {
                append("tg://resolve?domain=").append(bot)
                if (!start.isNullOrEmpty()) append("&start=").append(start)
            }
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tg)))
                return
            } catch (_: ActivityNotFoundException) {
                // Telegram is not installed: use the web link.
            }
        }
        openUrl(httpsLink)
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

    // ---- account ----

    /** Signed in: import an active subscription and go to the servers, otherwise show the account. */
    private fun afterLogin() {
        state = UiState.Busy(R.string.freedom_login_setting_up)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AccountManager.setupSubscription(applicationContext)
            withContext(Dispatchers.Main) {
                when (result) {
                    AccountManager.SetupResult.Imported -> openMain(this@LoginActivity)
                    AccountManager.SetupResult.NoSubscription -> loadAccount(autoTrial = registerFlow)
                    is AccountManager.SetupResult.Failed -> handleFailure(result)
                }
            }
        }
    }

    /** [autoTrial]: the user registered just now — start the free trial without an extra tap. */
    private fun loadAccount(notice: String? = null, autoTrial: Boolean = false, noticeIsError: Boolean = true) {
        state = UiState.Busy(R.string.freedom_loading)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val info = AccountManager.loadAccount()
                val trial = info.trial
                withContext(Dispatchers.Main) {
                    registerFlow = false
                    if (autoTrial && !info.hasActive && trial?.isAvailable == true && !trial.requiresPayment) {
                        startTrial()
                    } else {
                        state = UiState.Account(info, notice, noticeIsError)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: account load failed", e)
                withContext(Dispatchers.Main) {
                    handleFailure(AccountManager.SetupResult.Failed(e.message ?: "network error", (e as? CabinetApi.ApiException)?.code ?: 0))
                }
            }
        }
    }

    private fun startTrial() {
        state = UiState.Busy(R.string.freedom_trial_activating)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AccountManager.activateTrial(applicationContext)
            withContext(Dispatchers.Main) {
                when (result) {
                    AccountManager.SetupResult.Imported -> openMain(this@LoginActivity)
                    AccountManager.SetupResult.NoSubscription -> loadAccount()
                    is AccountManager.SetupResult.Failed -> {
                        if (result.httpCode == 401) handleFailure(result) else loadAccount(result.message)
                    }
                }
            }
        }
    }

    private fun importAndOpenMain() = afterLogin()

    private fun handleFailure(result: AccountManager.SetupResult.Failed) {
        if (result.httpCode == 401) {
            // session expired and could not be refreshed: sign in again
            AccountManager.logout()
        }
        state = UiState.Error(result.message)
    }

    // ---- promo ----

    private fun claimOffer(info: AccountManager.AccountInfo, offer: CabinetApi.PromoOffer) {
        state = UiState.Busy(R.string.freedom_loading)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AccountManager.withToken { CabinetApi.claimOffer(it, offer.id) }
                withContext(Dispatchers.Main) {
                    loadAccount(notice = getString(R.string.freedom_offer_claimed, offer.discountPercent ?: 0), noticeIsError = false)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: claim offer failed", e)
                withContext(Dispatchers.Main) { state = UiState.Account(info, e.message) }
            }
        }
    }

    private fun activatePromocode(info: AccountManager.AccountInfo, code: String) {
        if (code.isEmpty()) return
        state = UiState.Busy(R.string.freedom_promo_applying)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = AccountManager.withToken { CabinetApi.activatePromocode(it, code) }
                // Days or a trial may have been added: refresh the imported subscription.
                AccountManager.setupSubscription(applicationContext)
                val text = result.bonusDescription?.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.freedom_promo_done_with, it) }
                    ?: getString(R.string.freedom_promo_done)
                withContext(Dispatchers.Main) { loadAccount(notice = text, noticeIsError = false) }
            } catch (e: CabinetApi.ApiException) {
                LogUtil.w(AppConfig.TAG, "Freedom: promo code rejected: ${e.errorCode}")
                withContext(Dispatchers.Main) { state = UiState.Account(info, promoError(e)) }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: promo code failed", e)
                withContext(Dispatchers.Main) { state = UiState.Account(info, e.message) }
            }
        }
    }

    private fun promoError(e: CabinetApi.ApiException): String = getString(
        when (e.errorCode) {
            "not_found" -> R.string.freedom_promo_err_not_found
            "expired", "inactive", "not_yet_valid" -> R.string.freedom_promo_err_expired
            "used" -> R.string.freedom_promo_err_used
            "already_used_by_user" -> R.string.freedom_promo_err_already
            "active_discount_exists" -> R.string.freedom_promo_err_discount
            "no_subscription_for_days" -> R.string.freedom_promo_err_no_sub
            "not_first_purchase" -> R.string.freedom_promo_err_first
            "trial_subscription_exists" -> R.string.freedom_promo_err_trial
            "daily_limit" -> R.string.freedom_promo_err_limit
            else -> R.string.freedom_promo_err_other
        }
    )

    // ---- purchase ----

    private fun loadPlans(info: AccountManager.AccountInfo) {
        state = UiState.Busy(R.string.freedom_loading)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val options = AccountManager.withToken { CabinetApi.purchaseOptions(it) }
                withContext(Dispatchers.Main) { state = UiState.Plans(options, info) }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: purchase options failed", e)
                withContext(Dispatchers.Main) { state = UiState.Account(info, e.message) }
            }
        }
    }

    /** Buy from the balance; if it is not enough, the bot keeps the order and buys after a top-up. */
    private fun buy(tariff: CabinetApi.Tariff, period: CabinetApi.TariffPeriod, info: AccountManager.AccountInfo) {
        state = UiState.Busy(R.string.freedom_loading)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                when (val r = AccountManager.withToken { CabinetApi.purchaseTariff(it, tariff.id, period.days) }) {
                    CabinetApi.PurchaseResult.Done -> withContext(Dispatchers.Main) { afterLogin() }
                    is CabinetApi.PurchaseResult.NeedTopUp -> {
                        val methods = AccountManager.withToken { CabinetApi.paymentMethods(it) }
                        val missing = if (r.missingKopeks > 0) r.missingKopeks else period.priceKopeks
                        withContext(Dispatchers.Main) {
                            state = UiState.PayMethods(tariff, period, missing, methods, info)
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: purchase failed", e)
                withContext(Dispatchers.Main) { state = UiState.Account(info, e.message) }
            }
        }
    }

    private fun pay(current: UiState.PayMethods, method: CabinetApi.PaymentMethod, option: String?) {
        val amount = current.missingKopeks
            .coerceAtLeast(method.minAmountKopeks)
            .let { if (method.maxAmountKopeks > 0) it.coerceAtMost(method.maxAmountKopeks) else it }
        state = UiState.Busy(R.string.freedom_pay_creating)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val topUp = AccountManager.withToken { CabinetApi.topUp(it, amount, method.id, option) }
                withContext(Dispatchers.Main) {
                    val waiting = UiState.WaitingPayment(topUp.paymentUrl, current.info.subscription?.endDate, current.info)
                    state = waiting
                    openUrl(topUp.paymentUrl)
                    startPaymentPolling(waiting)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Freedom: top-up failed, method=${method.id}", e)
                withContext(Dispatchers.Main) { state = UiState.Account(current.info, e.message) }
            }
        }
    }

    /** True when the subscription became active or got a new end date after the payment. */
    private fun paymentArrived(waiting: UiState.WaitingPayment): Boolean {
        val sub = AccountManager.withToken { CabinetApi.subscriptionStatus(it) }.subscription ?: return false
        if (!sub.isActive) return false
        val wasActive = waiting.info.subscription?.isActive == true
        return !wasActive || sub.endDate != waiting.previousEnd || (waiting.info.subscription?.isTrial == true && !sub.isTrial)
    }

    private fun startPaymentPolling(waiting: UiState.WaitingPayment) {
        paymentJob?.cancel()
        paymentJob = lifecycleScope.launch(Dispatchers.IO) {
            val until = System.currentTimeMillis() + PAYMENT_WAIT_MS
            while (isActive && System.currentTimeMillis() < until) {
                delay(5000)
                val arrived = try {
                    paymentArrived(waiting)
                } catch (e: Exception) {
                    LogUtil.w(AppConfig.TAG, "Freedom: payment check failed: ${e.message}")
                    false
                }
                if (arrived) {
                    withContext(Dispatchers.Main) { afterLogin() }
                    return@launch
                }
            }
        }
    }

    private fun checkPaymentNow(waiting: UiState.WaitingPayment) {
        lifecycleScope.launch(Dispatchers.IO) {
            val arrived = try {
                paymentArrived(waiting)
            } catch (_: Exception) {
                false
            }
            if (arrived) {
                paymentJob?.cancel()
                withContext(Dispatchers.Main) { if (state is UiState.WaitingPayment) afterLogin() }
            }
        }
    }

    // ---- misc ----

    private fun logout() {
        paymentJob?.cancel()
        AccountManager.logout()
        state = UiState.Idle
    }

    private fun skipLogin() {
        AccountManager.markLoginSkipped()
        openMain(this)
    }

    private fun formatDate(iso: String?): String {
        if (iso.isNullOrEmpty()) return "—"
        // "2026-11-24T21:26:00+00:00" -> "24.11.2026"
        val d = iso.take(10).split('-')
        return if (d.size == 3) "${d[2]}.${d[1]}.${d[0]}" else iso
    }

    private fun formatRub(kopeks: Long): String {
        val rub = kopeks / 100
        val kop = kopeks % 100
        return if (kop == 0L) "$rub ₽" else "$rub,${kop.toString().padStart(2, '0')} ₽"
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Freedom: cannot open link", e)
        }
    }

    companion object {
        private const val PAYMENT_WAIT_MS = 30 * 60 * 1000L

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
