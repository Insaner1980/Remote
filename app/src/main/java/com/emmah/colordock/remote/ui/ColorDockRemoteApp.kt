package com.emmah.colordock.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R

enum class RemoteTab(val label:String,val icon:Int) { Remote("Remote",R.drawable.ic_settings_remote),Wallpapers("Wallpapers",R.drawable.ic_image),Dock("Color Dock",R.drawable.ic_grid_view) }

@Composable
fun RemoteAppShell(selected:RemoteTab,onSelect:(RemoteTab)->Unit,content:@Composable (Modifier)->Unit) {
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
        Surface(modifier=Modifier.testTag("bottom-navigation"),color=MaterialTheme.colorScheme.background,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            NavigationBar(containerColor=Color.Transparent,tonalElevation=0.dp) {
                RemoteTab.entries.forEach {tab->NavigationBarItem(selected==tab,{onSelect(tab)},icon={DockIcon(tab.icon)},label={Text(tab.label)},alwaysShowLabel=true,
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.onPrimaryContainer,selectedTextColor=MaterialTheme.colorScheme.onBackground,indicatorColor=MaterialTheme.colorScheme.primaryContainer,unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant))}
            }
        }
    }){padding->content(Modifier.padding(padding))}
}
