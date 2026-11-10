package com.emmah.colordock.remote.ui.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.DirectoryNode
import com.emmah.colordock.remote.mediacenter.protocol.DirectoryPage
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderControllerTest {
    @Test fun connectionReadsOnlyAuthorizedRootsAndDoesNotReplayMutation() {
        val sent = mutableListOf<McRequest>()
        val controller = FolderController { sent.add(it) }
        controller.observe("epoch", "server")
        assertEquals(1, sent.size)
        assertTrue(sent.single().command is RemoteCommand.FoldersRoots)
        controller.observe("epoch", "server")
        assertEquals(1, sent.size)
        controller.observe(null, null)
        controller.observe("new-epoch", "server")
        assertEquals(2, sent.size)
        assertTrue(sent.last().command is RemoteCommand.FoldersRoots)
    }

    @Test fun oldOwnerReplyCannotRestoreHandlesAndPagesContainOnlyCurrentDirectories() {
        val sent = mutableListOf<McRequest>()
        val controller = FolderController { sent.add(it) }
        controller.observe("epoch", "server-a")
        val old = sent.last().id
        controller.observe("epoch", "server-b")
        controller.receive(
            McReply.Success(old, RemoteResult.FoldersRoots(DirectoryPage(listOf(DirectoryNode("old", "Old"))))),
        )
        assertTrue(
            controller.state.value.items
                .isEmpty(),
        )
        val root = DirectoryNode("opaque-root", "Music")
        controller.receive(McReply.Success(sent.last().id, RemoteResult.FoldersRoots(DirectoryPage(listOf(root)))))
        controller.browse(root)
        val command = sent.last().command as RemoteCommand.FoldersChildren
        assertEquals("opaque-root", command.params.nodeId)
        controller.receive(
            McReply.Success(
                sent.last().id,
                RemoteResult.FoldersChildren(DirectoryPage(listOf(DirectoryNode("album", "Album")), "next")),
            ),
        )
        controller.next()
        assertEquals("next", (sent.last().command as RemoteCommand.FoldersChildren).params.cursor)
        controller.observe(null, null)
        controller.receive(McReply.Success(sent.last().id, RemoteResult.FoldersChildren(DirectoryPage(listOf(root)))))
        assertTrue(
            controller.state.value.items
                .isEmpty(),
        )
    }
}
