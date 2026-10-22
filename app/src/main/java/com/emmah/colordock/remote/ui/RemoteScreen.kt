package com.emmah.colordock.remote.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.*

@Composable
internal fun DockIcon(@DrawableRes icon: Int, label: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), label, modifier.size(24.dp), tint)
}

@Composable
fun RemoteScreen(
    state: TvState,
    channels: List<TvChannel>,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    onOpenDock: () -> Unit,
    onTune: (String) -> Unit,
    onConnection: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
) {
    var powerConfirmation by rememberSaveable { mutableStateOf(false) }
    var numbers by rememberSaveable { mutableStateOf(false) }
    val enabled = state.connection == TvConnectionState.Ready
    DisposableEffect(Unit) { onDispose(onRelease) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start=16.dp,end=16.dp,top=8.dp,bottom=24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onConnection, Modifier.weight(1f), shape=RoundedCornerShape(0.dp), contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Canvas(Modifier.size(10.dp)) { drawCircle(if (enabled) Color(0xFF50DB8A) else Color(0xFFFFB4AB)) }
                        Text(if (enabled) "Connected" else "TV unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SettingsButton(onSettings)
            OutlinedIconButton(onClick = { powerConfirmation = true }, enabled = enabled, modifier = Modifier.size(48.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                DockIcon(R.drawable.ic_power_settings_new, "Turn off TV", Modifier.size(30.dp), MaterialTheme.colorScheme.primary)
            }
        }
        AdaptivePair(
            first = { width -> RemoteAction("LG Home", R.drawable.ic_home, enabled, { onCommand(TvCommand.Home) }, width, "Go to LG Home") },
            second = { width -> RemoteAction("Color Dock", R.drawable.ic_grid_view, enabled, onOpenDock, width) },
        )
        TextButton(onClick = { onCommand(TvCommand.LiveTv) }, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
            DockIcon(R.drawable.ic_tv); Spacer(Modifier.width(10.dp)); Text("Live TV")
        }
        state.channel?.let { Text("${it.number}: ${it.name}", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodyMedium) }
        DirectionPad(enabled, onCommand, onPress, onRelease, Modifier.align(Alignment.CenterHorizontally))
        AdaptivePair(
            first = { width -> RemoteAction("Back", R.drawable.ic_undo, enabled, { onCommand(TvCommand.Back) }, width, "Back on TV") },
            second = { width -> RemoteAction("TV Guide", R.drawable.ic_format_list_bulleted, enabled && state.guideAvailable, { onCommand(TvCommand.Guide) }, width) },
        )
        if (!state.guideAvailable) Text("TV Guide is not available on this TV.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Rocker("Volume", R.drawable.ic_volume_up, TvCommand.VolumeUp, TvCommand.VolumeDown, enabled, onCommand, onPress, onRelease, Modifier.weight(1f))
            Rocker("Channel", R.drawable.ic_tv, TvCommand.ChannelUp, TvCommand.ChannelDown, enabled, onCommand, onPress, onRelease, Modifier.weight(1f))
        }
        AdaptivePair(
            first = { width -> RemoteAction(when (state.muted) { true -> "Unmute"; false -> "Mute"; null -> "Mute sound" }, R.drawable.ic_volume_off, enabled, { onCommand(if (state.muted == true) TvCommand.Unmute else TvCommand.Mute) }, width) },
            second = { width -> OutlinedButton(onClick = { numbers = true }, enabled = enabled, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = remoteColors(), modifier = width.heightIn(min = 56.dp)) {
                Text("123", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.width(8.dp)); Text("Numbers")
            } },
        )
    }
    if (powerConfirmation) AlertDialog(
        onDismissRequest = { powerConfirmation = false }, title = { Text("Turn off the TV?") },
        confirmButton = { TextButton(onClick = { powerConfirmation = false; onCommand(TvCommand.PowerOff) }) { Text("Turn off") } },
        dismissButton = { TextButton(onClick = { powerConfirmation = false }) { Text("Cancel") } },
    )
    if (numbers) NumberPadSheet(channels, onDismiss = { numbers = false }, onTune = { id -> numbers = false; onTune(id) })
}

@Composable
internal fun AdaptivePair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.4f
    BoxWithConstraints {
        if (maxWidth < 300.dp || largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f)); second(Modifier.weight(1f))
        }
    }
}

