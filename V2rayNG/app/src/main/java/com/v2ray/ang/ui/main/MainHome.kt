package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.extension.toTrafficString

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * Freedom VPN: home screen — connection card with a round button, a one-line subscription strip
 * and the subscription's servers right below. State comes from [MainViewModel]; every user
 * operation goes through [onAction] / [onNavigate].
 */
@Composable
internal fun MainHome(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    displayText: String,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    contentPadding: PaddingValues,
) {
    val group = uiState.groups.firstOrNull { it.id == uiState.selectedGroupId } ?: uiState.groups.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.showFreedomPromo) {
            FreedomPromoCard(
                onTry = { onNavigate(MainDestination.Promotion) },
                onDismiss = { onAction(MainAction.DismissFreedomPromo) },
            )
            Spacer(Modifier.height(12.dp))
        }
        if (group == null) {
            NoSubscription(onNavigate)
            return@Column
        }

        val groupState by mainViewModel.serverGroupState(group.id).collectAsStateWithLifecycle()
        val rows = groupState.rows
        val selected = rows.firstOrNull { it.guid == uiState.selectedGuid }

        if (rows.isEmpty()) {
            // The group has no servers yet (link not imported, or the subscription update failed).
            EmptyGroup(
                onUpdate = { onAction(MainAction.UpdateGroupSubscription(group.id)) },
                onNavigate = onNavigate,
            )
            return@Column
        }
        // Nothing selected in this group yet: take its first server ("⚡ Авто").
        LaunchedEffect(group.id, rows.firstOrNull()?.guid, selected == null) {
            if (selected == null) rows.firstOrNull()?.let { onAction(MainAction.SelectServer(it.guid)) }
        }
        // Measure every server once when the list first shows (results stay until the next test).
        LaunchedEffect(group.id) {
            if (!uiState.isTesting && rows.all { it.testDelayMillis == 0L }) onAction(MainAction.TestRealAllServers)
        }

        ConnectionCard(
            isRunning = uiState.isRunning,
            serverName = selected?.remarks?.let { splitServerName(it).second },
            displayText = displayText,
            onToggle = { onAction(MainAction.ToggleService) },
            onTestCurrent = { onAction(MainAction.TestCurrentServer) },
        )

        group.usageInfo?.let { info ->
            Spacer(Modifier.height(12.dp))
            val model = remember(info) { buildSubscriptionInfoUiModel(info, System.currentTimeMillis()) }
            SubscriptionStrip(
                model = model,
                onInvite = if (isFreedomGroup(group.remarks)) ({ onAction(MainAction.OpenInvite) }) else null,
            )
        }

        Spacer(Modifier.height(20.dp))
        ServersHeader(
            testing = uiState.isTesting,
            onTest = { onAction(MainAction.TestRealAllServers) },
            onRefresh = { onAction(MainAction.UpdateGroupSubscription(group.id)) },
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEach { row ->
                    androidx.compose.runtime.key(row.guid) {
                        ServerRow(
                            row = row,
                            selected = row.guid == uiState.selectedGuid,
                            testing = uiState.isTesting,
                            onSelect = { onAction(MainAction.SelectServer(row.guid)) },
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = { onAction(MainAction.SetSimpleMode(false)) },
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.freedom_home_all_servers_and_subs))
        }
    }
}

