package com.emmah.colordock.remote.mediacenter.protocol

import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.PlaylistsListParams
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteJson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSearchProtocolTest {
    @Test fun queryIsOptionalAndBoundedByUnicodeCharacters() {
        val plain = RemoteJson.encodeRequest(McRequest("plain", RemoteCommand.PlaylistsList(PlaylistsListParams())))
        assertFalse(plain.contains("query"))
        val query = "😀".repeat(200)
        val encoded =
            RemoteJson.encodeRequest(
                McRequest("search", RemoteCommand.PlaylistsList(PlaylistsListParams(query = query))),
            )
        assertTrue(encoded.contains("query"))
        assertTrue(
            runCatching {
                RemoteJson.encodeRequest(
                    McRequest(
                        "large",
                        RemoteCommand.PlaylistsList(
                            PlaylistsListParams(
                                query =
                                    query + "x",
                            ),
                        ),
                    ),
                )
            }.isFailure,
        )
    }
}
