package com.emmah.colordock.remote.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.TvChannel
import com.emmah.colordock.remote.tv.TvCommand
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import java.io.File

@Composable
internal fun AdaptivePair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    val largeText = LocalDensity.current.fontScale > LARGE_TEXT_FONT_SCALE
    val currentFirst by rememberUpdatedState(first)
    val currentSecond by rememberUpdatedState(second)
    val firstContent = remember { movableContentOf<Modifier> { currentFirst(it) } }
    val secondContent = remember { movableContentOf<Modifier> { currentSecond(it) } }
    BoxWithConstraints {
        if (maxWidth < 300.dp || largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                firstContent(Modifier.fillMaxWidth())
                secondContent(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                firstContent(Modifier.weight(1f))
                secondContent(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun remoteColors() =
    ButtonDefaults.outlinedButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun RemoteAction(
    text: String,
    @DrawableRes icon: Int?,
    enabled: Boolean,
    action: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    vertical: Boolean = false,
) {
    OutlinedButton(
        action,
        modifier.heightIn(min = 56.dp).then(
            if (description ==
                null
            ) {
                Modifier
            } else {
                Modifier.semantics {
                    contentDescription = description
                }
            },
        ),
        enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = remoteColors(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (vertical) 6.dp else 10.dp),
    ) {
        val artwork: @Composable () -> Unit = {
            if (icon !=
                null
            ) {
                DockIcon(icon)
            } else {
                Text("123", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (vertical) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                artwork()
                Text(text, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            artwork()
            Spacer(Modifier.width(10.dp))
            Text(text)
        }
    }
}

@Composable
internal fun DirectionPad(
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.size(220.dp).testTag("direction-pad"),
        CircleShape,
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 0.dp,
    ) {
        Box {
            DirectionKeys(enabled, onCommand, onPress, onRelease)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(102.dp)
                    .border(1.dp, colors.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    Modifier.size(88.dp),
                    CircleShape,
                    colors.primaryContainer,
                    shadowElevation = 0.dp,
                ) {
                    TextButton(
                        {
                            onCommand(TvCommand.Ok)
                        },
                        Modifier.fillMaxSize().semantics {
                            contentDescription = "OK"
                        },
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                    ) {
                        Text("OK", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun Rocker(
    title: String,
    @DrawableRes icon: Int,
    up: TvCommand,
    down: TvCommand,
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
) {
    Surface(
        modifier,
        RoundedCornerShape(24.dp),
        MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteKey(
                "$title up",
                R.drawable.ic_add,
                up,
                enabled,
                onCommand,
                onPress,
                onRelease,
                Modifier.fillMaxWidth().height(48.dp),
            )
            if (stretch) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DockIcon(icon)
                }
            } else {
                DockIcon(icon)
            }
            RemoteKey(
                "$title down",
                R.drawable.ic_remove,
                down,
                enabled,
                onCommand,
                onPress,
                onRelease,
                Modifier.fillMaxWidth().height(48.dp),
            )
        }
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun RemoteKey(
    label: String,
    @DrawableRes icon: Int,
    command: TvCommand,
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 34.dp,
    contentColor: Color = Color.Unspecified,
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val press =
        if (command.repeatable) {
            Modifier.pointerInput(command, enabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (enabled) {
                        down.consume()
                        currentOnPress(command)
                        try {
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume()
                        } finally {
                            currentOnRelease()
                        }
                    }
                }
            }
        } else {
            Modifier
        }
    TextButton(
        onClick = {
            onCommand(command)
        },
        enabled = enabled,
        modifier =
            modifier
                .then(press)
                .onFocusChanged {
                    if (!it.isFocused) currentOnRelease()
                }.semantics {
                    contentDescription = label
                },
        shape = CircleShape,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor =
                    if (contentColor ==
                        Color.Unspecified
                    ) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        contentColor
                    },
            ),
        contentPadding = PaddingValues(4.dp),
    ) {
        DockIcon(icon, modifier = Modifier.size(iconSize))
    }
}

private const val LARGE_TEXT_FONT_SCALE = 1.4f

@Composable
private fun BoxScope.DirectionKeys(
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
) {
    RemoteKey(
        "Up",
        R.drawable.ic_keyboard_arrow_up,
        TvCommand.Up,
        enabled,
        onCommand,
        onPress,
        onRelease,
        Modifier.align(Alignment.TopCenter).size(72.dp, 64.dp),
        iconSize = 28.dp,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RemoteKey(
        "Left",
        R.drawable.ic_keyboard_arrow_left,
        TvCommand.Left,
        enabled,
        onCommand,
        onPress,
        onRelease,
        Modifier.align(AbsoluteAlignment.CenterLeft).size(64.dp, 72.dp),
        iconSize = 28.dp,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RemoteKey(
        "Right",
        R.drawable.ic_keyboard_arrow_right,
        TvCommand.Right,
        enabled,
        onCommand,
        onPress,
        onRelease,
        Modifier.align(AbsoluteAlignment.CenterRight).size(64.dp, 72.dp),
        iconSize = 28.dp,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RemoteKey(
        "Down",
        R.drawable.ic_keyboard_arrow_down,
        TvCommand.Down,
        enabled,
        onCommand,
        onPress,
        onRelease,
        Modifier.align(Alignment.BottomCenter).size(72.dp, 64.dp),
        iconSize = 28.dp,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
