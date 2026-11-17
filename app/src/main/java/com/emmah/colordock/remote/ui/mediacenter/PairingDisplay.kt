package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val QR_QUIET_ZONE = 4
private const val QR_MIN_SIDE = 21
private const val QR_MAX_SIDE = 177

internal data class PairingDisplayState(
    val attemptId: String,
    val rows: List<String>?,
) {
    override fun toString(): String = "PairingDisplayState([redacted])"
}

@Composable
internal fun PairingDisplay(
    state: PairingDisplayState,
    dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = state.rows?.takeIf(::validPairingRows)
    AlertDialog(
        onDismissRequest = dismiss,
        modifier = modifier,
        title = { Text("Add phone") },
        text = {
            Column {
                if (rows == null) {
                    ListRow("QR code unavailable", null, ListRowPosition.Single)
                } else {
                    ListRow("Scan with the other phone", null, ListRowPosition.Single)
                    PairingQr(rows)
                }
            }
        },
        confirmButton = { Button(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
private fun PairingQr(
    rows: List<String>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(280.dp)) {
        drawRect(Color.White)
        val module = size.minDimension / (rows.size + QR_QUIET_ZONE * 2)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, value ->
                if (value == '1') {
                    drawRect(
                        Color.Black,
                        Offset((x + QR_QUIET_ZONE) * module, (y + QR_QUIET_ZONE) * module),
                        Size(module, module),
                    )
                }
            }
        }
    }
}

internal fun validPairingRows(rows: List<String>): Boolean =
    rows.size in QR_MIN_SIDE..QR_MAX_SIDE &&
        rows.all { row -> row.length == rows.size && row.all { it == '0' || it == '1' } }
