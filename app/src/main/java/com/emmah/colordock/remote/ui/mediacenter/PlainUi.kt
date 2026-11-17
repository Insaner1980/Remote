package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.ui.DockIcon

@Composable
internal fun ItemActions(
    label: String,
    actions: List<MenuAction>,
    enabled: Boolean = true,
    buttonTag: String? = null,
) {
    var open by remember(label) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = if (buttonTag == null) Modifier else Modifier.testTag(buttonTag),
            enabled = enabled,
        ) {
            DockIcon(R.drawable.ic_more_vert, label = label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    enabled = action.enabled,
                    colors =
                        MenuDefaults.itemColors(
                            textColor =
                                if (action.destructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        ),
                    onClick = {
                        open = false
                        action.run()
                    },
                )
            }
        }
    }
}

@Composable
internal fun RefreshAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Refresh",
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) { DockIcon(R.drawable.ic_refresh, label = label) }
}

internal fun mediaTitle(name: String): String =
    name
        .replace(
            Regex("(?i)\\.(mp4|mkv|avi|mov|webm|m4v|mp3|flac|wav|ogg|m4a|aac|opus|jpg|jpeg|png|webp|gif|heic)$"),
            "",
        ).replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Untitled" }

internal fun soundOutputName(name: String): String {
    val builtin = name.startsWith("Built-in Audio", true) || name.startsWith("Internal Audio", true)
    return when {
        builtin && name.contains("headphone", true) -> {
            "Headphones"
        }

        builtin && name.contains("HDMI", true) -> {
            val port = Regex("(?i)HDMI\\s*(\\d+)").find(name)?.groupValues?.get(1)
            if (port == null) "TV (HDMI)" else "TV (HDMI $port)"
        }

        builtin && (name.contains("IEC958", true) || name.contains("optical", true)) -> {
            "Optical audio"
        }

        builtin -> {
            "Computer audio"
        }

        else -> {
            name
                .replace("Digital Stereo", "", true)
                .replace("Analog Stereo", "", true)
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "Sound output" }
        }
    }
}

internal fun soundOutputLabels(
    outputs: List<com.emmah.colordock.remote.mediacenter.protocol.AudioOutput>,
): Map<String, String> {
    val bases = outputs.map { soundOutputName(it.name) }
    val counts = bases.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return outputs
        .mapIndexed { index, output ->
            val base = bases[index]
            val number = (seen[base] ?: 0) + 1
            seen[base] = number
            output.id to
                if (counts.getValue(base) == 1) {
                    base
                } else if (base.endsWith("(HDMI)")) {
                    base.removeSuffix("(HDMI)") + "(HDMI $number)"
                } else {
                    "$base $number"
                }
        }.toMap()
}

internal fun photoTitle(
    name: String,
    number: Int,
): String {
    val title = mediaTitle(name)
    val opaque = generatedPhotoName.matches(title)
    return if (opaque) "Photo $number" else title
}

internal fun operationLabel(status: OperationStatus): String =
    when (status) {
        OperationStatus.ACCEPTED, OperationStatus.RUNNING -> "Working…"
        OperationStatus.SUCCEEDED -> "Finished"
        OperationStatus.FAILED -> "Couldn't finish"
        OperationStatus.CANCELLED -> "Cancelled"
        OperationStatus.OUTCOME_UNKNOWN -> "Not confirmed"
    }

internal fun playbackTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val minutes = total / SECONDS_PER_MINUTE
    val remainder = (total % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    return "$minutes:$remainder"
}

private const val SECONDS_PER_MINUTE = 60

private val generatedPhotoName =
    Regex(
        "(?i)([a-f0-9]{16,}|file[ _-]?[a-f0-9]+|" +
            "(?:FB )?IMG[ _-]?\\d[\\d _-]*(?:WA\\d+|BURST\\d+)?|" +
            "Screenshot[ _-]?[\\d _-]+|\\d{8}[ _-]\\d{6}(?:[ _-]\\d+)?|" +
            "[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})",
    )
