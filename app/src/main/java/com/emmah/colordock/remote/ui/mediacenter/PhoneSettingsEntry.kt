package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R

@Composable
internal fun PhoneSettingsEntry(
    section: SettingsSection,
    onConnection: () -> Unit,
    onAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when (section) {
            SettingsSection.Devices -> {
                ListGroup(Modifier.padding(top = 24.dp)) {
                    ListRow(
                        "Connection",
                        null,
                        ListRowPosition.Single,
                        Modifier.semantics { contentDescription = "Mediacenter connection" },
                        leadingIcon = R.drawable.ic_settings,
                        onClick = onConnection,
                    )
                }
            }

            SettingsSection.Appearance -> {
                SectionLabel("Phone")
                ListGroup {
                    ListRow(
                        "Phone appearance",
                        null,
                        ListRowPosition.Single,
                        Modifier.semantics { contentDescription = "Phone appearance" },
                        onClick = onAppearance,
                    )
                }
            }

            else -> {}
        }
    }
}
