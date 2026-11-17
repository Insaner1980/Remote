package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.ui.RemoteDimens
import com.emmah.colordock.remote.ui.RemoteShapes

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
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RemoteDimens.BlockGap),
            ) {
                Text(
                    if (rows == null) "QR code unavailable" else "Scan with the other phone",
                    Modifier.align(Alignment.Start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rows != null) PairingQr(rows)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
private fun PairingQr(
    rows: List<String>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RemoteShapes.Inner),
    ) {
        drawRect(Color.White)
        // Keep both the requested 16 dp border and the QR spec's four-module quiet zone.
        val minQuietZone = minOf(16.dp.toPx(), size.minDimension / QR_QUIET_ZONE)
        val module =
            minOf(
                (size.minDimension - 2 * minQuietZone) / rows.size,
                size.minDimension / (rows.size + QR_QUIET_ZONE * 2),
            )
        val inset = (size.minDimension - rows.size * module) / 2
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, value ->
                if (value == '1') {
                    drawRect(
                        Color.Black,
                        Offset(inset + x * module, inset + y * module),
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
