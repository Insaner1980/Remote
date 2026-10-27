package com.emmah.colordock.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.companion.DockConnectionState
import com.emmah.colordock.remote.companion.DockPatch
import com.emmah.colordock.remote.companion.DockSnapshot
import com.emmah.colordock.remote.companion.DockState
import com.emmah.colordock.remote.companion.WeatherLocation
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
fun AppShelfScreen(
    snapshot: DockSnapshot,
    saving: Boolean,
    error: String?,
    onSave: (List<String>, List<String>, Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var order by rememberSaveable { mutableStateOf(snapshot.shelfOrder) }
    var hidden by rememberSaveable { mutableStateOf(snapshot.shelfHidden) }
    val revision =
        rememberSaveable { snapshot.revision }
    var localError by remember { mutableStateOf<String?>(null) }
    var saveRequested by rememberSaveable { mutableStateOf(false) }
    val currentOnCancel by rememberUpdatedState(onCancel)
    LaunchedEffect(snapshot.revision, saving, error, saveRequested) {
        if (saveRequested && !saving) {
            if (error != null) {
                saveRequested = false
            } else if (snapshot.revision > revision && snapshot.shelfOrder == order &&
                snapshot.shelfHidden.toSet() == hidden.toSet()
            ) {
                currentOnCancel()
            }
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Edit app shelf", style = MaterialTheme.typography.headlineSmall)
        Text("Choose the order of your Color Dock apps.")
        ShelfPreview(order, hidden)
        order.forEachIndexed { index, id ->
            ShelfAppRow(index, id, order, hidden, saving, { order = it }, { hidden = it }, { localError = it })
        }
        (error ?: localError)?.let {
            Text(
                it,
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button({
            saveRequested = true
            onSave(order, hidden, revision)
        }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !saving) {
            Text(if (saving) "Saving\u2026" else "Save")
        }
        TextButton(onCancel, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !saving) {
            Text("Cancel")
        }
    }
}

// Explicit state and callbacks preserve the hoisted Compose API.
@Suppress("LongParameterList")
@Composable
private fun ShelfAppRow(
    index: Int,
    id: String,
    order: List<String>,
    hidden: List<String>,
    saving: Boolean,
    onOrder: (List<String>) -> Unit,
    onHiddenChange: (List<String>) -> Unit,
    onError: (String?) -> Unit,
) {
    val name =
        shelfApps
            .firstOrNull {
                it.first == id
            }?.second ?: id
    OutlinedCard(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(
                    id !in hidden,
                    { visible ->
                        if (!visible &&
                            order.none {
                                it != id && it !in hidden
                            }
                        ) {
                            onError("Keep at least one app visible.")
                        } else {
                            onHiddenChange(if (visible) hidden - id else hidden + id)
                            onError(null)
                        }
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "Show $name"
                        },
                    enabled = !saving,
                )
            }
            ShelfReorderActions(name, index, order, saving, onOrder)
        }
    }
}

@Composable
private fun ShelfPreview(
    order: List<String>,
    hidden: List<String>,
) {
    Text(
        "Preview: " +
            order
                .filterNot {
                    it in hidden
                }.joinToString(", ") { id ->
                    shelfApps
                        .firstOrNull {
                            it.first == id
                        }?.second ?: id
                },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ShelfReorderActions(
    name: String,
    index: Int,
    order: List<String>,
    saving: Boolean,
    onOrder: (List<String>) -> Unit,
) {
    Row {
        TextButton(
            {
                onOrder(
                    order.toMutableList().apply {
                        add(index - 1, removeAt(index))
                    },
                )
            },
            enabled = index > 0 && !saving,
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription =
                        "Move $name up"
                },
        ) {
            DockIcon(R.drawable.ic_keyboard_arrow_up)
            Text("Move up")
        }
        TextButton(
            {
                onOrder(
                    order.toMutableList().apply {
                        add(index + 1, removeAt(index))
                    },
                )
            },
            enabled = index < order.lastIndex && !saving,
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription =
                        "Move $name down"
                },
        ) {
            DockIcon(R.drawable.ic_keyboard_arrow_down)
            Text("Move down")
        }
    }
}
