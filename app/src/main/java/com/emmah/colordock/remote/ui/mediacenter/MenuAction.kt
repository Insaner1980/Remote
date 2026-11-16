package com.emmah.colordock.remote.ui.mediacenter

internal data class MenuAction(
    val label: String,
    val enabled: Boolean = true,
    val run: () -> Unit,
)