@Composable
private fun ConnectionCard(
    isRunning: Boolean,
    serverName: String?,
    displayText: String,
    onToggle: () -> Unit,
    onTestCurrent: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cardColor by animateColorAsState(if (isRunning) scheme.primary else scheme.surfaceContainer, label = "heroCard")
    val onCard = if (isRunning) scheme.onPrimary else scheme.onSurface
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cardColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusChip(isRunning = isRunning, serverName = serverName, onCard = onCard)
            Spacer(Modifier.height(20.dp))
            ConnectButton(isRunning = isRunning, onClick = onToggle)
            Spacer(Modifier.height(16.dp))
            if (isRunning) {
                if (displayText.isNotBlank()) {
                    TextButton(onClick = onTestCurrent) {
                        Text(displayText, color = onCard, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            } else {
                Text(
                    stringResource(R.string.freedom_home_tap_to_connect),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(isRunning: Boolean, serverName: String?, onCard: Color) {
    val scheme = MaterialTheme.colorScheme
    val text = when {
        isRunning && !serverName.isNullOrBlank() -> stringResource(R.string.freedom_home_connected_to, serverName)
        isRunning -> stringResource(R.string.freedom_home_connected)
        else -> stringResource(R.string.freedom_home_disconnected)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isRunning) onCard.copy(alpha = 0.16f) else scheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) scheme.tertiary else scheme.onSurfaceVariant),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text,
                color = onCard,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConnectButton(isRunning: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (isRunning) scheme.onPrimary else scheme.primary, label = "connectButton")
    val content = if (isRunning) scheme.primary else scheme.onPrimary
    val ring = if (isRunning) scheme.onPrimary.copy(alpha = 0.22f) else scheme.primaryContainer
    val label = stringResource(if (isRunning) R.string.freedom_home_disconnect else R.string.freedom_home_connect)
    val state = stringResource(if (isRunning) R.string.freedom_home_connected else R.string.freedom_home_disconnected)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        border = BorderStroke(10.dp, ring),
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(196.dp)
            .semantics {
                contentDescription = label
                stateDescription = state
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_freedom_power_24dp),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(60.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(label, color = content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** One line: days left (or traffic), expiry date and traffic, a thin traffic bar, "Invite". */
@Composable
private fun SubscriptionStrip(model: SubscriptionInfoUiModel, onInvite: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val totalText = if (model.totalBytes > 0L) model.totalBytes.toTrafficString() else "∞"
    val traffic = stringResource(R.string.freedom_home_traffic_of, model.usedBytes.toTrafficString(), totalText)
    val headline = when {
        model.expired -> stringResource(R.string.freedom_home_expired)
        model.expireMillis != null -> {
            val days = freedomDaysLeft(model.expireMillis, System.currentTimeMillis())
            pluralStringResource(R.plurals.freedom_home_days_left, days, days)
        }
        else -> stringResource(R.string.sub_info_unlimited)
    }
    val details = listOfNotNull(
        model.expireMillis?.let { stringResource(R.string.freedom_home_until, formatExpiryDate(it, locale)) },
        traffic,
    ).joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (model.expired) scheme.error else scheme.onSurface,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (model.trafficLow) scheme.error else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                model.trafficFraction?.let { fraction ->
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .clearAndSetSemantics {},
                        color = if (model.trafficLow) scheme.error else scheme.primary,
                        trackColor = scheme.surfaceVariant,
                    )
                }
            }
            if (onInvite != null) {
                Spacer(Modifier.size(10.dp))
                Surface(
                    onClick = onInvite,
                    shape = RoundedCornerShape(50),
                    color = scheme.primaryContainer,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_freedom_invite_24dp),
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.freedom_home_invite_short),
                            color = scheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServersHeader(testing: Boolean, onTest: () -> Unit, onRefresh: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.freedom_home_servers),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        Surface(
            onClick = onTest,
            enabled = !testing,
            shape = RoundedCornerShape(50),
            color = scheme.surfaceContainer,
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_freedom_speed_24dp),
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(if (testing) R.string.freedom_home_ping_running else R.string.freedom_home_ping),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        IconButton(onClick = onRefresh) {
            Icon(
                painterResource(R.drawable.ic_refresh_24dp),
                contentDescription = stringResource(R.string.freedom_home_update),
                tint = scheme.primary,
            )
        }
    }
}

@Composable
private fun ServerRow(row: ServerRowUiModel, selected: Boolean, testing: Boolean, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (flag, name) = splitServerName(row.remarks)
    val stateText = stringResource(if (selected) R.string.freedom_home_selected else R.string.freedom_home_not_selected)
    Surface(
        shape = RoundedCornerShape(if (selected) 22.dp else 14.dp),
        color = if (selected) scheme.primaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) { stateDescription = stateText },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 60.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = scheme.surfaceContainerLow, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(flag ?: "🌐", fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics {})
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            DelayChip(row.testDelayMillis, testing)
            if (selected) {
                Spacer(Modifier.size(8.dp))
                Surface(shape = CircleShape, color = scheme.primary, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_freedom_check_24dp),
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Measured delay as a colored pill: green / amber / red by speed, "no connection", "…" while testing. */
@Composable
private fun DelayChip(delay: Long, testing: Boolean) {
    val dark = com.v2ray.ang.ui.compose.LocalDarkTheme.current
    val text: String
    val fg: Color
    val bg: Color
    when {
        delay > 0 -> {
            text = stringResource(R.string.freedom_home_delay, delay)
            when {
                delay < 300 -> { fg = if (dark) Color(0xFF4ADE80) else Color(0xFF15803D); bg = if (dark) Color(0xFF123524) else Color(0xFFDCF2E3) }
                delay < 800 -> { fg = if (dark) Color(0xFFFACC15) else Color(0xFF8A5300); bg = if (dark) Color(0xFF3A3112) else Color(0xFFFBEBD0) }
                else -> { fg = MaterialTheme.colorScheme.onErrorContainer; bg = MaterialTheme.colorScheme.errorContainer }
            }
        }
        delay < 0 -> {
            text = stringResource(R.string.freedom_home_no_connection)
            fg = MaterialTheme.colorScheme.onErrorContainer
            bg = MaterialTheme.colorScheme.errorContainer
        }
        testing -> {
            text = "…"
            fg = MaterialTheme.colorScheme.onSurfaceVariant
            bg = MaterialTheme.colorScheme.surfaceContainerLow
        }
        else -> return
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FreedomPromoCard(onTry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.freedom_promo_card_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.freedom_promo_card_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onTry) { Text(stringResource(R.string.freedom_promo_card_try)) }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.freedom_promo_card_later)) }
            }
        }
    }
}

@Composable
private fun NoSubscription(onNavigate: (MainDestination) -> Unit) {
    Spacer(Modifier.height(48.dp))
    Text(
        stringResource(R.string.freedom_home_no_subscription),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onNavigate(MainDestination.Promotion) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.freedom_home_account))
    }
}

