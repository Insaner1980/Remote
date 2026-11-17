package com.emmah.colordock.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R

@Composable
internal fun SettingsButton(onClick: () -> Unit) {
    OutlinedIconButton(
        onClick,
        Modifier.size(48.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        DockIcon(R.drawable.ic_contrast, label = "Settings", tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Suppress("LongParameterList")
@Composable
fun ThemeSettings(
    theme: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    sound: @Composable () -> Unit = {},
) {
    LgDismissDialog("Appearance", "Done", onDismiss, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.selectableGroup()) {
                AppTheme.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .selectable(theme == option, role = Role.RadioButton, onClick = {
                                onSelect(option)
                            })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = theme == option, onClick = null)
                        Text(if (option == AppTheme.SoftDark) "Dark" else "Light", Modifier.weight(1f))
                    }
                }
            }
            sound()
            error?.let {
                Text(
                    it,
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
