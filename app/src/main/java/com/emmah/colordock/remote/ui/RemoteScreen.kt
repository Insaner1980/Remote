package com.emmah.colordock.remote.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
internal fun DockIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(icon), label, modifier.size(24.dp), tint)
}

// Keep remembered overlay state and effect lifetimes at this screen boundary; rendering is delegated below.
// Explicit callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList", "LongMethod")
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
    apps: TvAppsUiState = TvAppsUiState(),
    appIcons: Map<String, File> = emptyMap(),
    onRefreshApps: () -> Unit = {},
    onOpenApp: (String) -> Unit = {},
    onLoadAppIcon: (String) -> Unit = {},
    onAppLaunchHandle: () -> Unit = {},
    onClearAppLaunchError: () -> Unit = {},
    targetSelector: (@Composable () -> Unit)? = null,
) {
    var powerConfirmation by rememberSaveable { mutableStateOf(false) }
    var numbers by rememberSaveable { mutableStateOf(false) }
    var appPicker by remember(apps.sessionGeneration) { mutableStateOf(false) }
    var pickerLaunch by remember(apps.sessionGeneration) { mutableStateOf<String?>(null) }
    val appsFocus =
        remember { FocusRequester() }
    var restoreAppsFocus by remember { mutableStateOf(false) }
    val closeApps = {
        appPicker = false
        pickerLaunch = null
        restoreAppsFocus = true
    }
    val openApps = {
        onRelease()
        if (apps.launchCompletedId != null) onAppLaunchHandle()
        onClearAppLaunchError()
        pickerLaunch = null
        appPicker = true
        onRefreshApps()
    }
    AppPickerEffects(
        apps,
        appPicker,
        pickerLaunch,
        restoreAppsFocus,
        appsFocus,
        { restoreAppsFocus = false },
        closeApps,
        onAppLaunchHandle,
    )
    val enabled = state.connection == TvConnectionState.Ready
    val currentOnRelease by rememberUpdatedState(onRelease)
    DisposableEffect(Unit) {
        onDispose { currentOnRelease() }
    }
    RemoteControls(
        state,
        enabled,
        onCommand,
        onPress,
        onRelease,
        onOpenDock,
        onConnection,
        onSettings,
        apps,
        appIcons,
        onLoadAppIcon,
        onOpenApp,
        onRefreshApps,
        appPicker,
        openApps,
        appsFocus,
        { powerConfirmation = true },
        { numbers = true },
        modifier,
        targetSelector,
    )
    if (powerConfirmation) {
        RemotePowerDialog({ powerConfirmation = false }, onCommand)
    }
    RemoteSheets(
        numbers,
        appPicker,
        channels,
        apps,
        appIcons,
        onLoadAppIcon,
        onRefreshApps,
        onRelease,
        onTune,
        onOpenApp,
        { numbers = false },
        { pickerLaunch = it },
        closeApps,
    )
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun RemoteControls(
    state: TvState,
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    onOpenDock: () -> Unit,
    onConnection: () -> Unit,
    onSettings: () -> Unit,
    apps: TvAppsUiState,
    appIcons: Map<String, File>,
    onLoadAppIcon: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onRefreshApps: () -> Unit,
    appPicker: Boolean,
    openApps: () -> Unit,
    appsFocus: FocusRequester,
    onPowerConfirmation: () -> Unit,
    onNumbers: () -> Unit,
    modifier: Modifier = Modifier,
    targetSelector: (@Composable () -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val scrollable by remember { derivedStateOf { scroll.maxValue in 1 until Int.MAX_VALUE } }
    val bottomPadding = if (scrollable) 32.dp else RemoteDimens.ScreenGutter
    val background = MaterialTheme.colorScheme.background
    Column(
        modifier
            .fillMaxSize()
            .remoteScrollFade(scroll, background, bottomPadding)
            .verticalScroll(scroll)
            .padding(
                start = RemoteDimens.ScreenGutter,
                end = RemoteDimens.ScreenGutter,
                top = RemoteDimens.ItemGap,
                bottom = bottomPadding,
            ),
    ) {
        RemoteControlsHeader(state, onConnection, onSettings, onPowerConfirmation, targetSelector)
        Spacer(Modifier.height(RemoteDimens.BlockGap))
        AdaptivePair(
            first = { width ->
                RemoteAction("LG Home", R.drawable.ic_home, enabled, {
                    onCommand(TvCommand.Home)
                }, width, "Go to LG Home")
            },
            second = { width ->
                RemoteAction("Color Dock", R.drawable.ic_open_in_new, enabled, onOpenDock, width)
            },
        )
        Spacer(Modifier.height(RemoteDimens.BlockGap / 2))
        state.channel?.let {
            Text(
                "${it.number}: ${it.name}",
                Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(RemoteDimens.BlockGap / 2))
        }
        DirectionPad(enabled, onCommand, onPress, onRelease, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(RemoteDimens.BlockGap))
        RemoteGuideActions(state, enabled, onCommand)
        Spacer(Modifier.height(RemoteDimens.BlockGap / 2))
        RemoteTransport(state, enabled, onCommand, onPress, onRelease, onNumbers, openApps, appsFocus)
        Spacer(Modifier.height(RemoteDimens.BlockGap / 2))
        TvAppsStrip(apps, appIcons, onLoadAppIcon, {
            onRelease()
            onOpenApp(it)
        }, onRefreshApps, showStatus = !appPicker)
    }
}

private fun Modifier.remoteScrollFade(
    scroll: ScrollState,
    background: Color,
    bottomPadding: Dp,
): Modifier =
    drawWithContent {
        drawContent()
        if (scroll.maxValue - scroll.value > bottomPadding.roundToPx()) {
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, background),
                    startY = size.height - 24.dp.toPx(),
                    endY = size.height,
                ),
            )
        }
    }

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun RemoteTransport(
    state: TvState,
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    onNumbers: () -> Unit,
    openApps: () -> Unit,
    appsFocus: FocusRequester,
) {
    val muteLabel =
        when (state.muted) {
            true -> "Unmute TV"
            false -> "Mute TV"
            null -> "Mute TV sound"
        }
    val mute = {
        onCommand(if (state.muted == true) TvCommand.Unmute else TvCommand.Mute)
    }
    val openNumbers = {
        onRelease()
        onNumbers()
    }
    val largeText = LocalDensity.current.fontScale >= LARGE_TEXT_FONT_SCALE
    BoxWithConstraints {
        if (maxWidth >= 328.dp &&
            !largeText
        ) {
            CompactTransport(
                enabled,
                onCommand,
                onPress,
                onRelease,
            ) {
                TransportShortcuts(enabled, onCommand, muteLabel, mute, openNumbers, openApps, appsFocus)
            }
        } else {
            ExpandedTransport(
                enabled,
                onCommand,
                onPress,
                onRelease,
                muteLabel,
                mute,
                openNumbers,
                openApps,
                appsFocus,
            )
        }
    }
}

