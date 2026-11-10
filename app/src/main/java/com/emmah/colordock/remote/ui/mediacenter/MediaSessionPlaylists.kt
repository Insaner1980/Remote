package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsGetParams
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsListParams
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsPlayParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun MediaSessionController.playlists(cursor: String? = null) {
    submit(RemoteCommand.PlaylistsList(PlaylistsListParams(cursor, MEDIA_PAGE_SIZE, state.value.playlistQuery)))
}

internal fun MediaSessionController.searchPlaylists(value: String) {
    if (value.codePointCount(0, value.length) > MEDIA_QUERY_LIMIT) {
        message("Search accepts at most 200 characters.")
        return
    }
    playlistSearch?.cancel()
    requests.filterValues { it is RemoteCommand.PlaylistsList }.keys.toList().forEach { id ->
        requests.remove(id)
        deadlines.remove(id)?.cancel()
    }
    mutable.value = state.value.copy(playlistQuery = value, playlists = null)
    playlistSearch =
        scope.launch {
            delay(MEDIA_SEARCH_DEBOUNCE_MS)
            playlists()
        }
}

internal fun MediaSessionController.playPlaylist() {
    val current = state.value.playlist ?: return
    if (state.value.busy) return
    if (state.value.snapshot
            ?.activity
            ?.mode in
        listOf(ActivityMode.IDLE, ActivityMode.MUSIC)
    ) {
        command(RemoteCommand.PlaylistsPlay(PlaylistsPlayParams(current.playlist.id)))
    } else {
        playPlaylistAfterRead = true
        playlist(current.playlist.id)
        if (requests.values.any { it is RemoteCommand.PlaylistsGet }) {
            mutable.value = state.value.copy(busy = true)
        } else {
            playPlaylistAfterRead = false
        }
    }
}

internal fun MediaSessionController.playlist(
    id: String,
    cursor: String? = null,
) {
    submit(RemoteCommand.PlaylistsGet(PlaylistsGetParams(id, cursor, MEDIA_PAGE_SIZE)))
}

internal fun MediaSessionController.refreshPlaylist() {
    state.value.playlist
        ?.playlist
        ?.id
        ?.let { playlist(it) } ?: playlists()
}

internal fun MediaSessionController.closePlaylist() {
    if (playPlaylistAfterRead) mutable.value = state.value.copy(busy = false)
    playPlaylistAfterRead = false
    mutable.value = state.value.copy(playlist = null)
}
