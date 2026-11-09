@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.phone.call

import android.content.Intent
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PhonePaused
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.hostyn.phone.MainActivity
import dev.hostyn.phone.R
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.Dialpad
import dev.hostyn.phone.ui.theme.CustomColor
import dev.hostyn.phone.ui.theme.callGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** What the bottom of the screen shows. */
private enum class Controls { Account, Incoming, Ended, Keypad, InCall }

@Composable
private fun InCallScreen(call: CallUi, others: List<CallUi>, audio: CallAudioState?) {
    val held = others.firstOrNull { it.phase == CallPhase.HOLDING }.takeIf { call.phase != CallPhase.RINGING }
    // Call waiting: the call we are already on while another one rings.
    val ongoing = if (call.phase == CallPhase.RINGING) others.firstOrNull { it.phase != CallPhase.RINGING } else null
    var keypad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val motion = MaterialTheme.motionScheme
    val showKeypad = keypad && call.phase == CallPhase.ACTIVE

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (held != null) HeldBanner(held)
                if (ongoing != null) OngoingBanner(ongoing)

                Spacer(Modifier.height(if (showKeypad) 16.dp else 32.dp))
                StatusText(call)
                Spacer(Modifier.height(if (showKeypad) 8.dp else 24.dp))

                if (!showKeypad) {
                    CallerAvatar(call)
                    Spacer(Modifier.height(24.dp))
                }
                Text(
                    call.name,
                    style = if (showKeypad) type.titleLargeEmphasized else type.displaySmallEmphasized,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (showKeypad) {
                    Text(
                        dtmf.ifEmpty { " " },
                        style = type.headlineMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (call.label != null) {
                    Text(
                        call.label,
                        style = type.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                AnimatedContent(
                    targetState = when {
                        call.phase == CallPhase.SELECT_ACCOUNT -> Controls.Account
                        call.phase == CallPhase.RINGING -> Controls.Incoming
                        call.phase == CallPhase.ENDED -> Controls.Ended
                        showKeypad -> Controls.Keypad
                        else -> Controls.InCall
                    },
                    // Enter/exit within the screen: rise from the bottom edge the controls sit on.
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 4 }) togetherWith
                            fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                    },
                    contentAlignment = Alignment.BottomCenter,
                    label = "controls",
                ) { mode ->
                    when (mode) {
                        Controls.Account -> AccountPicker(call)
                        Controls.Incoming -> IncomingActions(call, ongoing)
                        Controls.Ended -> Spacer(Modifier.height(120.dp).fillMaxWidth())
                        Controls.Keypad -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Dialpad(
                                onKeyDown = { CallManager.startDtmf(call.id, it); dtmf += it },
                                onKeyUp = { CallManager.stopDtmf(call.id) },
                                keyHeight = 58.dp,
                                keyColor = colors.surfaceContainerHigh,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            TextButton(onClick = { keypad = false }, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(top = 4.dp)) {
                                Text(stringResource(R.string.hide_keypad))
                            }
                        }
                        Controls.InCall -> CallControls(call, held, audio, onKeypad = { keypad = true })
                    }
                }

                if (call.phase != CallPhase.RINGING && call.phase != CallPhase.ENDED) {
                    Spacer(Modifier.height(24.dp))
                    HangUpButton { CallManager.hangUp(call.id) }
                }
                Spacer(Modifier.height(16.dp))
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
        // Tabular figures so the timer doesn't jiggle as digits change.
        style = MaterialTheme.typography.titleMediumEmphasized.copy(fontFeatureSettings = "tnum"),
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

/**
 * A [Morph] between two Material shapes as a [Shape], at [progress] and turned by [rotation]
 * degrees around its center.
 */
private class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        path.transform(
            Matrix().apply {
                translate(size.width / 2, size.height / 2)
                rotateZ(rotation)
                scale(size.width, size.height)
                translate(-0.5f, -0.5f)
            },
        )
        return Outline.Generic(path)
    }
}

/**
 * The caller, as the screen's hero moment. While the call rings, the photo is cut into a Material
 * cookie shape that turns slowly and beats with a spring, over a burst shape turning the other
 * way (two layers moving differently, for depth). Once answered both settle into a plain circle.
 */
@Composable
private fun CallerAvatar(call: CallUi) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val ringing = call.phase == CallPhase.RINGING || call.phase == CallPhase.DIALING

    val avatarMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val backMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.SoftBurst) }

    // 0 = circle, 1 = ringing shapes. Spatial springs, so the change overshoots and settles.
    val shaped = remember { Animatable(if (ringing) 1f else 0f) }
    val beat = remember { Animatable(0f) }
    val appear = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, motion.slowSpatialSpec()) }
    LaunchedEffect(ringing) {
        if (ringing) {
            launch { shaped.animateTo(1f, motion.defaultSpatialSpec()) }
            while (true) {
                beat.animateTo(1f, motion.slowSpatialSpec())
                beat.animateTo(0f, motion.slowSpatialSpec())
                delay(250)
            }
        } else {
            launch { beat.animateTo(0f, motion.defaultSpatialSpec()) }
            shaped.animateTo(0f, motion.slowSpatialSpec())
        }
    }
    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )

    val size = 152.dp
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.4f)) {
        // Back layer: a burst in the primary container color.
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val s = 0.8f + 0.4f * shaped.value + 0.12f * beat.value
                    scaleX = s
                    scaleY = s
                    alpha = shaped.value.coerceIn(0f, 1f)
                    shape = MorphShape(backMorph, shaped.value, -rotation * 0.6f)
                    clip = true
                }
                .background(colors.primaryContainer),
        )
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val s = appear.value * (1f + 0.07f * beat.value)
                    scaleX = s
                    scaleY = s
                    shape = MorphShape(avatarMorph, shaped.value, rotation)
                    clip = true
                },
        ) {
            if (call.isConference) {
                Box(Modifier.fillMaxSize().background(colors.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Groups, null, tint = colors.onTertiaryContainer, modifier = Modifier.size(64.dp))
                }
            } else {
                // The avatar draws its own circle; the morph shape above clips it to the final shape.
                ContactAvatar(call.name, call.photoUri, call.contactId ?: call.number.hashCode().toLong(), size = size, shape = RectangleShape)
            }
        }
    }
}

