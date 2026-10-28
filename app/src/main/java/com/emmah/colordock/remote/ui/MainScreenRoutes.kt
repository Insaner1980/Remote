package com.emmah.colordock.remote.ui

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.window.SplashScreenView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.tv.DiscoveredTv
import com.emmah.colordock.remote.tv.TvChannel
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvErrorCode
import com.emmah.colordock.remote.tv.TvState
import com.emmah.colordock.remote.tv.TvTarget
import com.emmah.colordock.remote.ui.AppTheme
import com.emmah.colordock.remote.ui.ConnectionScreen
import com.emmah.colordock.remote.ui.DockScreen
import com.emmah.colordock.remote.ui.DockViewModel
import com.emmah.colordock.remote.ui.RemoteAppShell
import com.emmah.colordock.remote.ui.RemoteScreen
import com.emmah.colordock.remote.ui.RemoteTab
import com.emmah.colordock.remote.ui.RemoteTheme
import com.emmah.colordock.remote.ui.RemoteViewModel
import com.emmah.colordock.remote.ui.ThemeSettings
import com.emmah.colordock.remote.ui.ThemeViewModel
import com.emmah.colordock.remote.ui.WallpaperScreen
import com.emmah.colordock.remote.ui.WallpaperViewModel
import com.emmah.colordock.remote.ui.asImageFile
import java.io.File

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun WallpaperRoute(
    dockState: DockState,
    wallpaperUi: WallpaperUiState,
    thumbnails: Map<String, File>,
    modelProvider: () -> RemoteViewModel,
    wallpapersProvider: () -> WallpaperViewModel,
    onAdd: () -> Unit,
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = modelProvider()
    val wallpapers = wallpapersProvider()
    WallpaperScreen(
        dockState,
        wallpaperUi,
        thumbnails,
        model::launchDock,
        wallpapers::join,
        wallpapers::source,
        {
            wallpapers.load(true)
        },
        wallpapers::thumbnail,
        wallpapers::detail,
        onAdd,
        wallpapers::patch,
        wallpapers::imageAction,
        wallpapers::next,
        wallpapers::preparedFit,
        wallpapers::send,
        wallpapers::cancelUpload,
        wallpapers::abandon,
        modifier,
        onSettings = openSettings,
    )
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun DockRoute(
    dockState: DockState,
    dockUi: DockUiState,
    thumbnails: Map<String, File>,
    modelProvider: () -> RemoteViewModel,
    dockProvider: () -> DockViewModel,
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = modelProvider()
    val dock = dockProvider()
    DockScreen(
        dockState,
        dockUi,
        thumbnails[dockState.snapshot?.current?.id].asImageFile(),
        model::launchDock,
        dock::join,
        dock::clock,
        dock::patch,
        dock::shelf,
        dock::query,
        dock::search,
        dock::location,
        modifier,
        onSettings = openSettings,
    )
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun RemoteRoute(
    state: TvState,
    channels: List<TvChannel>,
    modelProvider: () -> RemoteViewModel,
    tvApps: TvAppsUiState,
    appIcons: Map<String, File>,
    onConnection: () -> Unit,
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = modelProvider()
    RemoteScreen(
        state,
        channels,
        model::send,
        model::press,
        model::release,
        model::launchDock,
        model::tune,
        onConnection = onConnection,
        modifier = modifier,
        onSettings = openSettings,
        apps = tvApps,
        appIcons = appIcons,
        onRefreshApps = model::refreshApps,
        onOpenApp = model::openApp,
        onLoadAppIcon = model::loadAppIcon,
        onAppLaunchHandle = model::acknowledgeAppLaunch,
        onClearAppLaunchError = model::clearAppLaunchError,
    )
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
internal fun ConnectionRoute(
    state: TvState,
    displayedState: TvState,
    needsPermissionSettings: Boolean,
    savedAddress: String?,
    searching: Boolean,
    discovered: List<DiscoveredTv>,
    forgetTv: () -> Unit,
    onConnect: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onFind: () -> Unit,
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectionScreen(
        state = displayedState,
        needsSettings = needsPermissionSettings,
        onConnect = onConnect,
        onCancel = onCancel,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        onAppSettings = openSettings,
        onForget =
            if (savedAddress != null ||
                state.error in
                setOf(
                    TvErrorCode.PairingRequired,
                    TvErrorCode.IdentityChanged,
                    TvErrorCode.StorageUnavailable,
                )
            ) {
                forgetTv
            } else {
                null
            },
        initialAddress = savedAddress,
        searching = searching,
        discovered = discovered,
        onFind = onFind,
    )
}

@Composable
internal fun TvConnectionDialog(
    state: TvState,
    onDismiss: () -> Unit,
    onReconnect: () -> Unit,
    forgetTv: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(state.name)
        },
        text = {
            Column {
                Text(if (state.connection == TvConnectionState.Ready) "Connected" else "TV unavailable")
                TextButton(onClick = {
                    onDismiss()
                    onReconnect()
                }) {
                    Text("Reconnect")
                }
                TextButton(onClick = forgetTv) {
                    Text("Forget TV")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text("Close")
            }
        },
    )
}

@Composable
internal fun ConnectionErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Connection")
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
