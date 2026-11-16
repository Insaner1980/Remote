package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.LibraryQueryParams
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.MediaKind
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

internal data class LibraryState(
    val kind: MediaKind = MediaKind.VIDEO,
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val cursor: String? = null,
    val selectionId: String? = null,
    val hasPrevious: Boolean = false,
    val loading: Boolean = false,
    val connected: Boolean = false,
    val message: String = "Connect to Mediacenter to browse.",
    val notice: ActionNotice? = null,
)

/** Cancels obsolete debounce/deadline work; late replies never replace a newer page. */
internal class LibraryViewModel(
    private val scope: CoroutineScope,
    private val send: (McRequest) -> Boolean,
) {
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var epoch: String? = null
    private var owner: String? = null
    private val previous = mutableListOf<String?>()
    private var currentCursor: String? = null
    private var pendingCursor: String? = null
    private var pendingHistory: List<String?> = emptyList()
    private var pending: String? = null
    private var work: Job? = null

    fun observe(
        value: String?,
        identity: String? = value,
    ) {
        if (epoch == value && owner == identity) return
        owner = identity
        previous.clear()
        currentCursor = null
        epoch = value
        work?.cancel()
        pending = null
        mutable.value = LibraryState(kind = state.value.kind, query = state.value.query, connected = value != null)
        if (value != null) refresh()
    }

    fun search(
        value: String,
        kind: MediaKind = state.value.kind,
    ) {
        if (value.codePointCount(0, value.length) > MEDIA_QUERY_LIMIT) {
            mutable.value =
                state.value.copy(
                    message = "Search accepts at most 200 characters.",
                    notice = ActionNotice.SHORTEN_SEARCH,
                )
            return
        }
        work?.cancel()
        pending = null
        previous.clear()
        currentCursor = null
        mutable.value = LibraryState(kind = kind, query = value, connected = epoch != null, loading = epoch != null)
        work =
            scope.launch {
                delay(MEDIA_SEARCH_DEBOUNCE_MS)
                request(null)
            }
    }

    fun refresh() {
        work?.cancel()
        previous.clear()
        currentCursor = null
        request(null)
    }

    fun next() {
        if (state.value.loading) return
        state.value.cursor?.let {
            request(it, (previous + listOf(currentCursor)).takeLast(MEDIA_PAGE_SIZE))
        }
    }

    fun previous() {
        if (!state.value.loading && previous.isNotEmpty()) request(previous.last(), previous.dropLast(1))
    }

    private fun request(
        cursor: String?,
        history: List<String?> = previous.toList(),
    ) {
        val current = epoch ?: return
        pendingCursor = cursor
        pendingHistory = history
        val id = UUID.randomUUID().toString()
        pending = id
        val request =
            McRequest(
                id,
                RemoteCommand.LibraryQuery(
                    LibraryQueryParams(
                        state.value.kind,
                        query = state.value.query,
                        cursor = cursor,
                        limit = LIBRARY_PAGE_SIZE,
                    ),
                ),
                Expected(serverEpoch = current),
            )
        if (!send(request)) {
            pending = null
            mutable.value =
                state.value.copy(
                    loading = false,
                    message = "Mediacenter is not ready. Retry.",
                    notice = ActionNotice.RETRY,
                )
            return
        }
        mutable.value =
            state.value.copy(loading = true, hasPrevious = previous.isNotEmpty(), message = "", notice = null)
        work =
            scope.launch {
                delay(MEDIA_REQUEST_TIMEOUT_MS)
                if (pending ==
                    id
                ) {
                    pending = null
                    mutable.value =
                        state.value.copy(
                            loading = false,
                            message = "Library request timed out. Retry.",
                            notice = ActionNotice.RETRY,
                        )
                }
            }
    }

    fun receive(reply: McReply) {
        if (reply.id != pending) return
        work?.cancel()
        pending = null
        mutable.value =
            when (reply) {
                is McReply.Failure -> {
                    state.value.copy(
                        loading = false,
                        notice = errorNotice(reply.error.code),
                        message =
                            if (reply.error.code ==
                                WireErrorCode.SELECTION_TOO_LARGE
                            ) {
                                "Choose at most 10,000 photos. Narrow your search."
                            } else {
                                reply.error.message
                            },
                    )
                }

                is McReply.Success -> {
                    (reply.result as? RemoteResult.LibraryQuery)?.value?.let {
                        currentCursor =
                            pendingCursor
                        ; previous.clear()
                        previous.addAll(pendingHistory)
                        state.value.copy(
                            hasPrevious = previous.isNotEmpty(),
                            loading = false,
                            items = it.items,
                            cursor = it.nextCursor,
                            selectionId = it.selectionId,
                            message = if (it.items.isEmpty()) "No matching media." else "",
                            notice = null,
                        )
                    }
                        ?: state.value.copy(loading = false)
                }
            }
    }
}
