package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.DirectoryNode
import com.emmah.colordock.remote.mediacenter.protocol.DirectoryPage
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.FoldersChildrenParams
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal data class FolderState(
    val items: List<DirectoryNode> = emptyList(),
    val selected: DirectoryNode? = null,
    val cursor: String? = null,
    val loading: Boolean = false,
    val connected: Boolean = false,
    val message: String = "Connect to choose a folder.",
)

/** Safe reads only. Epoch/owner changes discard every opaque handle and late reply. */
internal class FolderController(
    private val send: (McRequest) -> Boolean,
) {
    private val mutable = MutableStateFlow(FolderState())
    val state = mutable.asStateFlow()
    private var epoch: String? = null
    private var owner: String? = null
    private var pending: String? = null
    private var requestedNode: DirectoryNode? = null

    fun observe(
        epoch: String?,
        owner: String?,
    ) {
        if (this.epoch == epoch && this.owner == owner) return
        this.epoch = epoch
        this.owner = owner
        pending = null
        requestedNode = null
        mutable.value = FolderState(connected = epoch != null && owner != null)
        if (state.value.connected) browse(null)
    }

    fun browse(node: DirectoryNode?) {
        request(node, null)
    }

    fun next() {
        val current = state.value
        if (!current.loading && current.cursor != null) request(current.selected, current.cursor)
    }

    fun refresh() {
        request(state.value.selected, null)
    }

    private fun request(
        node: DirectoryNode?,
        cursor: String?,
    ) {
        val current = epoch ?: return
        val command =
            if (node == null) {
                RemoteCommand.FoldersRoots(Empty)
            } else {
                RemoteCommand.FoldersChildren(FoldersChildrenParams(node.nodeId, cursor, MEDIA_PAGE_SIZE))
            }
        val id = UUID.randomUUID().toString()
        requestedNode = node
        pending = id
        val submitted = send(McRequest(id, command, Expected(serverEpoch = current)))
        mutable.value =
            state.value.copy(loading = submitted, message = if (submitted) "" else "Reconnect and refresh folders.")
        if (!submitted) pending = null
    }

    fun receive(reply: McReply) {
        if (reply.id != pending) return
        pending = null
        mutable.value =
            when (reply) {
                is McReply.Failure -> {
                    state.value.copy(
                        loading = false,
                        selected = null,
                        items = emptyList(),
                        cursor = null,
                        message = "${reply.error.message} Start from authorized folders again.",
                    )
                }

                is McReply.Success -> {
                    val page =
                        when (val result = reply.result) {
                            is RemoteResult.FoldersRoots -> result.value
                            is RemoteResult.FoldersChildren -> result.value
                            else -> DirectoryPage(emptyList())
                        }
                    state.value.copy(
                        items = page.items,
                        selected = requestedNode,
                        cursor = page.nextCursor,
                        loading = false,
                        message = "",
                    )
                }
            }
    }
}
