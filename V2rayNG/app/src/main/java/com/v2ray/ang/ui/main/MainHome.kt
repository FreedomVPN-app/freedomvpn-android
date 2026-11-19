package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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


/**
 * Freedom VPN: simple home screen — subscription card, one big connect button and a server picker.
 * State comes from [MainViewModel]; every user operation goes through [onAction] / [onNavigate].
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
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (group == null) {
            NoSubscription(onNavigate)
            return@Column
        }

        val groupState by mainViewModel.serverGroupState(group.id).collectAsStateWithLifecycle()
        val rows = groupState.rows
        val selected = rows.firstOrNull { it.guid == uiState.selectedGuid }

        group.usageInfo?.let { info ->
            SubscriptionInfoCard(
                info = info,
                fallbackTitle = group.remarks,
                onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                onRefresh = { onAction(MainAction.UpdateGroupSubscription(group.id)) },
            )
        }

        Spacer(Modifier.height(36.dp))
        ConnectButton(isRunning = uiState.isRunning, onClick = { onAction(MainAction.ToggleService) })
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (uiState.isRunning) R.string.freedom_home_connected else R.string.freedom_home_disconnected
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (uiState.isRunning && displayText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onAction(MainAction.TestCurrentServer) }) {
                Text(displayText, textAlign = TextAlign.Center, maxLines = 2)
            }
        }

        Spacer(Modifier.height(28.dp))
        ServerSelector(
            title = selected?.remarks ?: stringResource(R.string.freedom_home_choose_server),
            delay = selected?.testDelayMillis ?: 0L,
            testing = uiState.isTesting,
            enabled = rows.isNotEmpty(),
            onClick = { showPicker = true },
        )

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = { onNavigate(MainDestination.Promotion) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_freedom_person_24dp), contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.freedom_home_account))
        }
    }

    if (showPicker && group != null) {
        val groupState by mainViewModel.serverGroupState(group.id).collectAsStateWithLifecycle()
        ServerPickerDialog(
            rows = groupState.rows,
            selectedGuid = uiState.selectedGuid,
            testing = uiState.isTesting,
            onTest = { onAction(MainAction.TestRealAllServers) },
            onSelect = { guid ->
                showPicker = false
                onAction(MainAction.SelectServer(guid))
            },
            onDismiss = { showPicker = false },
        )
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
private fun ConnectButton(isRunning: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val container by animateColorAsState(
        if (isRunning) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "connectContainer",
    )
    val content = if (isRunning) MaterialTheme.colorScheme.onPrimary else accent
    val label = stringResource(if (isRunning) R.string.freedom_home_disconnect else R.string.freedom_home_connect)
    val state = stringResource(if (isRunning) R.string.freedom_home_connected else R.string.freedom_home_disconnected)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        border = BorderStroke(6.dp, accent.copy(alpha = if (isRunning) 0.3f else 0.15f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(180.dp)
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
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(label, color = content, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ServerSelector(title: String, delay: Long, testing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val (flag, name) = splitServerName(title)
    val label = stringResource(R.string.freedom_home_server)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label: $title" },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (flag != null) {
                Text(flag, fontSize = 28.sp)
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DelayLabel(delay, testing)
            Spacer(Modifier.size(8.dp))
            Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null)
        }
    }
}

@Composable
private fun ServerPickerDialog(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    testing: Boolean,
    onTest: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Measure every server once when the list opens (results stay until the next test).
    LaunchedEffect(Unit) {
        if (!testing && rows.isNotEmpty() && rows.all { it.testDelayMillis == 0L }) onTest()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.freedom_home_choose_server)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(rows, key = { it.guid }) { row ->
                    val isSelected = row.guid == selectedGuid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(row.guid) },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        val (flag, name) = splitServerName(row.remarks)
                        Text(flag ?: "", fontSize = 22.sp, modifier = Modifier.size(width = 32.dp, height = 30.dp))
                        Text(
                            name,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        DelayLabel(row.testDelayMillis, testing)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTest, enabled = !testing) {
                Text(stringResource(R.string.freedom_home_test))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/** Measured delay: green / amber / red by speed, "no connection" on failure, "…" while testing. */
@Composable
private fun DelayLabel(delay: Long, testing: Boolean) {
    val text: String
    val color: Color
    when {
        delay > 0 -> {
            text = stringResource(R.string.freedom_home_delay, delay)
            color = when {
                delay < 300 -> Color(0xFF2E9E6A)
                delay < 800 -> Color(0xFFC9900A)
                else -> Color(0xFFD9534F)
            }
        }
        delay < 0 -> {
            text = stringResource(R.string.freedom_home_no_connection)
            color = MaterialTheme.colorScheme.error
        }
        testing -> {
            text = "…"
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> return
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
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
