package com.v2ray.ang.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.v2ray.ang.R

/** Freedom VPN: tabs of the bottom bar on the home screen. */
internal enum class HomeTab { Home, Servers, Account, More }

/** Everything the old side menu held besides the account: opened from the "More" tab. */
internal val moreMenuItems = listOf(
    MainDestination.Settings,
    MainDestination.PerAppProxy,
    MainDestination.Cabinet,
    MainDestination.Logcat,
    MainDestination.BackupRestore,
    MainDestination.CheckUpdate,
    MainDestination.About,
)

@Composable
internal fun HomeBottomBar(onSelect: (HomeTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        HomeTabItem(HomeTab.Home, R.drawable.ic_freedom_home_24dp, R.string.freedom_nav_home, selected = true, onSelect)
        HomeTabItem(HomeTab.Servers, R.drawable.ic_freedom_list_24dp, R.string.freedom_home_servers, selected = false, onSelect)
        HomeTabItem(HomeTab.Account, R.drawable.ic_freedom_person_24dp, R.string.freedom_account, selected = false, onSelect)
        HomeTabItem(HomeTab.More, R.drawable.ic_menu_24dp, R.string.freedom_nav_more, selected = false, onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HomeTabItem(
    tab: HomeTab,
    iconRes: Int,
    labelRes: Int,
    selected: Boolean,
    onSelect: (HomeTab) -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = { onSelect(tab) },
        icon = { Icon(painterResource(iconRes), contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
internal fun HomeMoreSheet(onDismiss: () -> Unit, onNavigate: (MainDestination) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.freedom_nav_more)) },
        text = {
            Column {
                moreMenuItems.forEach { item ->
                    ListItem(
                        headlineContent = { Text(stringResource(item.labelRes)) },
                        leadingContent = { Icon(painterResource(item.iconRes), contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(role = Role.Button) {
                            onDismiss()
                            onNavigate(item)
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
