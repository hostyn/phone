package dev.hostyn.phone.ui.recents

import dev.hostyn.phone.ui.components.callTypeIcon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.Arrangement
import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hostyn.phone.call.MissedCallNotifier
import dev.hostyn.phone.data.BlockedNumbersRepository
import dev.hostyn.phone.data.isBlocked
import dev.hostyn.phone.ui.components.BlockDialog
import dev.hostyn.phone.ui.components.sms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import dev.hostyn.phone.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.provider.CallLog.Calls
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.hostyn.phone.data.CallLogRepository
import dev.hostyn.phone.data.RecentCall
import dev.hostyn.phone.data.formatPhone
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.EmptyState
import dev.hostyn.phone.ui.components.isDefaultDialer
import dev.hostyn.phone.ui.components.rememberDialerRoleRequest
import dev.hostyn.phone.ui.components.rememberOnResume
import dev.hostyn.phone.ui.components.segmentShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


private sealed interface RecentRow {
    val key: Any
    data class Day(val title: String) : RecentRow { override val key = "d_$title" }
    data class Entry(val call: RecentCall, val first: Boolean, val last: Boolean) : RecentRow { override val key = call.id }
}

private fun dayTitle(context: Context, date: LocalDate, today: LocalDate): String = when (date) {
    today -> context.getString(R.string.today)
    today.minusDays(1) -> context.getString(R.string.yesterday)
    else -> DateUtils.formatDateTime(
        context,
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_NO_YEAR,
    ).replaceFirstChar { it.titlecase() }
}

private fun buildRows(context: Context, calls: List<RecentCall>): List<RecentRow> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val rows = mutableListOf<RecentRow>()
    calls.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.forEach { (date, group) ->
        rows += RecentRow.Day(dayTitle(context, date, today))
        group.forEachIndexed { i, c -> rows += RecentRow.Entry(c, i == 0, i == group.lastIndex) }
    }
    return rows
}

