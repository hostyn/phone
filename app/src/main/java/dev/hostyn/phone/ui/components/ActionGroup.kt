@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.phone.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** M3 icon button sizes used for labeled actions. */
enum class ActionSize { Medium, Large }

/**
 * A standard M3 button group of labeled icon buttons that share the width. Pressing one widens it
 * and squeezes its neighbours.
 */
@Composable
fun ActionGroup(modifier: Modifier = Modifier, content: ButtonGroupScope.() -> Unit) {
    ButtonGroup(overflowIndicator = {}, modifier = modifier.fillMaxWidth(), content = content)
}

/** An empty slot, to keep a row on the same grid as the one above it. */
fun ButtonGroupScope.emptyAction() {
    customItem(buttonGroupContent = { Spacer(Modifier.weight(1f)) }, menuContent = {})
}

fun ButtonGroupScope.action(
    icon: ImageVector,
    label: String,
    size: ActionSize,
    checked: Boolean? = null,
    enabled: Boolean = true,
    extra: @Composable () -> Unit = {},
    onClick: () -> Unit,
) = action({ Icon(icon, label, Modifier.size(size.iconSize)) }, label, size, checked, enabled, extra, onClick)

/**
 * One labeled action in an [ActionGroup]: a tonal icon button with its label underneath. With
 * [checked] it is a toggle, round when off and square when on; otherwise a plain action. Both
 * square up a little while pressed.
 */
fun ButtonGroupScope.action(
    icon: @Composable () -> Unit,
    label: String,
    size: ActionSize,
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
                val buttonModifier = Modifier.fillMaxWidth().height(size.height)
                Box {
                    if (checked != null) {
                        FilledTonalIconToggleButton(
                            checked = checked,
                            onCheckedChange = { onClick() },
                            shapes = IconButtonDefaults.toggleableShapes(
                                shape = size.roundShape,
                                pressedShape = size.pressedShape,
                                checkedShape = size.checkedShape,
                            ),
                            enabled = enabled,
                            interactionSource = source,
                            modifier = buttonModifier,
                        ) { icon() }
                    } else {
                        FilledTonalIconButton(
                            onClick = onClick,
                            shapes = IconButtonDefaults.shapes(size.roundShape, size.pressedShape),
                            enabled = enabled,
                            interactionSource = source,
                            modifier = buttonModifier,
                        ) { icon() }
                    }
                    extra()
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = if (size == ActionSize.Large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
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

private val ActionSize.height: Dp
    get() = when (this) {
        ActionSize.Medium -> IconButtonDefaults.mediumContainerSize().height
        ActionSize.Large -> IconButtonDefaults.largeContainerSize().height
    }

val ActionSize.iconSize: Dp
    get() = when (this) {
        ActionSize.Medium -> IconButtonDefaults.mediumIconSize
        ActionSize.Large -> IconButtonDefaults.largeIconSize
    }

private val ActionSize.roundShape
    @Composable get() = if (this == ActionSize.Medium) IconButtonDefaults.mediumRoundShape else IconButtonDefaults.largeRoundShape
private val ActionSize.pressedShape
    @Composable get() = if (this == ActionSize.Medium) IconButtonDefaults.mediumPressedShape else IconButtonDefaults.largePressedShape
private val ActionSize.checkedShape
    @Composable get() = if (this == ActionSize.Medium) IconButtonDefaults.mediumSelectedRoundShape else IconButtonDefaults.largeSelectedRoundShape
