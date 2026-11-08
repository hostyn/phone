package dev.hostyn.phone.call

import dev.hostyn.phone.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import dev.hostyn.phone.MainActivity
import dev.hostyn.phone.ui.components.segmentShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.PhonePaused
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.Dialpad
import kotlinx.coroutines.delay

val AnswerGreen = Color(0xFF1E8E3E)
val HangUpRed = Color(0xFFD93025)

@Composable
fun InCallRoute(
    onFinish: () -> Unit,
    onProximity: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    val calls by CallManager.calls.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val lastEnded by CallManager.lastEnded.collectAsState()

    val primary = CallManager.primary(calls)
    val finished = primary == null
    LaunchedEffect(finished) {
        if (finished) {
            delay(if (lastEnded != null) 1500 else 0)
            onFinish()
        }
    }
    val call = primary ?: lastEnded ?: return

    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val nearEar = (call.phase == CallPhase.ACTIVE || call.phase == CallPhase.DIALING) &&
        (route == CallAudioState.ROUTE_EARPIECE || route == CallAudioState.ROUTE_WIRED_HEADSET)
    LaunchedEffect(nearEar) { onProximity(nearEar) }
    LaunchedEffect(call.phase) { onKeepScreenOn(call.phase == CallPhase.RINGING) }

    val others = calls.filter { it.id != call.id && it.phase != CallPhase.ENDED }
    InCallScreen(call, others, audio)
}

