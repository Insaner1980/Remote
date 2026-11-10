package com.emmah.colordock.remote.ui.mediacenter

internal data class SourceActions(
    val refresh: () -> Unit,
    val next: () -> Unit,
    val scan: (String) -> Unit,
    val remove: (String) -> Unit,
    val cancelScan: (() -> Unit)?,
    val folders: FolderActions,
    val cancelRecoveredScan: (String) -> Unit = {},
)
