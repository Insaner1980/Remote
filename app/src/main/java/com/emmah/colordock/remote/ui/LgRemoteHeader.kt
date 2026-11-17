package com.emmah.colordock.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState

@Composable
internal fun RemoteHeader(
    onSettings: () -> Unit,
    selector: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap),
    ) {
        Column(Modifier.weight(1f)) { selector() }
        SettingsButton(onSettings)
        trailing()
    }
}

@Composable
internal fun RemoteControlsHeader(
    state: TvState,
    onConnection: () -> Unit,
    onSettings: () -> Unit,
    onPowerConfirmation: () -> Unit,
    targetSelector: (@Composable () -> Unit)?,
) {
    val enabled = state.connection == TvConnectionState.Ready
    RemoteHeader(
        onSettings,
        selector =
            targetSelector ?: {
                TextButton(
                    onConnection,
                    Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            state.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            if (enabled) "Connected" else "TV unavailable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
    ) {
        OutlinedIconButton(
            onClick = {
                onPowerConfirmation()
            },
            enabled = enabled,
            modifier =
                Modifier.size(
                    48.dp,
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            DockIcon(
                R.drawable.ic_power_settings_new,
                modifier = Modifier.size(30.dp),
                label = "Turn off TV",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
