package dev.hostyn.contactos.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Keys = listOf(
    '1' to "", '2' to "ABC", '3' to "DEF",
    '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
    '*' to "", '0' to "+", '#' to "",
)

@Composable
fun Dialpad(
    onKeyDown: (Char) -> Unit,
    modifier: Modifier = Modifier,
    onKeyUp: (Char) -> Unit = {},
    onLongPress: ((Char) -> Unit)? = null,
    keyHeight: Dp = 64.dp,
    keyColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (digit, letters) ->
                    DialKey(
                        digit = digit,
                        letters = letters,
                        color = keyColor,
                        onDown = onKeyDown,
                        onUp = onKeyUp,
                        onLongPress = onLongPress,
                        modifier = Modifier.weight(1f).height(keyHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialKey(
    digit: Char,
    letters: String,
    color: Color,
    onDown: (Char) -> Unit,
    onUp: (Char) -> Unit,
    onLongPress: ((Char) -> Unit)?,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val long by rememberUpdatedState(onLongPress)

    Surface(
        shape = CircleShape,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .indication(interaction, ripple())
            .pointerInput(digit) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interaction.emit(press)
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        down(digit)
                        val released = tryAwaitRelease()
                        up(digit)
                        interaction.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    },
                    onLongPress = { long?.invoke(digit) },
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    digit.toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 30.sp,
                )
                if (letters.isNotEmpty()) {
                    Text(
                        letters,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