@Composable
private fun IncomingActions(call: CallUi, ongoing: CallUi?) {
    val green = callGreen()
    var replying by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (call.canReplyWithText) {
            FilledTonalButton(
                onClick = { replying = true },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(bottom = 32.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Message, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.message))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            val decline = CustomColor(
                MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError,
                MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
            )
            if (ongoing == null) {
                BigCallButton(Icons.Rounded.CallEnd, decline, stringResource(R.string.decline)) { CallManager.reject(call.id) }
                BigCallButton(Icons.Rounded.Call, green, stringResource(R.string.answer)) { CallManager.answer(call.id) }
            } else {
                // Three choices: make the buttons a size smaller so they fit.
                BigCallButton(Icons.Rounded.CallEnd, decline, stringResource(R.string.decline), large = false) { CallManager.reject(call.id) }
                BigCallButton(Icons.Rounded.PhoneForwarded, green, stringResource(R.string.end_and_answer), large = false) {
                    CallManager.endAndAnswer(call.id)
                }
                if (ongoing.canHold || ongoing.phase == CallPhase.HOLDING) {
                    BigCallButton(Icons.Rounded.PhonePaused, green, stringResource(R.string.hold_and_answer), large = false) {
                        CallManager.answer(call.id)
                    }
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

/** Answer / decline: an extra large (or large) round icon button that squares up when pressed. */
@Composable
private fun BigCallButton(icon: ImageVector, color: CustomColor, label: String, large: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(if (large) 152.dp else 112.dp)) {
        FilledIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(
                shape = if (large) IconButtonDefaults.extraLargeRoundShape else IconButtonDefaults.largeRoundShape,
                pressedShape = if (large) IconButtonDefaults.extraLargePressedShape else IconButtonDefaults.largePressedShape,
            ),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color.color, contentColor = color.onColor),
            modifier = Modifier.size(if (large) IconButtonDefaults.extraLargeContainerSize() else IconButtonDefaults.largeContainerSize()),
        ) {
            Icon(icon, label, Modifier.size(if (large) IconButtonDefaults.extraLargeIconSize else IconButtonDefaults.largeIconSize))
        }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Hang up: a large, wide red icon button that morphs when pressed. */
@Composable
private fun HangUpButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(IconButtonDefaults.largeRoundShape, IconButtonDefaults.largePressedShape),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = Modifier.size(IconButtonDefaults.largeContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
    ) {
        Icon(Icons.Rounded.CallEnd, stringResource(R.string.hang_up), Modifier.size(IconButtonDefaults.largeIconSize))
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
                val count = presets.size + 1
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    presets.forEachIndexed { i, p ->
                        SegmentedListItem(onClick = { onSend(p) }, shapes = ListItemDefaults.segmentedShapes(i, count)) {
                            Text(p)
                        }
                    }
                    SegmentedListItem(
                        onClick = { custom = "" },
                        shapes = ListItemDefaults.segmentedShapes(count - 1, count),
                        leadingContent = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                    ) {
                        Text(stringResource(R.string.quick_reply_custom), color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { custom = it },
                        shape = MaterialTheme.shapes.extraLarge,
                        maxLines = 4,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { onSend(text.trim()) },
                        enabled = text.isNotBlank(),
                        shapes = IconButtonDefaults.shapes(IconButtonDefaults.mediumRoundShape, IconButtonDefaults.mediumPressedShape),
                        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                    ) {
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
        shape = MaterialTheme.shapes.large,
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

/**
 * In-call controls: two standard button groups of large tonal icon buttons. Toggles are round when
 * off and square when on; pressing one widens it and squeezes its neighbours, as M3 button groups do.
 */
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

    val muteLabel = stringResource(R.string.mute)
    val keypadLabel = stringResource(R.string.keypad)
    val addLabel = stringResource(R.string.add_call)
    val swapLabel = stringResource(R.string.swap)
    val holdLabel = stringResource(if (call.phase == CallPhase.HOLDING) R.string.resume else R.string.hold)
    val mergeLabel = stringResource(R.string.merge)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        ControlGroup {
            control(Icons.Rounded.MicOff, muteLabel, checked = muted) { CallManager.setMuted(!muted) }
            control(Icons.Rounded.Dialpad, keypadLabel, enabled = call.phase == CallPhase.ACTIVE, onClick = onKeypad)
            if (hasBluetooth) {
                val (icon, label) = when (route) {
                    CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth to bluetoothLabel
                    CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp to speakerLabel
                    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset to headsetLabel
                    else -> Icons.Rounded.PhoneInTalk to earpieceLabel
                }
                control(icon, label, checked = route != CallAudioState.ROUTE_EARPIECE, onClick = { routeMenu = true }, extra = {
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
                                trailingIcon = { if (route == r) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { routeMenu = false; CallManager.setRoute(r) },
                            )
                        }
                    }
                })
            } else {
                val speaker = route == CallAudioState.ROUTE_SPEAKER
                control(Icons.AutoMirrored.Rounded.VolumeUp, speakerLabel, checked = speaker) {
                    CallManager.setRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                }
            }
        }
        ControlGroup {
            control(Icons.Rounded.AddIcCall, addLabel, enabled = call.phase == CallPhase.ACTIVE) {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_DIAL)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            if (held != null) {
                // Two calls: swap between them.
                control(Icons.Rounded.SwapCalls, swapLabel) { CallManager.setHold(held.id, false) }
            } else {
                val onHold = call.phase == CallPhase.HOLDING
                control(Icons.Rounded.Pause, holdLabel, checked = onHold, enabled = active && call.canHold) {
                    CallManager.setHold(call.id, !onHold)
                }
            }
            if (held != null && call.canMerge) {
                control(Icons.Rounded.CallMerge, mergeLabel) { CallManager.merge(call.id) }
            } else {
                // Keeps the second row on the same three-column grid as the first.
                customItem(buttonGroupContent = { Spacer(Modifier.weight(1f)) }, menuContent = {})
            }
        }
    }
}

@Composable
private fun ControlGroup(content: ButtonGroupScope.() -> Unit) {
    ButtonGroup(
        overflowIndicator = {},
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

/**
 * One in-call control in a [ButtonGroup]: a large tonal icon button with its label underneath.
 * With [checked] it is a toggle (round when off, square when on); otherwise a plain action.
 */
private fun ButtonGroupScope.control(
    icon: ImageVector,
    label: String,
    checked: Boolean? = null,
    enabled: Boolean = true,
    extra: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val source = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).animateWidth(source),
            ) {
                val size = Modifier.fillMaxWidth().height(IconButtonDefaults.largeContainerSize().height)
                Box {
                    if (checked != null) {
                        FilledTonalIconToggleButton(
                            checked = checked,
                            onCheckedChange = { onClick() },
                            shapes = IconButtonDefaults.toggleableShapes(
                                shape = IconButtonDefaults.largeRoundShape,
                                pressedShape = IconButtonDefaults.largePressedShape,
                                checkedShape = IconButtonDefaults.largeSelectedRoundShape,
                            ),
                            enabled = enabled,
                            interactionSource = source,
                            modifier = size,
                        ) { Icon(icon, label, Modifier.size(IconButtonDefaults.largeIconSize)) }
                    } else {
                        FilledTonalIconButton(
                            onClick = onClick,
                            shapes = IconButtonDefaults.shapes(IconButtonDefaults.largeRoundShape, IconButtonDefaults.largePressedShape),
                            enabled = enabled,
                            interactionSource = source,
                            modifier = size,
                        ) { Icon(icon, label, Modifier.size(IconButtonDefaults.largeIconSize)) }
                    }
                    extra()
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        menuContent = {},
    )
}

@Composable
private fun HeldBanner(held: CallUi) {
    Surface(
        shape = MaterialTheme.shapes.large,
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
            TextButton(onClick = { CallManager.setHold(held.id, false) }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.SwapCalls, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        accounts.forEachIndexed { i, (handle, label) ->
            SegmentedListItem(
                onClick = { CallManager.selectAccount(call.id, handle) },
                shapes = ListItemDefaults.segmentedShapes(i, accounts.size),
                leadingContent = { Icon(Icons.Rounded.SimCard, null, tint = MaterialTheme.colorScheme.primary) },
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (accounts.isEmpty()) {
            Text(stringResource(R.string.no_sim), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
