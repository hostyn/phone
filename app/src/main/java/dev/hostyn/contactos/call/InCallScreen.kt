package dev.hostyn.contactos.call

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hostyn.contactos.ui.components.ContactAvatar
import dev.hostyn.contactos.ui.components.Dialpad
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

    val held = calls.firstOrNull { it.phase == CallPhase.HOLDING && it.id != call.id }
    InCallScreen(call, held, audio)
}

@Composable
private fun InCallScreen(call: CallUi, held: CallUi?, audio: CallAudioState?) {
    var keypad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val showKeypad = keypad && call.phase == CallPhase.ACTIVE

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.surface, colors.surfaceContainer, colors.primaryContainer.copy(alpha = 0.55f)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (held != null) HeldBanner(held)

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
                    1 -> IncomingActions(call)
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
                            Text("Ocultar teclado")
                        }
                    }
                    else -> CallControls(call, audio, onKeypad = { keypad = true })
                }
            }

            if (call.phase != CallPhase.RINGING && call.phase != CallPhase.ENDED) {
                Spacer(Modifier.height(28.dp))
                RoundButton(Icons.Rounded.CallEnd, HangUpRed, "Colgar") { CallManager.hangUp(call.id) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusText(call: CallUi) {
    val text = when (call.phase) {
        CallPhase.RINGING -> "Llamada entrante"
        CallPhase.SELECT_ACCOUNT -> "Elige con qué SIM llamar"
        CallPhase.DIALING -> "Llamando…"
        CallPhase.HOLDING -> "En espera"
        CallPhase.ENDED -> call.endReason ?: "Llamada finalizada"
        CallPhase.ACTIVE -> if (call.connectTime > 0) elapsed(call.connectTime) else "Conectada"
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
        ContactAvatar(call.name, call.photoUri, call.contactId ?: call.number.hashCode().toLong(), size = 136.dp)
    }
}

@Composable
private fun IncomingActions(call: CallUi) {
    val t = rememberInfiniteTransition(label = "answer")
    val bounce by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce",
    )
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        RoundButton(Icons.Rounded.CallEnd, HangUpRed, "Rechazar") { CallManager.reject(call.id) }
        RoundButton(
            Icons.Rounded.Call, AnswerGreen, "Responder",
            modifier = Modifier.graphicsLayer { translationY = -bounce * 10.dp.toPx() },
        ) { CallManager.answer(call.id) }
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
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CallControls(call: CallUi, audio: CallAudioState?, onKeypad: () -> Unit) {
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val supported = audio?.supportedRouteMask ?: (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
    val hasBluetooth = supported and CallAudioState.ROUTE_BLUETOOTH != 0
    val hasWired = supported and CallAudioState.ROUTE_WIRED_HEADSET != 0
    var routeMenu by remember { mutableStateOf(false) }
    val active = call.phase == CallPhase.ACTIVE || call.phase == CallPhase.HOLDING

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ToggleControl(Icons.Rounded.MicOff, "Silenciar", checked = muted) { CallManager.setMuted(!muted) }
        ToggleControl(Icons.Rounded.Dialpad, "Teclado", checked = false, enabled = call.phase == CallPhase.ACTIVE, onClick = onKeypad)
        Box {
            if (hasBluetooth) {
                val (icon, label) = when (route) {
                    CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth to "Bluetooth"
                    CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp to "Altavoz"
                    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset to "Auriculares"
                    else -> Icons.Rounded.PhoneInTalk to "Teléfono"
                }
                ToggleControl(icon, label, checked = route != CallAudioState.ROUTE_EARPIECE) { routeMenu = true }
                DropdownMenu(expanded = routeMenu, onDismissRequest = { routeMenu = false }) {
                    val options = buildList {
                        add(Triple(CallAudioState.ROUTE_BLUETOOTH, "Bluetooth", Icons.Rounded.Bluetooth))
                        add(Triple(CallAudioState.ROUTE_SPEAKER, "Altavoz", Icons.AutoMirrored.Rounded.VolumeUp))
                        if (hasWired) add(Triple(CallAudioState.ROUTE_WIRED_HEADSET, "Auriculares", Icons.Rounded.Headset))
                        else add(Triple(CallAudioState.ROUTE_EARPIECE, "Teléfono", Icons.Rounded.PhoneInTalk))
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
                ToggleControl(Icons.AutoMirrored.Rounded.VolumeUp, "Altavoz", checked = speaker) {
                    CallManager.setRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                }
            }
        }
        val onHold = call.phase == CallPhase.HOLDING
        ToggleControl(Icons.Rounded.Pause, if (onHold) "Reanudar" else "En espera", checked = onHold, enabled = active && call.canHold) {
            CallManager.setHold(call.id, !onHold)
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
    // Forma «expresiva»: círculo cuando está apagado, cuadrado redondeado cuando está activo.
    val corner by animateDpAsState(if (checked) 22.dp else 34.dp, label = "corner")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
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
            maxLines = 1,
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
                Text("En espera", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(held.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { CallManager.setHold(held.id, false) }) {
                Icon(Icons.Rounded.SwapCalls, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cambiar")
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
            h to (label ?: "SIM ${i + 1}")
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
            Text("No hay ninguna SIM disponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
