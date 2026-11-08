package dev.hostyn.phone.ui.detail

import dev.hostyn.phone.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.data.ContactDetail
import dev.hostyn.phone.data.ContactsRepository
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.rememberCallLauncher
import dev.hostyn.phone.data.formatPhone
import dev.hostyn.phone.ui.components.email
import dev.hostyn.phone.ui.components.emailLabel
import dev.hostyn.phone.ui.components.phoneLabel
import dev.hostyn.phone.ui.components.sms
import kotlinx.coroutines.launch

private object Loading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repo: ContactsRepository,
    contactId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    var left by remember { mutableStateOf(false) }
    val leave = { if (!left) { left = true; onBack() } }
    val state by remember(contactId) { repo.observeContact(contactId) }.collectAsState(initial = Loading)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val placeCall = rememberCallLauncher()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // If the contact disappears (deleted here or from another app), go back.
    LaunchedEffect(state) { if (state == null) leave() }

    val contact = state as? ContactDetail

    val copiedText = stringResource(R.string.copied)
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar(copiedText) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (contact != null) {
                        IconButton(onClick = { scope.launch { repo.setStarred(contact.id, !contact.starred) } }) {
                            if (contact.starred) Icon(Icons.Rounded.Star, stringResource(R.string.remove_favorite), tint = MaterialTheme.colorScheme.primary)
                            else Icon(Icons.Rounded.StarOutline, stringResource(R.string.add_favorite))
                        }
                        IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (contact == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            ContactAvatar(contact.displayName, contact.photoUri, contact.id, size = 128.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                contact.displayName.ifBlank { stringResource(R.string.no_name) },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))

            val firstPhone = contact.phones.firstOrNull()?.value
            val firstEmail = contact.emails.firstOrNull()?.value
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                QuickAction(Icons.Rounded.Call, stringResource(R.string.call), firstPhone != null) { firstPhone?.let(placeCall) }
                QuickAction(Icons.AutoMirrored.Rounded.Message, stringResource(R.string.message), firstPhone != null) { firstPhone?.let(context::sms) }
                QuickAction(Icons.Rounded.MailOutline, stringResource(R.string.email), firstEmail != null) { firstEmail?.let(context::email) }
            }
            Spacer(Modifier.height(28.dp))

            if (contact.phones.isEmpty() && contact.emails.isEmpty()) {
                Text(
                    stringResource(R.string.contact_no_info),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.contact_info),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                        contact.phones.forEach { p ->
                            InfoRow(
                                icon = Icons.Rounded.Phone,
                                value = formatPhone(context, p.value),
                                label = p.phoneLabel(context),
                                onClick = { placeCall(p.value) },
                                onLongClick = { copy(p.value) },
                                trailing = {
                                    IconButton(onClick = { context.sms(p.value) }) {
                                        Icon(Icons.AutoMirrored.Rounded.Message, stringResource(R.string.send_message))
                                    }
                                },
                            )
                        }
                        contact.emails.forEach { e ->
                            InfoRow(
                                icon = Icons.Rounded.Email,
                                value = e.value,
                                label = e.emailLabel(context),
                                onClick = { context.email(e.value) },
                                onLongClick = { copy(e.value) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && contact != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(stringResource(R.string.delete_contact_title)) },
            text = { Text(stringResource(R.string.delete_contact_message, contact.displayName)) },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch { repo.delete(contact.id); leave() }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(width = 64.dp, height = 52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { Icon(icon, null) }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoRow(
    icon: ImageVector,
    value: String,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
