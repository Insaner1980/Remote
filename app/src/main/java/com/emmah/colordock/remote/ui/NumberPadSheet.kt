package com.emmah.colordock.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.tv.TvChannel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPadSheet(
    channels: List<TvChannel>,
    onDismiss: () -> Unit,
    onTune: (String) -> Unit,
) {
    var digits by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val matches =
        channels.filter {
            it.number == digits
        }
    val channel =
        matches.singleOrNull()?.id ?: selected?.takeIf { id ->
            matches.any {
                it.id == id
            }
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Channel number", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = digits,
                onValueChange = {},
                readOnly = true,
                label = {
                    Text("Number")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ChannelKeypad(digits) {
                digits = it
                selected = null
            }
            if (channels.isEmpty()) {
                Text("Channels unavailable")
            } else if (digits.isNotEmpty() && matches.isEmpty()) {
                Text("No matching channel. Check the channel number.")
            }
            ChannelMatches(matches, selected) { selected = it }
            Button(onClick = {
                channel?.let(onTune)
            }, enabled = channel != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Open Live TV and tune")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Cancel")
            }
        }
    }
}

private const val MAX_CHANNEL_DIGITS = 5
private const val LAST_CHANNEL_DIGIT = 9
private const val DIGITS_PER_ROW = 3

@Composable
private fun ColumnScope.ChannelKeypad(
    digits: String,
    onDigits: (String) -> Unit,
) {
    for (row in (1..LAST_CHANNEL_DIGIT).chunked(DIGITS_PER_ROW)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RemoteDimens.ItemGap)) {
            for (digit in row) {
                ChannelDigit(digit, digits, onDigits, Modifier.weight(1f))
            }
        }
    }
    ChannelDigit(0, digits, onDigits, Modifier.fillMaxWidth())
    OutlinedButton(
        onClick = { onDigits(digits.dropLast(1)) },
        enabled = digits.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().heightIn(min = RemoteDimens.KeyHeight),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        DialogActionLabel("Delete", R.drawable.ic_backspace)
    }
}

@Composable
private fun ChannelDigit(
    digit: Int,
    digits: String,
    onDigits: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = { if (digits.length < MAX_CHANNEL_DIGITS) onDigits(digits + digit) },
        modifier = modifier.heightIn(min = RemoteDimens.TileHeight),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
            ),
    ) {
        Text(
            digit.toString(),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                ),
        )
    }
}

@Composable
private fun ColumnScope.ChannelMatches(
    matches: List<TvChannel>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    if (matches.size > 1) {
        Text("Choose a channel")
        for (match in matches) {
            OutlinedButton(onClick = {
                onSelect(match.id)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("${match.number}: ${match.name}${if (selected == match.id) ", Selected" else ""}")
            }
        }
    }
}
