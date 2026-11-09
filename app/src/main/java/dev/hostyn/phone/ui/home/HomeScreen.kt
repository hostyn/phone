@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.phone.ui.home

import androidx.annotation.StringRes
import dev.hostyn.phone.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import dev.hostyn.phone.ui.theme.LocalTransformOrigin
import dev.hostyn.phone.ui.theme.containerTransform
import dev.hostyn.phone.ui.theme.topLevelEnter
import dev.hostyn.phone.ui.theme.topLevelExit
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import dev.hostyn.phone.data.BlockedNumbersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.data.CallLogRepository
import dev.hostyn.phone.data.ContactsRepository
import dev.hostyn.phone.ui.components.DefaultDialerBanner
import dev.hostyn.phone.ui.components.SearchPill
import dev.hostyn.phone.ui.components.rememberCallLauncher
import dev.hostyn.phone.ui.contacts.ContactsTab
import dev.hostyn.phone.ui.recents.RecentsTab

/** Shared key between the dialpad button and the dialpad screen. */
const val DialpadTransformKey = "dialpad"

private class Tab(@StringRes val label: Int, val selected: ImageVector, val unselected: ImageVector)

private val Tabs = listOf(
    Tab(R.string.tab_recents, Icons.Rounded.Schedule, Icons.Outlined.Schedule),
    Tab(R.string.tab_contacts, Icons.Rounded.Person, Icons.Outlined.Person),
)

@Composable
fun HomeScreen(
    repo: ContactsRepository,
    callLog: CallLogRepository,
    blocked: BlockedNumbersRepository,
    onOpenContact: (Long) -> Unit,
    onCreateContact: (String?) -> Unit,
    onDialpad: () -> Unit,
    onSettings: () -> Unit,
    onOpenNumber: (String) -> Unit,
    recentsRequest: Int,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var tab by rememberSaveable { mutableIntStateOf(prefs.getInt("tab", 0)) }
    var query by rememberSaveable { mutableStateOf("") }
    val placeCall = rememberCallLauncher()

    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tab) { prefs.edit().putInt("tab", tab).apply() }
    LaunchedEffect(recentsRequest) {
        if (recentsRequest > 0) {
            tab = 0
            query = ""
        }
    }
    // Searching always shows contacts.
    LaunchedEffect(query) { if (query.isNotBlank()) tab = 1 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SearchPill(
                query = query,
                onQuery = { query = it },
                trailing = {
                    Box {
                        IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_history)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                                onClick = { menu = false; confirmClear = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                onClick = { menu = false; onSettings() },
                            )
                        }
                    }
                },
                modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        bottomBar = {
            // M3 Expressive flexible (short) navigation bar.
            ShortNavigationBar {
                Tabs.forEachIndexed { i, t ->
                    ShortNavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            if (i == 0) query = ""
                        },
                        icon = { Icon(if (tab == i) t.selected else t.unselected, null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            // The dialpad grows out of this button (container transform).
            val origin = LocalTransformOrigin.current
            MediumFloatingActionButton(
                onClick = { origin.key = DialpadTransformKey; onDialpad() },
                modifier = Modifier.containerTransform(DialpadTransformKey, FloatingActionButtonDefaults.mediumShape),
            ) {
                Icon(Icons.Rounded.Dialpad, stringResource(R.string.keypad), Modifier.size(FloatingActionButtonDefaults.MediumIconSize))
            }
        },
    ) { padding ->
        // M3 top level pattern: the old tab fades out quickly, then the new one fades in.
        AnimatedContent(
            targetState = tab,
            transitionSpec = { topLevelEnter() togetherWith topLevelExit() },
            label = "tab",
        ) { current ->
            when (current) {
                0 -> RecentsTab(
                    callLog = callLog,
                    blocked = blocked,
                    contentPadding = padding,
                    onCall = placeCall,
                    onOpenContact = onOpenContact,
                    onOpenNumber = onOpenNumber,
                    onAddContact = { onCreateContact(it) },
                )
                else -> ContactsTab(
                    repo = repo,
                    query = query,
                    contentPadding = padding,
                    onOpen = onOpenContact,
                    onCreate = { onCreateContact(null) },
                    header = { DefaultDialerBanner() },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) { callLog.clearAll() }
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
