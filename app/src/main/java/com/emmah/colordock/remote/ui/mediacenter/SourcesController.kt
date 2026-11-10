package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.Source
import com.emmah.colordock.remote.mediacenter.protocol.SourcesListParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal data class SourcesState(
    val items: List<Source> = emptyList(),
    val recoveredScans: List<OperationState> = emptyList(),
    val cursor: String? = null,
    val connected: Boolean = false,
    val loading: Boolean = false,
    val message: String = "Connect to manage sources.",
)

/** Source observations are refreshed by the native sources revision, never inferred from acceptance. */
internal class SourcesController(
    private val send: (McRequest) -> Boolean,
) {
    val recovered = RecoveredSourceScans()
    private val mutable = MutableStateFlow(SourcesState())
    val state = mutable.asStateFlow()
    private var epoch: String? = null
    private var owner: String? = null
    private var revision: String? = null
    private var pending: String? = null

    fun observe(
        epoch: String?,
        owner: String?,
        revision: String?,
    ) {
        val changed = this.epoch != epoch || this.owner != owner
        val updated = this.revision != revision
        this.epoch = epoch
        this.owner = owner
        this.revision = revision
        if (changed) {
            pending = null
            mutable.value = SourcesState(connected = epoch != null && owner != null)
        }
        if (state.value.connected && (changed || updated)) refresh()
    }

    fun refresh() {
        request(null)
    }

    fun next() {
        state.value.cursor
            ?.takeUnless { state.value.loading }
            ?.let(::request)
    }

    private fun request(cursor: String?) {
        val current = epoch ?: return
        val id = UUID.randomUUID().toString()
        pending = id
        val submitted =
            send(
                McRequest(
                    id,
                    RemoteCommand.SourcesList(SourcesListParams(cursor, MEDIA_PAGE_SIZE)),
                    Expected(serverEpoch = current),
                ),
            )
        mutable.value =
            state.value.copy(loading = submitted, message = if (submitted) "" else "Reconnect and refresh sources.")
        if (!submitted) pending = null
    }

    fun receive(reply: McReply) {
        recovered.receive(reply)
        if (reply.id != pending) return
        pending = null
        mutable.value =
            when (reply) {
                is McReply.Failure -> {
                    state.value.copy(loading = false, message = reply.error.message)
                }

                is McReply.Success -> {
                    val page = (reply.result as? RemoteResult.SourcesList)?.value
                    state.value.copy(
                        items = page?.items.orEmpty(),
                        cursor = page?.nextCursor,
                        loading = false,
                        message = "",
                    )
                }
            }
    }
}
