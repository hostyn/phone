package dev.hostyn.contactos.ui.home

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import dev.hostyn.contactos.data.CallLogRepository
import dev.hostyn.contactos.data.ContactsRepository
import dev.hostyn.contactos.ui.components.DefaultDialerBanner
import dev.hostyn.contactos.ui.components.SearchPill
import dev.hostyn.contactos.ui.components.rememberCallLauncher
import dev.hostyn.contactos.ui.contacts.ContactsTab
import dev.hostyn.contactos.ui.recents.RecentsTab

private class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector)

private val Tabs = listOf(
    Tab("Recientes", Icons.Rounded.Schedule, Icons.Outlined.Schedule),
    Tab("Contactos", Icons.Rounded.Person, Icons.Outlined.Person),
)

@Composable
fun HomeScreen(
    repo: ContactsRepository,
    callLog: CallLogRepository,
    onOpenContact: (Long) -> Unit,
    onCreateContact: (String?) -> Unit,
    onDialpad: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var tab by rememberSaveable { mutableIntStateOf(prefs.getInt("tab", 0)) }
    var query by rememberSaveable { mutableStateOf("") }
    val placeCall = rememberCallLauncher()

    LaunchedEffect(tab) { prefs.edit().putInt("tab", tab).apply() }
    // Buscar siempre muestra contactos.
    LaunchedEffect(query) { if (query.isNotBlank()) tab = 1 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SearchPill(
                query = query,
                onQuery = { query = it },
                modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        bottomBar = {
            NavigationBar {
                Tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            if (i == 0) query = ""
                        },
                        icon = { Icon(if (tab == i) t.selected else t.unselected, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onDialpad,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Rounded.Dialpad, "Teclado")
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, label = "tab") { current ->
            when (current) {
                0 -> RecentsTab(
                    callLog = callLog,
                    contentPadding = padding,
                    onCall = placeCall,
                    onOpenContact = onOpenContact,
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
}
