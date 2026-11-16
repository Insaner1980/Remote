package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ListStatus(
    loading: Boolean,
    connected: Boolean,
    message: String,
    empty: Boolean,
    kind: String,
) {
    Column {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!connected || loading) return@Column
        if (message.isNotEmpty()) {
            Text("Couldn't load $kind")
        } else if (empty) {
            Text(if (kind == "drives") "No USB drive" else "No folders")
        }
    }
}
