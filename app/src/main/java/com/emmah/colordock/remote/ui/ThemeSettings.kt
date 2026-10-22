package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
    IconButton(onClick, Modifier.size(48.dp)) {
        DockIcon(R.drawable.ic_settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun ThemeSettings(theme: AppTheme, onSelect: (AppTheme) -> Unit, onDismiss: () -> Unit, error: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("App theme", style = MaterialTheme.typography.titleMedium)
                Text("Choose how Remote looks on this phone.")
                Column(Modifier.selectableGroup()) {
                    AppTheme.entries.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .selectable(theme == option, role = Role.RadioButton, onClick = { onSelect(option) })
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = theme == option, onClick = null)
                            Text(option.label, Modifier.weight(1f))
                        }
                    }
                }
                error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}
