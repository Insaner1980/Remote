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

internal data class SettingsSummaries(
    val sound: String? = null,
    val vpn: String? = null,
    val media: String? = null,
    val devices: String? = null,
    val appearance: String? = null,
    val advanced: String? = null,
) {
    fun forSection(section: SettingsSection): String? =
        when (section) {
            SettingsSection.Sound -> sound
            SettingsSection.VPN -> vpn
            SettingsSection.Media -> media
            SettingsSection.Devices -> devices
            SettingsSection.Appearance -> appearance
            SettingsSection.Advanced -> advanced
        }
}

@Composable
internal fun SettingsOverview(
    onSelect: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    summaries: SettingsSummaries = SettingsSummaries(),
) {
    ListGroup(modifier.fillMaxWidth()) {
        SettingsSection.entries.forEachIndexed { index, section ->
            ListRow(
                section.name,
                summaries.forSection(section),
                when (index) {
                    0 -> ListRowPosition.First
                    SettingsSection.entries.lastIndex -> ListRowPosition.Last
                    else -> ListRowPosition.Middle
                },
                Modifier.testTag("settings-section-${section.name}"),
                leadingIcon = section.icon,
                onClick = { onSelect(section) },
            )
        }
    }
}
