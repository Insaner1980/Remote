package com.emmah.colordock.remote.ui

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
            Text("Channel number", style = MaterialTheme.typography.headlineSmall)
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
                Text("The channel list is unavailable. Open Live TV and reconnect to try again.")
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

@Composable
private fun ColumnScope.ChannelKeypad(
    digits: String,
    onDigits: (String) -> Unit,
) {
    for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (digit in row) {
                Button(onClick = {
                    if (digits.length <
                        MAX_CHANNEL_DIGITS
                    ) {
                        onDigits(digits + digit)
                    }
                }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                    Text(digit)
                }
            }
        }
    }
    Button(onClick = {
        if (digits.length <
            MAX_CHANNEL_DIGITS
        ) {
            onDigits(digits + "0")
        }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("0")
    }
    OutlinedButton(onClick = {
        onDigits(digits.dropLast(1))
    }, enabled = digits.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("Delete")
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