@Composable
private fun CompactTransport(
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    shortcuts: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("compact-controls"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Rocker(
            "TV volume",
            "VOL",
            R.drawable.ic_volume_up,
            TvCommand.VolumeUp,
            TvCommand.VolumeDown,
            enabled,
            onCommand,
            onPress,
            onRelease,
            Modifier.width(56.dp).fillMaxHeight(),
            stretch = true,
        )
        shortcuts()
        Rocker(
            "Channel",
            "CH",
            R.drawable.ic_tv,
            TvCommand.ChannelUp,
            TvCommand.ChannelDown,
            enabled,
            onCommand,
            onPress,
            onRelease,
            Modifier.width(56.dp).fillMaxHeight(),
            stretch = true,
        )
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun ExpandedTransport(
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    onPress: (TvCommand) -> Unit,
    onRelease: () -> Unit,
    muteLabel: String,
    mute: () -> Unit,
    openNumbers: () -> Unit,
    openApps: () -> Unit,
    appsFocus: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Rocker(
                "TV volume",
                "VOL",
                R.drawable.ic_volume_up,
                TvCommand.VolumeUp,
                TvCommand.VolumeDown,
                enabled,
                onCommand,
                onPress,
                onRelease,
                Modifier.weight(1f),
            )
            Rocker(
                "Channel",
                "CH",
                R.drawable.ic_tv,
                TvCommand.ChannelUp,
                TvCommand.ChannelDown,
                enabled,
                onCommand,
                onPress,
                onRelease,
                Modifier.weight(1f),
            )
        }
        AdaptivePair(
            first = {
                RemoteAction(muteLabel, R.drawable.ic_volume_off, enabled, mute, it)
            },
            second = {
                RemoteAction("Numbers", null, enabled, openNumbers, it)
            },
        )
        AdaptivePair(
            first = {
                RemoteAction("Live TV", R.drawable.ic_tv, enabled, {
                    onCommand(TvCommand.LiveTv)
                }, it)
            },
            second = {
                RemoteAction(
                    stringResource(R.string.all_tv_apps),
                    R.drawable.ic_grid_view,
                    enabled,
                    openApps,
                    it.focusRequester(appsFocus).focusProperties {
                        canFocus =
                            enabled
                    },
                )
            },
        )
    }
}

@Composable
private fun RemotePowerDialog(
    onDismiss: () -> Unit,
    onCommand: (TvCommand) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text("Turn off the TV?")
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onCommand(TvCommand.PowerOff)
                },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                DialogActionLabel("Turn off", R.drawable.ic_power_settings_new, destructive = true)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text("Cancel")
            }
        },
    )
}

private const val LARGE_TEXT_FONT_SCALE = 1.4f

@Composable
private fun RemoteGuideActions(
    state: TvState,
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
) {
    AdaptivePair(
        first = { width ->
            RemoteAction("Back", R.drawable.ic_undo, enabled, {
                onCommand(TvCommand.Back)
            }, width, "Back on TV")
        },
        second = { width ->
            RemoteAction(
                "TV Guide",
                R.drawable.ic_format_list_bulleted,
                enabled && state.guideAvailable,
                {
                    onCommand(TvCommand.Guide)
                },
                width,
            )
        },
    )
    if (!state.guideAvailable) {
        Text(
            "TV Guide is not available on this TV.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun RowScope.TransportShortcuts(
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    muteLabel: String,
    mute: () -> Unit,
    openNumbers: () -> Unit,
    openApps: () -> Unit,
    appsFocus: FocusRequester,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteAction(
                muteLabel,
                R.drawable.ic_volume_off,
                enabled,
                mute,
                Modifier.weight(1f),
                vertical = true,
            )
            RemoteAction("Numbers", null, enabled, openNumbers, Modifier.weight(1f), vertical = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteAction(
                "Live TV",
                R.drawable.ic_tv,
                enabled,
                {
                    onCommand(TvCommand.LiveTv)
                },
                Modifier.weight(1f),
                vertical = true,
            )
            RemoteAction(
                stringResource(R.string.all_tv_apps),
                R.drawable.ic_grid_view,
                enabled,
                openApps,
                Modifier.weight(1f).focusRequester(appsFocus).focusProperties {
                    canFocus =
                        enabled
                },
                vertical = true,
            )
        }
    }
}
