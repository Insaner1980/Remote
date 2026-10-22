package com.emmah.colordock.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.tv.TvChannel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPadSheet(channels: List<TvChannel>, onDismiss: () -> Unit, onTune: (String) -> Unit) {
    var digits by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val matches = channels.filter { it.number == digits }
    val channel = matches.singleOrNull()?.id ?: selected?.takeIf { id -> matches.any { it.id == id } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Channel number", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = digits, onValueChange = {}, readOnly = true, label = { Text("Number") }, modifier = Modifier.fillMaxWidth())
            for (row in listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"))) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (digit in row) Button(onClick = { if (digits.length < 5) { digits += digit; selected = null } }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text(digit) }
                }
            }
            Button(onClick = { if (digits.length < 5) { digits += "0"; selected = null } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("0") }
            OutlinedButton(onClick = { digits = digits.dropLast(1); selected = null }, enabled = digits.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Delete") }
            if (channels.isEmpty()) Text("The channel list is unavailable. Open Live TV and reconnect to try again.")
            else if (digits.isNotEmpty() && matches.isEmpty()) Text("No matching channel. Check the channel number.")
            if (matches.size > 1) {
                Text("Choose a channel")
                for (match in matches) OutlinedButton(onClick = { selected = match.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("${match.number}: ${match.name}${if (selected == match.id) ", Selected" else ""}") }
            }
            Button(onClick = { channel?.let(onTune) }, enabled = channel != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Open Live TV and tune") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Cancel") }
        }
    }
}
