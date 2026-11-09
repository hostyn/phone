package dev.hostyn.phone.ui.detail

import dev.hostyn.phone.ui.components.CallHistoryCard
import dev.hostyn.phone.data.CallLogRepository
import dev.hostyn.phone.ui.components.rememberOnResume
import dev.hostyn.phone.ui.components.isDefaultDialer
import dev.hostyn.phone.ui.components.BlockDialog
import dev.hostyn.phone.data.isBlocked
import dev.hostyn.phone.data.BlockedNumbersRepository
import androidx.compose.material.icons.rounded.Block
import dev.hostyn.phone.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import dev.hostyn.phone.ui.components.rememberMessagingButtons
import dev.hostyn.phone.ui.components.open
import dev.hostyn.phone.ui.components.MessagingButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
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
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Checkbox
import dev.hostyn.phone.data.shareContact
import dev.hostyn.phone.ui.components.FieldKind
import dev.hostyn.phone.ui.components.formatEventDate
import dev.hostyn.phone.ui.components.openUrl
import dev.hostyn.phone.ui.components.protocolLabel
import dev.hostyn.phone.ui.components.showOnMap
import dev.hostyn.phone.ui.components.typeLabel

private object Loading

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    repo: ContactsRepository,
    blocked: BlockedNumbersRepository,
    callLog: CallLogRepository,
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
    val messagingButtons = rememberMessagingButtons()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val blockedList by remember { blocked.observe() }.collectAsState(initial = emptyList())
    val canBlock = rememberOnResume { context.isDefaultDialer() }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val c = state as? ContactDetail ?: return@rememberLauncherForActivityResult
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        // The default ringtone is stored as “no custom ringtone”.
        val value = picked?.takeUnless { RingtoneManager.isDefault(it) }?.toString()
        scope.launch { repo.setRingtone(c.id, value) }
    }
    fun pickRingtone(c: ContactDetail) {
        ringtonePicker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_RINGTONE_URI)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    c.ringtone?.takeIf { it.isNotEmpty() }?.let(Uri::parse) ?: Settings.System.DEFAULT_RINGTONE_URI,
                )
            },
        )
    }

    // If the contact disappears (deleted here or from another app), go back.
    LaunchedEffect(state) { if (state == null) leave() }

    val contact = state as? ContactDetail
    val phones = contact?.phones?.map { it.value }.orEmpty()
    val isBlocked = phones.isNotEmpty() && phones.all { blockedList.isBlocked(it) }
    val history by remember(phones) { callLog.observeHistory(phones) }.collectAsState(initial = emptyList())
    val blockedMsg = stringResource(R.string.number_blocked)
    val unblockedMsg = stringResource(R.string.number_unblocked)
    val errorMsg = stringResource(R.string.error_block)
    fun setBlocked(block: Boolean) {
        scope.launch {
            val ok = if (block) blocked.block(phones) else blocked.unblock(phones)
            snackbar.showSnackbar(if (!ok) errorMsg else if (block) blockedMsg else unblockedMsg)
        }
    }

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
                                if (contact.lookupKey != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.share)) },
                                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                        onClick = { menu = false; scope.launch { shareContact(context, repo, contact) } },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ringtone)) },
                                    leadingIcon = { Icon(Icons.Rounded.MusicNote, null) },
                                    onClick = { menu = false; pickRingtone(contact) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.send_to_voicemail)) },
                                    leadingIcon = { Icon(Icons.Rounded.Voicemail, null) },
                                    trailingIcon = { Checkbox(checked = contact.sendToVoicemail, onCheckedChange = null) },
                                    onClick = { scope.launch { repo.setSendToVoicemail(contact.id, !contact.sendToVoicemail) } },
                                )
                                if (canBlock && phones.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (isBlocked) R.string.unblock_numbers else R.string.block_numbers)) },
                                        leadingIcon = { Icon(Icons.Rounded.Block, null) },
                                        onClick = {
                                            menu = false
                                            if (isBlocked) setBlocked(false) else confirmBlock = true
                                        },
                                    )
                                }
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
            val data = contact.data
            val org = data.organization.let { o ->
                listOf(listOf(o.title, o.department).filter { it.isNotBlank() }.joinToString(", "), o.company)
                    .filter { it.isNotBlank() }.joinToString(" · ")
            }
            listOf(data.nickname.value.takeIf { it.isNotBlank() }?.let { "“${it.trim()}”" }, org.ifBlank { null })
                .filterNotNull()
                .forEach { line ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            if (contact.groupTitles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    contact.groupTitles.forEach { title ->
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            if (isBlocked) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.blocked), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))

            val firstPhone = contact.phones.firstOrNull()?.value
            val firstEmail = contact.emails.firstOrNull()?.value
            val chats = if (firstPhone != null) messagingButtons else emptyList()
            QuickActionRow(3 + chats.size) { item ->
                QuickAction(Icons.Rounded.Call, stringResource(R.string.call), firstPhone != null, item) { firstPhone?.let(placeCall) }
                QuickAction(Icons.AutoMirrored.Rounded.Message, stringResource(R.string.message), firstPhone != null, item) { firstPhone?.let(context::sms) }
                chats.forEach { app -> QuickAction(app, firstPhone!!, item) }
                QuickAction(Icons.Rounded.MailOutline, stringResource(R.string.email), firstEmail != null, item) { firstEmail?.let(context::email) }
            }
            Spacer(Modifier.height(28.dp))

            val hasInfo = listOf(data.phones, data.emails, data.sips, data.ims, data.addresses, data.websites, data.events, data.relations)
                .any { it.isNotEmpty() }
            if (!hasInfo) {
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
                        data.sips.forEach { sip ->
                            InfoRow(
                                icon = Icons.Rounded.Dialpad,
                                value = sip.value,
                                label = "${stringResource(R.string.sip_address)} · ${FieldKind.Sips.label(context, sip.type, sip.label)}",
                                onClick = { placeCall("sip:" + sip.value.removePrefix("sip:")) },
                                onLongClick = { copy(sip.value) },
                            )
                        }
                        data.ims.forEach { im ->
                            InfoRow(
                                icon = Icons.Rounded.Chat,
                                value = im.handle,
                                label = im.protocolLabel(context),
                                onClick = { copy(im.handle) },
                                onLongClick = { copy(im.handle) },
                            )
                        }
                        data.addresses.forEach { a ->
                            InfoRow(
                                icon = Icons.Rounded.LocationOn,
                                value = a.display(),
                                label = a.typeLabel(context),
                                onClick = { context.showOnMap(a.display()) },
                                onLongClick = { copy(a.display()) },
                            )
                        }
                        data.websites.forEach { w ->
                            InfoRow(
                                icon = Icons.Rounded.Language,
                                value = w.value,
                                label = FieldKind.Websites.label(context, w.type, w.label),
                                onClick = { context.openUrl(w.value) },
                                onLongClick = { copy(w.value) },
                            )
                        }
                        data.events.forEach { e ->
                            val date = formatEventDate(e.value)
                            InfoRow(
                                icon = Icons.Rounded.Event,
                                value = date,
                                label = FieldKind.Events.label(context, e.type, e.label),
                                onClick = { copy(date) },
                                onLongClick = { copy(date) },
                            )
                        }
                        data.relations.forEach { r ->
                            InfoRow(
                                icon = Icons.Rounded.People,
                                value = r.value,
                                label = FieldKind.Relations.label(context, r.type, r.label),
                                onClick = { copy(r.value) },
                                onLongClick = { copy(r.value) },
                            )
                        }
                    }
                }
            }
            if (!data.note.isBlank) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            stringResource(R.string.notes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer { Text(data.note.value.trim(), style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                CallHistoryCard(history)
            }
        }
    }

    if (confirmBlock && contact != null) {
        BlockDialog(
            title = contact.displayName.ifBlank { formatPhone(context, phones.first()) },
            body = stringResource(R.string.block_contact_body),
            onDismiss = { confirmBlock = false },
            onConfirm = { confirmBlock = false; setBlocked(true) },
        )
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

/** A row of [QuickAction]s: spaced out when there are few, sharing the width when there are many. */
@Composable
internal fun QuickActionRow(count: Int, content: @Composable (item: Modifier) -> Unit) {
    if (count <= 3) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) { content(Modifier.width(64.dp)) }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            content(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier = Modifier.width(64.dp), onClick: () -> Unit) =
    QuickAction(label, enabled, modifier, onClick) { Icon(icon, null) }

/** A chat app's button: its own icon, which already has its colors. */
@Composable
internal fun QuickAction(app: MessagingButton, number: String, modifier: Modifier) {
    val context = LocalContext.current
    QuickAction(app.label, enabled = true, modifier = modifier, onClick = { app.open(context, number) }) {
        Image(app.icon, null, modifier = Modifier.size(30.dp))
    }
}

@Composable
internal fun QuickAction(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.widthIn(max = 64.dp).fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { icon() }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
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
