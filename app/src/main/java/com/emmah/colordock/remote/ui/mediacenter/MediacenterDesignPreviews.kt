package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.AppTheme
import com.emmah.colordock.remote.ui.RemoteTheme

@Suppress("UnusedPrivateMember")
@Preview(name = "Mediacenter components dark", showBackground = true)
@Composable
private fun DarkComponentsPreview() {
    RemoteTheme(AppTheme.SoftDark) { ComponentsPreview() }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Mediacenter components light", showBackground = true)
@Composable
private fun LightComponentsPreview() {
    RemoteTheme(AppTheme.WarmLight) { ComponentsPreview() }
}

@Composable
private fun ComponentsPreview() {
    Surface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceSelector("Mediacenter", true, SelectorSize.Large, {})
            DeviceSelector("Mediacenter", true, SelectorSize.Compact, {})
            SectionLabel("Sound")
            ListGroup {
                ListRow("TV (HDMI)", "Connected", ListRowPosition.First, leadingIcon = R.drawable.ic_tv, onClick = {})
                ListRow("Speakers", null, ListRowPosition.Last, leadingIcon = R.drawable.ic_tv, onClick = {})
            }
            MediaKey("Menu", R.drawable.ic_settings, true, {})
            MediaIconButton(R.drawable.ic_settings, "Settings", {}, true, emphasized = true)
            PillSearchField("", {}, "Search")
        }
    }
}
