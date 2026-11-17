package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.DockIcon
import com.emmah.colordock.remote.ui.NAV_LABEL_MAX_FONT_SCALE
import com.emmah.colordock.remote.ui.RemoteDimens

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
    val showHeadingSelector = !subpage && destination != MediaDestination.Remote
    BoxWithConstraints(modifier.testTag("mediacenter-page-heading")) {
        val largeText = LocalDensity.current.fontScale >= HEADING_LARGE_FONT_SCALE
        val selectorOnSeparateLine =
            showHeadingSelector &&
                (maxWidth < 340.dp || (largeText && maxWidth < 440.dp))
        Column {
            MediacenterHeadingRow {
                if (subpage) {
                    MediacenterSubpageTitle(section.name, "Back to settings", onBack)
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
                if (showHeadingSelector && !selectorOnSeparateLine) deviceSelector()
            }
            if (selectorOnSeparateLine) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { deviceSelector() }
            }
        }
    }
}

private const val HEADING_LARGE_FONT_SCALE = 1.4f

@Composable
internal fun MediacenterSubpageHeading(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    MediacenterHeadingRow {
        MediacenterSubpageTitle(title, "Back", onBack)
        actions()
    }
}

@Composable
private fun MediacenterHeadingRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = RemoteDimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap),
        content = content,
    )
}

@Composable
private fun RowScope.MediacenterSubpageTitle(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
) {
    IconButton(onClick = onBack) { DockIcon(R.drawable.ic_keyboard_arrow_left, label = backLabel) }
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Mediacenter",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                icon = { DockIcon(icon, Modifier.testTag("navigation-icon-${destination.name}")) },
                label = {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides
                            Density(
                                density.density,
                                fontScale = density.fontScale.coerceAtMost(NAV_LABEL_MAX_FONT_SCALE),
                            ),
                    ) {
                        Text(
                            destination.name,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
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
