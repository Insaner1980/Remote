package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun PhoneSettingsEntry(
    section: SettingsSection,
    onConnection: () -> Unit,
    onAppearance: () -> Unit,
) {
    when (section) {
        SettingsSection.Devices -> {
            TextButton(onClick = onConnection) { Text("Mediacenter connection") }
        }

        SettingsSection.Appearance -> {
            TextButton(onClick = onAppearance) { Text("Phone appearance") }
        }

        else -> {}
    }
}
