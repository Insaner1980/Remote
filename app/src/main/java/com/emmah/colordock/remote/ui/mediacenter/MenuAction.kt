package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.R

internal data class MenuAction(
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val destructiveIcon: Int = R.drawable.ic_delete,
    val run: () -> Unit,
)
