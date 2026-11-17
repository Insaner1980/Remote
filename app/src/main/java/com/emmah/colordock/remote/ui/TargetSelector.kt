package com.emmah.colordock.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.ui.mediacenter.DeviceSelector
import com.emmah.colordock.remote.ui.mediacenter.SelectorSize

/** The device-name area of each target's existing header owns selection and connection access. */
@Suppress("LongParameterList")
@Composable
internal fun TargetSelector(
    state: ControlTargetState,
    tvName: String,
    onSelect: (ControlTarget) -> Unit,
    onConnection: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val name =
        if (state.selected ==
            ControlTarget.LgTv
        ) {
            tvName.ifBlank { ControlTarget.LgTv.label }
        } else {
            ControlTarget.Mediacenter.label
        }
    Column(modifier) {
        if (state.selected == ControlTarget.Mediacenter) {
            DeviceSelector(
                name,
                state.ready(state.selected),
                if (compact) SelectorSize.Compact else SelectorSize.Large,
                { open = true },
            )
        } else {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth().testTag("target-selector"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Change device", style = MaterialTheme.typography.labelMedium)
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (state.ready(state.selected)) "Connected" else "Not connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DockIcon(R.drawable.ic_keyboard_arrow_down)
            }
        }
        if (open) {
            TargetSelectionDialog(state, { target ->
                open = false
                onSelect(target)
            }, {
                open = false
                onConnection()
            }, { open = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSelectionDialog(
    state: ControlTargetState,
    onSelect: (ControlTarget) -> Unit,
    onConnection: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.selected == ControlTarget.Mediacenter) {
        MediacenterTargetSelectionSheet(state, onSelect, onConnection, onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control device") },
        text = {
            Column {
                ControlTarget.entries.forEach { target ->
                    TextButton(onClick = { onSelect(target) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(target.label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            if (target == state.selected) {
                                DockIcon(
                                    R.drawable.ic_check,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (state.ready(target)) "Connected" else "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                TextButton(onClick = onConnection) { Text("${state.selected.label} connection") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// Picker rows are local to the Mediacenter sheet, never used by LG dialogs.
@Suppress("LongParameterList")
@Composable
private fun SheetTargetRow(
    title: String,
    supporting: String?,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                DockIcon(icon, tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            DockIcon(R.drawable.ic_check, tint = colors.onSecondaryContainer)
        } else if (supporting == null) {
            DockIcon(R.drawable.ic_keyboard_arrow_right, tint = colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediacenterTargetSelectionSheet(
    state: ControlTargetState,
    onSelect: (ControlTarget) -> Unit,
    onConnection: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                "Control device",
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            ControlTarget.entries.forEach { target ->
                SheetTargetRow(
                    target.label,
                    if (state.ready(target)) "Connected" else "Not connected",
                    R.drawable.ic_tv,
                    { onSelect(target) },
                    selected = target == state.selected,
                )
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SheetTargetRow("${state.selected.label} connection", null, R.drawable.ic_settings, onConnection)
        }
    }
}
