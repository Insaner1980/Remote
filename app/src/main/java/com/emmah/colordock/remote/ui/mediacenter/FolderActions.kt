package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.DirectoryNode
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind

internal data class FolderActions(
    val browse: (DirectoryNode?) -> Unit,
    val next: () -> Unit,
    val refresh: () -> Unit,
    val choose: (DirectoryNode, List<MediaKind>) -> Unit,
)
