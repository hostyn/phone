package dev.hostyn.contactos.ui.recents

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CallLog.Calls
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
import dev.hostyn.contactos.data.CallLogRepository
import dev.hostyn.contactos.data.RecentCall
import dev.hostyn.contactos.data.formatPhone
import dev.hostyn.contactos.ui.components.ContactAvatar
import dev.hostyn.contactos.ui.components.EmptyState
import dev.hostyn.contactos.ui.components.isDefaultDialer
import dev.hostyn.contactos.ui.components.rememberDialerRoleRequest
import dev.hostyn.contactos.ui.components.rememberOnResume
import dev.hostyn.contactos.ui.components.segmentShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Spanish = Locale("es", "ES")
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm", Spanish)
private val DayFmt = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Spanish)

private sealed interface RecentRow {
    val key: Any
    data class Day(val title: String) : RecentRow { override val key = "d_$title" }
    data class Entry(val call: RecentCall, val first: Boolean, val last: Boolean) : RecentRow { override val key = call.id }
}

private fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy"
    today.minusDays(1) -> "Ayer"
    else -> DayFmt.format(date).replaceFirstChar { it.titlecase(Spanish) }
}

private fun buildRows(calls: List<RecentCall>): List<RecentRow> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val rows = mutableListOf<RecentRow>()
    calls.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }.forEach { (date, group) ->
        rows += RecentRow.Day(dayTitle(date, today))
        group.forEachIndexed { i, c -> rows += RecentRow.Entry(c, i == 0, i == group.lastIndex) }
    }
    return rows
}

@Composable
fun RecentsTab(
    callLog: CallLogRepository,
    contentPadding: PaddingValues,
    onCall: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
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
            "Tus llamadas recientes",
            if (!isDefault) "Haz que esta app sea tu app de teléfono predeterminada para llamar y ver aquí tu historial."
            else "Permite el acceso al registro de llamadas para verlo aquí.",
            Modifier.fillMaxSize().padding(contentPadding),
        ) {
            Button(onClick = { if (!isDefault) requestRole() else requestPermission.launch(Manifest.permission.READ_CALL_LOG) }) {
                Text(if (!isDefault) "Establecer como app de teléfono" else "Permitir acceso")
            }
        }
        return
    }

    val calls by remember { callLog.observe() }.collectAsState(initial = null)
    val list = calls
    when {
        list == null -> Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> EmptyState(
            Icons.Rounded.History,
            "Sin llamadas recientes",
            "Las llamadas que hagas y recibas aparecerán aquí.",
            Modifier.fillMaxSize().padding(contentPadding),
        )
        else -> {
            val rows = remember(list) { buildRows(list) }
            LazyColumn(
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 88.dp,
                ),
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
                        is RecentRow.Entry -> RecentCallRow(row, onCall, onOpenContact, onAddContact, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCallRow(
    row: RecentRow.Entry,
    onCall: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
    onAddContact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val call = row.call
    val shape = segmentShape(row.first, row.last)
    val hidden = call.number.isBlank()
    val title = call.name ?: if (hidden) "Número oculto" else formatPhone(context, call.number)
    val colors = MaterialTheme.colorScheme

    val (icon, tint) = when (call.type) {
        Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade to colors.primary
        Calls.MISSED_TYPE -> Icons.AutoMirrored.Rounded.CallMissed to colors.error
        Calls.REJECTED_TYPE -> Icons.Rounded.CallEnd to colors.onSurfaceVariant
        Calls.BLOCKED_TYPE -> Icons.Rounded.Block to colors.onSurfaceVariant
        Calls.VOICEMAIL_TYPE -> Icons.Rounded.Voicemail to colors.onSurfaceVariant
        else -> Icons.AutoMirrored.Rounded.CallReceived to colors.tertiary
    }
    val time = TimeFmt.format(Instant.ofEpochMilli(call.date).atZone(ZoneId.systemDefault()))
    val subtitle = listOfNotNull(call.numberLabel, time).joinToString(" · ")

    Surface(
        shape = shape,
        color = colors.surfaceContainer,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = if (row.last) 0.dp else 2.dp)
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = !hidden) {
                if (call.contactId != null) onOpenContact(call.contactId) else onAddContact(call.number)
            },
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
                }
            }
            if (!hidden) {
                IconButton(onClick = { onCall(call.number) }) {
                    Icon(Icons.Rounded.Call, "Llamar", tint = colors.primary)
                }
            }
        }
    }
}