@Composable
private fun EmptyGroup(onUpdate: () -> Unit, onNavigate: (MainDestination) -> Unit) {
    Spacer(Modifier.height(48.dp))
    Text(
        stringResource(R.string.freedom_home_no_servers),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.freedom_home_no_servers_hint),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = { onNavigate(MainDestination.Promotion) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.freedom_home_account))
    }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.freedom_home_update))
    }
}

/** Whole days left until [expireMillis], rounded up ("ends in 3 h" counts as 1 day); 0 once expired. */
internal fun freedomDaysLeft(expireMillis: Long, nowMillis: Long): Int {
    val left = expireMillis - nowMillis
    if (left <= 0L) return 0
    return ((left + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Splits a server name like "🇳🇱 Netherlands" or "⚡ Авто" into its leading emoji (shown large)
 * and the rest. Returns null for the emoji when the name starts with a letter or digit.
 */
internal fun splitServerName(remarks: String): Pair<String?, String> {
    val trimmed = remarks.trim()
    if (trimmed.isEmpty()) return null to remarks
    val first = trimmed.codePointAt(0)
    if (Character.isLetterOrDigit(first) || first == '['.code || first == '('.code) return null to trimmed
    val space = trimmed.indexOf(' ')
    if (space <= 0) return null to trimmed
    val head = trimmed.substring(0, space)
    if (head.any { Character.isLetterOrDigit(it) }) return null to trimmed
    val rest = trimmed.substring(space + 1).trim()
    return if (rest.isEmpty()) null to trimmed else head to rest
}

/** The account subscription imported by Freedom VPN (see FreedomImporter.SUBSCRIPTION_NAME). */
internal fun isFreedomGroup(remarks: String): Boolean =
    remarks == com.v2ray.ang.freedom.FreedomImporter.SUBSCRIPTION_NAME
