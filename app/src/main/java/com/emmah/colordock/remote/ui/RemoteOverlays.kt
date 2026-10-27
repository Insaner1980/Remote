package com.emmah.colordock.remote.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.TvChannel
import com.emmah.colordock.remote.tv.TvCommand
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import java.io.File

// Explicit callbacks keep overlay state owned by RemoteScreen.
@Suppress("LongParameterList")
@Composable
internal fun AppPickerEffects(
    apps: TvAppsUiState,
    appPicker: Boolean,
    pickerLaunch: String?,
    restoreAppsFocus: Boolean,
    appsFocus: FocusRequester,
    onFocusRestore: () -> Unit,
    closeApps: () -> Unit,
    onAppLaunchHandle: () -> Unit,
) {
    val currentOnFocusRestore by rememberUpdatedState(onFocusRestore)
    val currentCloseApps by rememberUpdatedState(closeApps)
    val currentOnAppLaunchHandle by rememberUpdatedState(onAppLaunchHandle)
    LaunchedEffect(appPicker, restoreAppsFocus) {
        if (!appPicker && restoreAppsFocus) {
            withFrameNanos {}
            appsFocus.requestFocus()
            currentOnFocusRestore()
        }
    }
    LaunchedEffect(apps.launchCompletedId, apps.sessionGeneration) {
        apps.launchCompletedId?.let { id ->
            if (appPicker && pickerLaunch == id) currentCloseApps()
            currentOnAppLaunchHandle()
        }
    }
}

// Explicit callbacks keep overlay state owned by RemoteScreen.
@Suppress("LongParameterList")
@Composable
internal fun RemoteSheets(
    numbers: Boolean,
    appPicker: Boolean,
    channels: List<TvChannel>,
    apps: TvAppsUiState,
    appIcons: Map<String, File>,
    onLoadAppIcon: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onRelease: () -> Unit,
    onTune: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onCloseNumbers: () -> Unit,
    onPickerLaunch: (String) -> Unit,
    closeApps: () -> Unit,
) {
    if (numbers) {
        NumberPadSheet(channels, onDismiss = {
            onCloseNumbers()
        }, onTune = { id ->
            onCloseNumbers()
            onTune(id)
        })
    }
    if (appPicker) {
        TvAppsSheet(apps, appIcons, onLoadAppIcon, { id ->
            onRelease()
            onPickerLaunch(id)
            onOpenApp(id)
        }, onRefreshApps, closeApps)
    }
}
