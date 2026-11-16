package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.PendingCommand

internal data class MediaFeedbackState(
    val pending: List<PendingCommand> = emptyList(),
    val uncertain: Boolean = false,
    val recoveryAvailable: Boolean = true,
)