@Composable
private fun remoteColors() = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)

@Composable
private fun RemoteAction(text: String, @DrawableRes icon: Int, enabled: Boolean, action: () -> Unit, modifier: Modifier, description: String? = null) {
    OutlinedButton(action, modifier.heightIn(min = 56.dp).then(if (description == null) Modifier else Modifier.semantics { contentDescription = description }), enabled,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = remoteColors(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        DockIcon(icon); Spacer(Modifier.width(10.dp)); Text(text)
    }
}

@Composable
private fun DirectionPad(enabled: Boolean, onCommand: (TvCommand) -> Unit, onPress: (TvCommand) -> Unit, onRelease: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.size(220.dp).testTag("direction-pad"), CircleShape, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant), shadowElevation = 2.dp) {
        Box {
            Canvas(Modifier.fillMaxSize()) {
                val inset = size.width * .147f
                drawLine(colors.outlineVariant, Offset(inset,inset), Offset(size.width-inset,size.height-inset), 1f)
                drawLine(colors.outlineVariant, Offset(size.width-inset,inset), Offset(inset,size.height-inset), 1f)
            }
            RemoteKey("Up", R.drawable.ic_keyboard_arrow_up, TvCommand.Up, enabled, onCommand, onPress, onRelease, Modifier.align(Alignment.TopCenter).size(72.dp, 64.dp))
            RemoteKey("Left", R.drawable.ic_keyboard_arrow_left, TvCommand.Left, enabled, onCommand, onPress, onRelease, Modifier.align(Alignment.CenterStart).size(64.dp, 72.dp))
            RemoteKey("Right", R.drawable.ic_keyboard_arrow_right, TvCommand.Right, enabled, onCommand, onPress, onRelease, Modifier.align(Alignment.CenterEnd).size(64.dp, 72.dp))
            RemoteKey("Down", R.drawable.ic_keyboard_arrow_down, TvCommand.Down, enabled, onCommand, onPress, onRelease, Modifier.align(Alignment.BottomCenter).size(72.dp, 64.dp))
            Surface(Modifier.align(Alignment.Center).size(88.dp), CircleShape, colors.primaryContainer, shadowElevation = 2.dp) {
                TextButton({ onCommand(TvCommand.Ok) }, Modifier.fillMaxSize().semantics { contentDescription = "OK" }, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer)) { Text("OK", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

@Composable
private fun Rocker(title: String, @DrawableRes icon: Int, up: TvCommand, down: TvCommand, enabled: Boolean, onCommand: (TvCommand) -> Unit, onPress: (TvCommand) -> Unit, onRelease: () -> Unit, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(24.dp), MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteKey("$title up", R.drawable.ic_add, up, enabled, onCommand, onPress, onRelease, Modifier.fillMaxWidth().height(48.dp))
            DockIcon(icon)
            RemoteKey("$title down", R.drawable.ic_remove, down, enabled, onCommand, onPress, onRelease, Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

@Composable
private fun RemoteKey(label: String, @DrawableRes icon: Int, command: TvCommand, enabled: Boolean, onCommand: (TvCommand) -> Unit, onPress: (TvCommand) -> Unit, onRelease: () -> Unit, modifier: Modifier) {
    val press = if (command.repeatable) Modifier.pointerInput(command, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (enabled) {
                down.consume(); onPress(command)
                try { waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume() }
                finally { onRelease() }
            }
        }
    } else Modifier
    TextButton(onClick = { onCommand(command) }, enabled = enabled,
        modifier = modifier.then(press).onFocusChanged { if (!it.isFocused) onRelease() }.semantics { contentDescription = label },
        shape = CircleShape, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), contentPadding = PaddingValues(4.dp)) {
        DockIcon(icon, modifier = Modifier.size(34.dp))
    }
}
