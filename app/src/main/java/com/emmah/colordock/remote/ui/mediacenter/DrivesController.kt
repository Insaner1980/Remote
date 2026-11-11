package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Drive
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

internal data class DrivesState(
    val items: List<Drive> = emptyList(),
    val recovered: List<RecoveredDrive> = emptyList(),
    val connected: Boolean = false,
    val loading: Boolean = false,
    val message: String = "Connect to manage drives.",
)

/** Drive observations are refreshed by the native sources revision, never inferred from acceptance. */
internal class DrivesController(
    private val send: (McRequest) -> Boolean,
) {
    val recovery = RecoveredDrives()
    private val mutable = MutableStateFlow(DrivesState())
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
            mutable.value = DrivesState(connected = epoch != null && owner != null)
        }
        if (state.value.connected && (changed || updated)) refresh()
    }

    fun refresh() {
        request()
    }

    private fun request() {
        val current = epoch ?: return
        val id = UUID.randomUUID().toString()
        pending = id
        val submitted =
            send(
                McRequest(
                    id,
                    RemoteCommand.DrivesList(Empty),
                    Expected(serverEpoch = current),
                ),
            )
        mutable.value =
            state.value.copy(loading = submitted, message = if (submitted) "" else "Reconnect and refresh drives.")
        if (!submitted) pending = null
    }

    fun receive(reply: McReply) {
        recovery.receive(reply)
        if (reply.id != pending) return
        pending = null
        mutable.value =
            when (reply) {
                is McReply.Failure -> {
                    state.value.copy(loading = false, message = reply.error.message)
                }

                is McReply.Success -> {
                    val page = (reply.result as? RemoteResult.DrivesList)?.value
                    state.value.copy(
                        items = page?.items.orEmpty(),
                        loading = false,
                        message = "",
                    )
                }
            }
    }
}
