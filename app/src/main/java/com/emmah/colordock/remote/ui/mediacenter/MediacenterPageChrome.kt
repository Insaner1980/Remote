package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.DockIcon

// Hoisted header slots preserve the target's navigation and recovery ownership.
@Suppress("LongParameterList")
@Composable
internal fun MediacenterPageHeading(
    destination: MediaDestination,
    section: SettingsSection?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    selector: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    attention: @Composable () -> Unit = {},
) {
    val currentSelector by rememberUpdatedState(selector)
    val deviceSelector = remember { movableContentOf { currentSelector() } }
    val subpage = destination == MediaDestination.Settings && section != null
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (subpage) {
            IconButton(onClick = onBack) { DockIcon(R.drawable.ic_keyboard_arrow_left, label = "Back to settings") }
            Column(Modifier.weight(1f)) {
                Text(
                    section.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Mediacenter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (destination == MediaDestination.Remote) {
            Box(Modifier.weight(1f)) { deviceSelector() }
        } else {
            Text(
                destination.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
        }
        actions()
        attention()
        if (!subpage && destination != MediaDestination.Remote) deviceSelector()
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
                label = { Text(destination.name, style = MaterialTheme.typography.labelMedium) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
