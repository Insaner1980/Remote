package com.emmah.colordock.remote.tv

import org.junit.Assert.*
import org.junit.Test

class PointerInputClientTest {
    @Test fun directionalKeyUsesTheLgFrameAndTerminatingBlankLine() {
        assertEquals("type:button\nname:UP\n\n", PointerInputClient.frame(TvCommand.Up))
        assertEquals("type:button\nname:BACK\n\n", PointerInputClient.frame(TvCommand.Back))
        assertEquals("type:button\nname:HOME\n\n", PointerInputClient.frame(TvCommand.Home))
        assertEquals("type:button\nname:GUIDE\n\n", PointerInputClient.frame(TvCommand.Guide))
    }

    @Test fun okUsesTheFocusedEnterKeyAndVolumeCannotEnterThePointerChannel() {
        assertEquals("type:button\nname:ENTER\n\n", PointerInputClient.frame(TvCommand.Ok))
        assertNull(PointerInputClient.frame(TvCommand.VolumeUp))
        assertNull(PointerInputClient.frame(TvCommand.PowerOff))
    }
}
