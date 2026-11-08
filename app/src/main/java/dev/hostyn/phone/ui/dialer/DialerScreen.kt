package dev.hostyn.phone.ui.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.CallLog
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.hostyn.phone.call.AnswerGreen
import dev.hostyn.phone.data.ContactSummary
import dev.hostyn.phone.data.ContactsRepository
import dev.hostyn.phone.data.formatPhone
import dev.hostyn.phone.data.samePhone
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.Dialpad
import dev.hostyn.phone.ui.components.foldAccents
import dev.hostyn.phone.ui.components.rememberCallLauncher

private class Suggestion(val contact: ContactSummary, val number: String)

private val T9: Map<Char, Char> = buildMap {
    "abc def ghi jkl mno pqrs tuv wxyz".split(' ').forEachIndexed { i, letters ->
        letters.forEach { put(it, '2' + i) }
    }
}

private fun t9(text: String): String = text.foldAccents().map { if (it.isDigit()) it else T9[it] ?: '1' }.joinToString("")

/** Suggestions: first by T9 name (4-6-7… = “Gor…”), then by number. */
private fun suggest(input: String, contacts: List<ContactSummary>): List<Suggestion> {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return emptyList()
    val byName = mutableListOf<Suggestion>()
    val byNumber = mutableListOf<Suggestion>()
    for (c in contacts) {
        if (c.numbers.isEmpty()) continue
        val words = c.name.split(' ', '-', '.').filter { it.isNotBlank() }
        val nameMatch = words.any { t9(it).startsWith(digits) }
        if (nameMatch) {
            byName += Suggestion(c, c.numbers.first())
        } else {
            val n = c.numbers.firstOrNull { num -> num.filter { it.isDigit() }.contains(digits) }
            if (n != null) byNumber += Suggestion(c, n)
        }
    }
    return (byName + byNumber).distinctBy { it.contact.id }.take(3)
}

private class DialTones(context: Context) {
    private val enabled = Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
    private val generator = if (enabled) {
        try { ToneGenerator(AudioManager.STREAM_DTMF, 70) } catch (_: RuntimeException) { null }
    } else null

    fun play(c: Char) {
        val tone = when (c) {
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (c - '0')
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        generator?.startTone(tone, 120)
    }

    fun release() = generator?.release()
}

private fun lastOutgoingNumber(context: Context): String =
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
        try { CallLog.Calls.getLastOutgoingCall(context).orEmpty() } catch (_: Exception) { "" }
    } else ""

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    repo: ContactsRepository,
    initialNumber: String,
    onBack: () -> Unit,
    onAddContact: (String) -> Unit,
) {
    val context = LocalContext.current
    val call = rememberCallLauncher()
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    val contacts by remember { repo.observeContacts() }.collectAsState(initial = emptyList())
    val suggestions = remember(number, contacts) { suggest(number, contacts) }
    val known = remember(number, contacts) { contacts.any { c -> c.numbers.any { samePhone(it, number) } } }

    val tones = remember { DialTones(context) }
    DisposableEffect(Unit) { onDispose { tones.release() } }

    val display = formatPhone(context, number)
    val numberStyle = when {
        display.length > 17 -> MaterialTheme.typography.headlineSmall
        display.length > 13 -> MaterialTheme.typography.headlineLarge
        else -> MaterialTheme.typography.displaySmall
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(bottom = 20.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }

            // Suggestions, pinned to the bottom of the free space.
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                suggestions.forEach { s ->
                    SuggestionRow(s, onClick = { call(s.number) })
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 8.dp),
            ) {
                Spacer(Modifier.width(48.dp))
                Text(
                    display,
                    style = numberStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(visible = number.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = { number = number.dropLast(1) },
                                    onLongClick = { number = "" },
                                    onClickLabel = "Delete",
                                ),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = number.isNotEmpty() && !known, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = { onAddContact(number) }) {
                        Icon(Icons.Rounded.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to contacts")
                    }
                }
            }

            Dialpad(
                onKeyDown = { c ->
                    tones.play(c)
                    number += c
                },
                onLongPress = { c ->
                    if (c == '0' && number.endsWith('0')) number = number.dropLast(1) + "+"
                },
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = {
                        if (number.isBlank()) number = lastOutgoingNumber(context) else call(number)
                    },
                    shape = CircleShape,
                    color = AnswerGreen,
                    contentColor = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(width = 112.dp, height = 68.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Call, "Call", modifier = Modifier.size(30.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ContactAvatar(s.contact.name, s.contact.photoUri, s.contact.id, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(s.contact.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatPhone(context, s.number), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.Call, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
    }
}
