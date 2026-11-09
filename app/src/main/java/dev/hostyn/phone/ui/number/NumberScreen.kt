package dev.hostyn.phone.ui.number

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.R
import dev.hostyn.phone.data.BlockedNumbersRepository
import dev.hostyn.phone.data.CallLogRepository
import dev.hostyn.phone.data.formatPhone
import dev.hostyn.phone.data.isBlocked
import dev.hostyn.phone.data.lookupNumber
import dev.hostyn.phone.ui.components.BlockDialog
import dev.hostyn.phone.ui.components.CallHistoryCard
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.isDefaultDialer
import dev.hostyn.phone.ui.components.rememberCallLauncher
import dev.hostyn.phone.ui.components.rememberOnResume
import dev.hostyn.phone.ui.components.sms
import dev.hostyn.phone.ui.detail.QuickAction
import dev.hostyn.phone.ui.components.rememberMessagingButtons
import dev.hostyn.phone.ui.detail.QuickActionRow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Details of a number that isn't a contact: actions and its call history. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NumberScreen(
    number: String,
    callLog: CallLogRepository,
    blocked: BlockedNumbersRepository,
    onBack: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onAddContact: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val placeCall = rememberCallLauncher()
    val history by remember(number) { callLog.observeHistory(listOf(number)) }.collectAsState(initial = null)
    val blockedList by remember { blocked.observe() }.collectAsState(initial = emptyList())
    val isBlocked = blockedList.isBlocked(number)
    val canBlock = rememberOnResume { context.isDefaultDialer() }
    var menu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val formatted = formatPhone(context, number)
    val messagingButtons = rememberMessagingButtons()
    val view = LocalView.current
    val snackbar = remember { SnackbarHostState() }
    val copyLabel = stringResource(R.string.copy_number)
    val copiedText = stringResource(R.string.copied)
    fun copy() {
        clipboard.setText(AnnotatedString(number))
        // Android 13+ shows its own clipboard preview; a snackbar would say the same thing twice.
        if (android.os.Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar(copiedText) }
    }

    // Saved as a contact in the meantime (e.g. after “Add”): show the contact instead.
    val contactId by produceState<Long?>(null, number) {
        value = withContext(Dispatchers.IO) { context.contentResolver.lookupNumber(number)?.contactId }
    }
    var left by remember { mutableStateOf(false) }
    LaunchedEffect(contactId) {
        contactId?.let { if (!left) { left = true; onOpenContact(it) } }
    }

    val blockedMsg = stringResource(R.string.number_blocked)
    val unblockedMsg = stringResource(R.string.number_unblocked)
    val errorMsg = stringResource(R.string.error_block)
    fun setBlocked(block: Boolean) {
        scope.launch {
            val ok = if (block) blocked.block(listOf(number)) else blocked.unblock(listOf(number))
            Toast.makeText(context, if (!ok) errorMsg else if (block) blockedMsg else unblockedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_number)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = { menu = false; copy() },
                            )
                            if (canBlock) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (isBlocked) R.string.unblock_number else R.string.block_number)) },
                                    leadingIcon = { Icon(Icons.Rounded.Block, null) },
                                    onClick = { menu = false; if (isBlocked) setBlocked(false) else confirmBlock = true },
                                )
                            }
                            if (!history.isNullOrEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_number_history)) },
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
            ContactAvatar("", null, number.hashCode().toLong(), size = 128.dp)
            Spacer(Modifier.height(20.dp))
            // Long-press the number to copy it, like the numbers of a contact.
            Text(
                formatted,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClickLabel = null, onLongClickLabel = copyLabel, onClick = {}, onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        copy()
                    })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (isBlocked) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.blocked), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
            QuickActionRow(3 + messagingButtons.size) { item ->
                QuickAction(Icons.Rounded.Call, stringResource(R.string.call), true, item) { placeCall(number) }
                QuickAction(Icons.AutoMirrored.Rounded.Message, stringResource(R.string.message), true, item) { context.sms(number) }
                messagingButtons.forEach { app -> QuickAction(app, number, item) }
                QuickAction(Icons.Rounded.PersonAdd, stringResource(R.string.add), true, item) { onAddContact(number) }
            }
            Spacer(Modifier.height(28.dp))
            CallHistoryCard(history.orEmpty())
        }
    }

    if (confirmBlock) {
        BlockDialog(
            title = formatted,
            body = stringResource(R.string.block_body),
            onDismiss = { confirmBlock = false },
            onConfirm = { confirmBlock = false; setBlocked(true) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(stringResource(R.string.delete_number_history)) },
            text = { Text(stringResource(R.string.delete_number_history_body, formatted)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val ids = history.orEmpty().map { it.id }
                    scope.launch {
                        withContext(Dispatchers.IO) { callLog.delete(ids) }
                        onBack()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
