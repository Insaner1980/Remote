package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun PhoneSettingsEntry(
    section: SettingsSection,
    onConnection: () -> Unit,
    onAppearance: () -> Unit,
) {
    when (section) {
        SettingsSection.Devices -> {
            TextButton(
                onClick = onConnection,
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            "Mediacenter connection"
                    },
            ) { Text("Connection") }
        }

        SettingsSection.Appearance -> {
            TextButton(
                onClick = onAppearance,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Phone appearance"
                    },
            ) { Text("Phone") }
        }

        else -> {}
    }
}