@Composable
private fun InCallScreen(call: CallUi, others: List<CallUi>, audio: CallAudioState?) {
    val held = others.firstOrNull { it.phase == CallPhase.HOLDING }.takeIf { call.phase != CallPhase.RINGING }
    // Call waiting: the call we are already on while another one rings.
    val ongoing = if (call.phase == CallPhase.RINGING) others.firstOrNull { it.phase != CallPhase.RINGING } else null
    var keypad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val showKeypad = keypad && call.phase == CallPhase.ACTIVE

    // Opaque colors only: a translucent stop would mix with the window background, which follows
    // the system theme rather than the app's. The content color makes text readable in dark theme.
    val background = remember(colors) {
        Brush.verticalGradient(
            listOf(
                colors.surface,
                colors.surfaceContainer,
                colors.primaryContainer.copy(alpha = 0.45f).compositeOver(colors.surfaceContainer),
            ),
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            .background(background),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (held != null) HeldBanner(held)
                if (ongoing != null) OngoingBanner(ongoing)

                Spacer(Modifier.height(if (showKeypad) 16.dp else 40.dp))
                StatusText(call)
                Spacer(Modifier.height(if (showKeypad) 8.dp else 28.dp))

                if (!showKeypad) {
                    CallerAvatar(call)
                    Spacer(Modifier.height(24.dp))
                }
                Text(
                    call.name,
                    style = if (showKeypad) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showKeypad) {
                    Text(
                        dtmf.ifEmpty { " " },
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (call.label != null) {
                    Text(
                        call.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                AnimatedContent(
                    targetState = when {
                        call.phase == CallPhase.SELECT_ACCOUNT -> 0
                        call.phase == CallPhase.RINGING -> 1
                        call.phase == CallPhase.ENDED -> 2
                        showKeypad -> 3
                        else -> 4
                    },
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                    label = "controls",
                ) { mode ->
                    when (mode) {
                        0 -> AccountPicker(call)
                        1 -> IncomingActions(call, ongoing)
                        2 -> Spacer(Modifier.height(120.dp))
                        3 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Dialpad(
                                onKeyDown = { CallManager.startDtmf(call.id, it); dtmf += it },
                                onKeyUp = { CallManager.stopDtmf(call.id) },
                                keyHeight = 58.dp,
                                keyColor = colors.surfaceContainerHighest.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            TextButton(onClick = { keypad = false }, modifier = Modifier.padding(top = 4.dp)) {
                                Text(stringResource(R.string.hide_keypad))
                            }
                        }
                        else -> CallControls(call, held, audio, onKeypad = { keypad = true })
                    }
                }

                if (call.phase != CallPhase.RINGING && call.phase != CallPhase.ENDED) {
                    Spacer(Modifier.height(28.dp))
                    RoundButton(Icons.Rounded.CallEnd, HangUpRed, stringResource(R.string.hang_up)) { CallManager.hangUp(call.id) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatusText(call: CallUi) {
    val text = when (call.phase) {
        CallPhase.RINGING -> stringResource(R.string.incoming_call)
        CallPhase.SELECT_ACCOUNT -> stringResource(R.string.choose_sim_for_call)
        CallPhase.DIALING -> stringResource(R.string.calling)
        CallPhase.HOLDING -> stringResource(R.string.on_hold)
        CallPhase.ENDED -> call.endReason ?: stringResource(R.string.call_ended)
        CallPhase.ACTIVE -> if (call.connectTime > 0) elapsed(call.connectTime) else stringResource(R.string.connected)
    }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (call.phase == CallPhase.ENDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun elapsed(since: Long): String {
    val now by produceState(System.currentTimeMillis(), since) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    val s = ((now - since) / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun CallerAvatar(call: CallUi) {
    val ringing = call.phase == CallPhase.RINGING || call.phase == CallPhase.DIALING
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        if (ringing) {
            val t = rememberInfiniteTransition(label = "pulse")
            listOf(0, 900).forEach { offset ->
                val progress by t.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(1800, delayMillis = 0, easing = LinearEasing),
                        RepeatMode.Restart,
                        initialStartOffset = androidx.compose.animation.core.StartOffset(offset),
                    ),
                    label = "ring",
                )
                Box(
                    Modifier
                        .size(136.dp)
                        .graphicsLayer {
                            val scale = 1f + progress * 0.45f
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - progress) * 0.5f
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        if (call.isConference) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(136.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(64.dp))
                }
            }
        } else {
            ContactAvatar(call.name, call.photoUri, call.contactId ?: call.number.hashCode().toLong(), size = 136.dp)
        }
    }
}

@Composable
private fun IncomingActions(call: CallUi, ongoing: CallUi?) {
    val t = rememberInfiniteTransition(label = "answer")
    val bounce by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce",
    )
    var replying by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (call.canReplyWithText) {
            FilledTonalButton(onClick = { replying = true }, modifier = Modifier.padding(bottom = 28.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Message, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.message))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            RoundButton(Icons.Rounded.CallEnd, HangUpRed, stringResource(R.string.decline)) { CallManager.reject(call.id) }
            if (ongoing == null) {
                RoundButton(
                    Icons.Rounded.Call, AnswerGreen, stringResource(R.string.answer),
                    modifier = Modifier.graphicsLayer { translationY = -bounce * 10.dp.toPx() },
                ) { CallManager.answer(call.id) }
            } else {
                RoundButton(Icons.Rounded.PhoneForwarded, AnswerGreen, stringResource(R.string.end_and_answer)) {
                    CallManager.endAndAnswer(call.id)
                }
                if (ongoing.canHold || ongoing.phase == CallPhase.HOLDING) {
                    RoundButton(
                        Icons.Rounded.PhonePaused, AnswerGreen, stringResource(R.string.hold_and_answer),
                        modifier = Modifier.graphicsLayer { translationY = -bounce * 10.dp.toPx() },
                    ) { CallManager.answer(call.id) }
                }
            }
        }
    }
    if (replying) {
        QuickReplySheet(
            onDismiss = { replying = false },
            onSend = { text ->
                replying = false
                CallManager.rejectWithMessage(call.id, text)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplySheet(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val presets = listOf(
        stringResource(R.string.quick_reply_1),
        stringResource(R.string.quick_reply_2),
        stringResource(R.string.quick_reply_3),
        stringResource(R.string.quick_reply_4),
    )
    var custom by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding()) {
            Text(
                stringResource(R.string.reply_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
            val text = custom
            if (text == null) {
                presets.forEachIndexed { i, p ->
                    val shape = segmentShape(i == 0, false)
                    Surface(
                        onClick = { onSend(p) },
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    ) {
                        Text(p, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
                    }
                }
                Surface(
                    onClick = { custom = "" },
                    shape = segmentShape(false, true),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.quick_reply_custom), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { custom = it },
                        shape = RoundedCornerShape(28.dp),
                        maxLines = 4,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { onSend(text.trim()) }, enabled = text.isNotBlank(), modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send))
                    }
                }
            }
        }
    }
}

@Composable
private fun OngoingBanner(ongoing: CallUi) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Icon(
                if (ongoing.phase == CallPhase.HOLDING) Icons.Rounded.Pause else Icons.Rounded.PhoneInTalk,
                null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.in_call_with, ongoing.name),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    color: Color,
    label: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = color,
            contentColor = Color.White,
            shadowElevation = 2.dp,
            modifier = modifier.size(76.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, modifier = Modifier.size(32.dp))
            }
        }
        if (label != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.width(104.dp),
            )
        }
    }
}

@Composable
private fun CallControls(call: CallUi, held: CallUi?, audio: CallAudioState?, onKeypad: () -> Unit) {
    val context = LocalContext.current
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val supported = audio?.supportedRouteMask ?: (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
    val hasBluetooth = supported and CallAudioState.ROUTE_BLUETOOTH != 0
    val hasWired = supported and CallAudioState.ROUTE_WIRED_HEADSET != 0
    var routeMenu by remember { mutableStateOf(false) }
    val bluetoothLabel = stringResource(R.string.bluetooth)
    val speakerLabel = stringResource(R.string.speaker)
    val headsetLabel = stringResource(R.string.headset)
    val earpieceLabel = stringResource(R.string.earpiece)
    val active = call.phase == CallPhase.ACTIVE || call.phase == CallPhase.HOLDING

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ToggleControl(Icons.Rounded.MicOff, stringResource(R.string.mute), checked = muted) { CallManager.setMuted(!muted) }
        ToggleControl(Icons.Rounded.Dialpad, stringResource(R.string.keypad), checked = false, enabled = call.phase == CallPhase.ACTIVE, onClick = onKeypad)
        Box {
            if (hasBluetooth) {
                val (icon, label) = when (route) {
                    CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth to bluetoothLabel
                    CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp to speakerLabel
                    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset to headsetLabel
                    else -> Icons.Rounded.PhoneInTalk to earpieceLabel
                }
                ToggleControl(icon, label, checked = route != CallAudioState.ROUTE_EARPIECE) { routeMenu = true }
                DropdownMenu(expanded = routeMenu, onDismissRequest = { routeMenu = false }) {
                    val options = buildList {
                        add(Triple(CallAudioState.ROUTE_BLUETOOTH, bluetoothLabel, Icons.Rounded.Bluetooth))
                        add(Triple(CallAudioState.ROUTE_SPEAKER, speakerLabel, Icons.AutoMirrored.Rounded.VolumeUp))
                        if (hasWired) add(Triple(CallAudioState.ROUTE_WIRED_HEADSET, headsetLabel, Icons.Rounded.Headset))
                        else add(Triple(CallAudioState.ROUTE_EARPIECE, earpieceLabel, Icons.Rounded.PhoneInTalk))
                    }
                    options.forEach { (r, name, ic) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            leadingIcon = { Icon(ic, null) },
                            trailingIcon = { if (route == r) Text("✓", color = MaterialTheme.colorScheme.primary) },
                            onClick = { routeMenu = false; CallManager.setRoute(r) },
                        )
                    }
                }
            } else {
                val speaker = route == CallAudioState.ROUTE_SPEAKER
                ToggleControl(Icons.AutoMirrored.Rounded.VolumeUp, speakerLabel, checked = speaker) {
                    CallManager.setRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ToggleControl(Icons.Rounded.AddIcCall, stringResource(R.string.add_call), checked = false, enabled = call.phase == CallPhase.ACTIVE) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_DIAL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        if (held != null) {
            // Two calls: swap between them.
            ToggleControl(Icons.Rounded.SwapCalls, stringResource(R.string.swap), checked = false) { CallManager.setHold(held.id, false) }
        } else {
            val onHold = call.phase == CallPhase.HOLDING
            ToggleControl(Icons.Rounded.Pause, if (onHold) stringResource(R.string.resume) else stringResource(R.string.hold), checked = onHold, enabled = active && call.canHold) {
                CallManager.setHold(call.id, !onHold)
            }
        }
        if (held != null && call.canMerge) {
            ToggleControl(Icons.Rounded.CallMerge, stringResource(R.string.merge), checked = false) { CallManager.merge(call.id) }
        } else {
            Spacer(Modifier.width(88.dp))
        }
    }
    }
}

@Composable
private fun ToggleControl(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // “Expressive” shape: circle when off, rounded square when on.
    val corner by animateDpAsState(if (checked) 22.dp else 34.dp, label = "corner")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(corner),
            color = if (checked) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (checked) colors.onPrimary else colors.onSurface,
            modifier = Modifier.size(68.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun HeldBanner(held: CallUi) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Icon(Icons.Rounded.Pause, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.on_hold), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(held.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { CallManager.setHold(held.id, false) }) {
                Icon(Icons.Rounded.SwapCalls, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.swap))
            }
        }
    }
}

@Composable
private fun AccountPicker(call: CallUi) {
    val context = LocalContext.current
    val accounts: List<Pair<PhoneAccountHandle, String>> = remember(call.accounts) {
        val tm = context.getSystemService(TelecomManager::class.java)
        call.accounts.mapIndexed { i, h ->
            val label = try { tm.getPhoneAccount(h)?.label?.toString() } catch (_: Exception) { null }
            h to (label ?: context.getString(R.string.sim_number, i + 1))
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { (handle, label) ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { CallManager.selectAccount(call.id, handle) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Rounded.SimCard, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.no_sim), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
