package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R

enum class RemoteTab(
    val label: String,
    val icon: Int,
) {
    Remote(
        "Remote",
        R.drawable.ic_settings_remote,
    ),
    Wallpapers("Wallpapers", R.drawable.ic_image),
    Dock("Color Dock", R.drawable.ic_grid_view),
}

@Composable
fun RemoteAppShell(
    selected: RemoteTab,
    onSelect: (RemoteTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        Surface(
            modifier = Modifier.testTag("bottom-navigation"),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                    RemoteTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected == tab,
                            {
                                onSelect(tab)
                            },
                            icon = {
                                DockIcon(tab.icon, Modifier.testTag("navigation-icon-${tab.label}"))
                            },
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
                                        tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            },
                            alwaysShowLabel = true,
                            colors =
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                    }
                }
            }
        }
    }) { padding ->
        content(Modifier.padding(padding))
    }
}

/** Only LG owns the global Remote / Wallpapers / Color Dock bar. */
@Composable
internal fun TargetAppShell(
    target: ControlTargetState,
    selected: RemoteTab,
    onSelect: (RemoteTab) -> Unit,
    mediacenter: @Composable (Modifier) -> Unit,
    television: @Composable (Modifier) -> Unit,
) {
    key(target.selected, target.generation) {
        when (target.selected) {
            ControlTarget.LgTv -> {
                RemoteAppShell(selected, onSelect, content = television)
            }

            ControlTarget.Mediacenter -> {
                Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                    mediacenter(Modifier.padding(padding))
                }
            }
        }
    }
}