@Composable
fun RecentsTab(
    callLog: CallLogRepository,
    blocked: BlockedNumbersRepository,
    contentPadding: PaddingValues,
    onCall: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenNumber: (String) -> Unit,
    onAddContact: (String) -> Unit,
) {
    val context = LocalContext.current
    val isDefault = rememberOnResume { context.isDefaultDialer() }
    val canRead = rememberOnResume {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }
    val requestRole = rememberDialerRoleRequest()
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    if (!canRead) {
        EmptyState(
            Icons.Rounded.History,
            stringResource(R.string.recents_title),
            if (!isDefault) stringResource(R.string.recents_need_default)
            else stringResource(R.string.recents_need_permission),
            Modifier.fillMaxSize().padding(contentPadding),
        ) {
            Button(onClick = { if (!isDefault) requestRole() else requestPermission.launch(Manifest.permission.READ_CALL_LOG) }) {
                Text(if (!isDefault) stringResource(R.string.set_default_phone_app) else stringResource(R.string.allow_access))
            }
        }
        return
    }

    // Looking at Recents counts as having seen the missed calls.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) MissedCallNotifier.clear(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var missedOnly by rememberSaveable { mutableStateOf(false) }
    val calls by remember(missedOnly) { callLog.observe(missedOnly) }.collectAsState(initial = null)
    val blockedList by remember { blocked.observe() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var toBlock by remember { mutableStateOf<RecentCall?>(null) }
    val blockedMsg = stringResource(R.string.number_blocked)
    val unblockedMsg = stringResource(R.string.number_unblocked)
    val errorMsg = stringResource(R.string.error_block)
    val actions = remember(blocked, callLog) {
        RecentActions(
            onBlock = { toBlock = it },
            onUnblock = { c ->
                scope.launch {
                    val ok = blocked.unblock(listOf(c.number))
                    Toast.makeText(context, if (ok) unblockedMsg else errorMsg, Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { c -> scope.launch(Dispatchers.IO) { callLog.delete(c.ids) } },
        )
    }
    toBlock?.let { c ->
        BlockDialog(
            title = c.name ?: formatPhone(context, c.number),
            body = stringResource(R.string.block_body),
            onDismiss = { toBlock = null },
            onConfirm = {
                toBlock = null
                scope.launch {
                    val ok = blocked.block(listOf(c.number))
                    Toast.makeText(context, if (ok) blockedMsg else errorMsg, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    val list = calls
    val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding())
    if (list != null && list.isEmpty() && !missedOnly) {
        EmptyState(
            Icons.Rounded.History,
            stringResource(R.string.no_recent_calls),
            stringResource(R.string.no_recent_calls_body),
            Modifier.fillMaxSize().padding(contentPadding),
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
        ) {
            FilterChip(
                selected = !missedOnly,
                onClick = { missedOnly = false },
                label = { Text(stringResource(R.string.filter_all)) },
                leadingIcon = if (!missedOnly) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
            )
            FilterChip(
                selected = missedOnly,
                onClick = { missedOnly = true },
                label = { Text(stringResource(R.string.filter_missed)) },
                leadingIcon = if (missedOnly) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
            )
        }
        Box(Modifier.weight(1f)) {
            when {
                list == null -> Box(Modifier.fillMaxSize().padding(bottom), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> EmptyState(
                    Icons.AutoMirrored.Rounded.CallMissed,
                    stringResource(R.string.no_missed_calls),
                    stringResource(R.string.no_missed_calls_body),
                    Modifier.fillMaxSize().padding(bottom),
                )
                else -> {
                    val rows = remember(list) { buildRows(context, list) }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 88.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is RecentRow.Day -> Text(
                                    row.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.animateItem().padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
                                )
                                is RecentRow.Entry -> RecentCallRow(
                                    row, blockedList.isBlocked(row.call.number), onCall, onOpenContact, onOpenNumber, onAddContact, actions,
                                    Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCallRow(
    row: RecentRow.Entry,
    isBlocked: Boolean,
    onCall: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenNumber: (String) -> Unit,
    onAddContact: (String) -> Unit,
    actions: RecentActions,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val view = LocalView.current
    val context = LocalContext.current
    val call = row.call
    val shape = segmentShape(row.first, row.last)
    val hidden = call.number.isBlank()
    val title = call.name ?: if (hidden) stringResource(R.string.private_number) else formatPhone(context, call.number)
    val colors = MaterialTheme.colorScheme

    val (icon, tint) = callTypeIcon(call.type, colors)
    val time = DateFormat.getTimeFormat(context).format(java.util.Date(call.date))
    val subtitle = listOfNotNull(call.numberLabel, time).joinToString(" · ")

    Box(
        modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = if (row.last) 0.dp else 2.dp),
    ) {
    Surface(
        shape = shape,
        color = colors.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = {
                    if (hidden) menu = true
                    else if (call.contactId != null) onOpenContact(call.contactId) else onOpenNumber(call.number)
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    menu = true
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            ContactAvatar(call.name ?: "", call.photoUri, call.contactId ?: call.number.hashCode().toLong(), size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (call.missed) colors.error else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (call.count > 1) {
                        Text(
                            " (${call.count})",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    if (isBlocked) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Block, stringResource(R.string.blocked), tint = colors.error, modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (!hidden) {
                IconButton(onClick = { onCall(call.number) }) {
                    Icon(Icons.Rounded.Call, stringResource(R.string.call), tint = colors.primary)
                }
            }
        }
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(56.dp, 0.dp)) {
        if (!hidden) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_number)) },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                onClick = { menu = false; clipboard.setText(AnnotatedString(call.number)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.send_message)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Message, null) },
                onClick = { menu = false; context.sms(call.number) },
            )
            if (call.contactId == null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_contacts)) },
                    leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
                    onClick = { menu = false; onAddContact(call.number) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (isBlocked) R.string.unblock_number else R.string.block_number)) },
                leadingIcon = { Icon(Icons.Rounded.Block, null) },
                onClick = { menu = false; if (isBlocked) actions.onUnblock(call) else actions.onBlock(call) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete_from_history)) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
            onClick = { menu = false; actions.onDelete(call) },
        )
    }
    }
}

private class RecentActions(
    val onBlock: (RecentCall) -> Unit,
    val onUnblock: (RecentCall) -> Unit,
    val onDelete: (RecentCall) -> Unit,
)
