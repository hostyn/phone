package dev.hostyn.phone.ui.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SegmentedListItem

import android.content.Context
import android.provider.CallLog.Calls
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.R
import dev.hostyn.phone.data.CallEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Icon and color for a call log type. */
fun callTypeIcon(type: Int, colors: ColorScheme): Pair<ImageVector, Color> = when (type) {
    Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade to colors.primary
    Calls.MISSED_TYPE -> Icons.AutoMirrored.Rounded.CallMissed to colors.error
    Calls.REJECTED_TYPE -> Icons.Rounded.CallEnd to colors.onSurfaceVariant
    Calls.BLOCKED_TYPE -> Icons.Rounded.Block to colors.onSurfaceVariant
    Calls.VOICEMAIL_TYPE -> Icons.Rounded.Voicemail to colors.onSurfaceVariant
    else -> Icons.AutoMirrored.Rounded.CallReceived to colors.tertiary
}

private fun callTypeLabel(type: Int): Int = when (type) {
    Calls.OUTGOING_TYPE -> R.string.call_outgoing
    Calls.MISSED_TYPE -> R.string.missed_call
    Calls.REJECTED_TYPE -> R.string.call_rejected
    Calls.BLOCKED_TYPE -> R.string.call_blocked
    Calls.VOICEMAIL_TYPE -> R.string.voicemail
    else -> R.string.call_incoming
}

/** "Today, 11:03", "Yesterday, 9:15", "Mon, 3 Nov, 18:40". */
fun formatCallTime(context: Context, millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val time = DateFormat.getTimeFormat(context).format(Date(millis))
    val day = when (date) {
        today -> context.getString(R.string.today)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> {
            var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
            if (date.year == today.year) flags = flags or DateUtils.FORMAT_NO_YEAR
            DateUtils.formatDateTime(context, millis, flags).replaceFirstChar { it.titlecase() }
        }
    }
    return "$day, $time"
}

/** "45 s", "3 min 12 s", "1 h 05 min". */
fun formatDuration(context: Context, seconds: Long): String = when {
    seconds < 60 -> context.getString(R.string.duration_seconds, seconds.toInt())
    seconds < 3600 -> context.getString(R.string.duration_minutes, (seconds / 60).toInt(), (seconds % 60).toInt())
    else -> context.getString(R.string.duration_hours, (seconds / 3600).toInt(), (seconds / 60 % 60).toInt())
}

private const val Collapsed = 5

/** Segmented list with the calls to/from a contact or number. Shows nothing if there are none. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CallHistoryCard(entries: List<CallEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) entries else entries.take(Collapsed)

    Column(modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        ListHeader(stringResource(R.string.call_history))
        SegmentedGroup {
            shown.forEach { e ->
                item { shapes ->
                    val (icon, tint) = callTypeIcon(e.type, colors)
                    val answered = e.type == Calls.INCOMING_TYPE || e.type == Calls.OUTGOING_TYPE
                    val details = listOfNotNull(
                        formatCallTime(context, e.date),
                        e.duration.takeIf { answered && it > 0 }?.let { formatDuration(context, it) },
                    ).joinToString(" · ")
                    SegmentedListItem(
                        shapes = shapes,
                        colors = segmentedColors(),
                        leadingContent = { Icon(icon, null, tint = tint) },
                        supportingContent = { Text(details) },
                    ) {
                        Text(
                            stringResource(callTypeLabel(e.type)),
                            color = if (e.type == Calls.MISSED_TYPE) colors.error else colors.onSurface,
                        )
                    }
                }
            }
        }
        if (entries.size > Collapsed) {
            TextButton(
                onClick = { expanded = !expanded },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            ) {
                Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
            }
        }
    }
}
