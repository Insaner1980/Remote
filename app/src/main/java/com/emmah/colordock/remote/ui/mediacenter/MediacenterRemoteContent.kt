package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.R
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.ui.AdaptivePair

@Composable
internal fun ColumnScope.TargetRemote(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
) {
    val state = model.media
    val media = controllers.media
    val browserActive = state.snapshot?.activity?.mode == ActivityMode.BROWSER
    if (!browserActive) {
        Column(Modifier.testTag("transport-controls")) {
            if (state.snapshot?.activity?.mode == ActivityMode.PHOTOS) {
                NowPlayingCard {
                    Text(
                        state.snapshot.activity.title ?: "Photos",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    PhotoControls(state, model.library.selectionId, { media.command(it) }, media::slideshow)
                }
            } else {
                NowPlayingControls(state, { media.command(it) })
            }
        }
    }
    StreamingServices(
        model.streaming,
        !state.busy && state.preview == null && model.sound.preview == null,
        { media.openBrowser(model.streaming, it) },
    )
    val browser = controllers.browser
    if (browserActive && !state.showingMenus && browser != null) {
        Spacer(Modifier.weight(1f))
        val enabled = state.browserInputVisible() && model.sound.preview == null
        BrowserControls(model.streaming, browser, enabled = enabled)
        RemoteModeActions(model, controllers, includeBack = false)
    } else {
        RendererControls(state.snapshot, controllers.renderer, bottom = {
            RemoteModeActions(model, controllers, includeBack = true)
        })
    }
}

@Composable
private fun RemoteModeActions(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
    includeBack: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = model.media
    val media = controllers.media
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val menu: @Composable (Modifier) -> Unit = { keyModifier ->
            MediaKey("Menu", R.drawable.ic_menu, state.snapshot != null && !state.busy, {
                controllers.browser?.endGesture()
                media.command(RemoteCommand.ActivityShowMenu(Empty))
            }, keyModifier)
        }
        if (includeBack) {
            AdaptivePair(first = { keyModifier ->
                MediaKey(
                    "Back",
                    R.drawable.ic_undo,
                    state.snapshot?.ui?.available == true,
                    { controllers.renderer.back() },
                    keyModifier,
                )
            }, second = menu)
        } else {
            menu(Modifier.fillMaxWidth())
        }
        if (state.snapshot?.activity?.sessionId != null) {
            MediaKey(
                "Playback",
                R.drawable.ic_play_arrow,
                !state.busy,
                { media.command(RemoteCommand.ActivityShowPlayback(Empty)) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}
