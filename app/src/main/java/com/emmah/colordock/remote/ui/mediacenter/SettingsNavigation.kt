package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.DockIcon

enum class SettingsSection(
    val icon: Int,
) {
    Sound(R.drawable.ic_volume_up),
    VPN(R.drawable.ic_shield),
    Media(R.drawable.ic_smart_display),
    Devices(R.drawable.ic_devices),
    Appearance(R.drawable.ic_brush),
    Advanced(R.drawable.ic_settings_gear),
}

internal class SettingsNavigation {
    var section by mutableStateOf<SettingsSection?>(null)
}

@Composable
internal fun SettingsOverview(
    onSelect: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        SettingsSection.entries.forEach { section ->
            val subdued = section == SettingsSection.Advanced
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("settings-section-${section.name}")
                    .clickable { onSelect(section) }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DockIcon(
                    section.icon,
                    tint =
                        if (subdued) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
                Text(
                    section.name,
                    Modifier.weight(1f),
                    fontSize = 18.sp,
                    color =
                        if (subdued) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                )
                DockIcon(R.drawable.ic_keyboard_arrow_right, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
