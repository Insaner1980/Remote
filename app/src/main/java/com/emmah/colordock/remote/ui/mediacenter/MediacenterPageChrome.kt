package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.DockIcon

@Composable
internal fun MediacenterPageHeading(
    destination: MediaDestination,
    section: SettingsSection?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    attention: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (destination == MediaDestination.Settings && section != null) {
            IconButton(onClick = onBack) { DockIcon(R.drawable.ic_keyboard_arrow_left, label = "Back to settings") }
        }
        Text(
            if (destination == MediaDestination.Settings) section?.name ?: "Settings" else destination.name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        attention()
    }
}

@Composable
internal fun MediacenterBottomNavigation(
    selected: MediaDestination,
    onSelect: (MediaDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier.testTag("mediacenter-bottom-navigation"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0),
    ) {
        MediaDestination.entries.forEach { destination ->
            val icon =
                when (destination) {
                    MediaDestination.Remote -> R.drawable.ic_settings_remote
                    MediaDestination.Library -> R.drawable.ic_library_books
                    MediaDestination.Settings -> R.drawable.ic_settings_gear
                }
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                modifier = Modifier.testTag("media-nav-${destination.name}"),
                icon = { DockIcon(icon) },
                label = { Text(destination.name) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
