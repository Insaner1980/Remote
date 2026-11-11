package com.emmah.colordock.remote.ui.mediacenter

internal data class DriveActions(
    val refresh: () -> Unit,
    val mount: (String) -> Unit,
    val eject: (String) -> Unit,
    val cancel: (() -> Unit)? = null,
)
