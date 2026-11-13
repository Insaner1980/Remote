package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.emmah.colordock.remote.mediacenter.MediacenterRepository
import kotlinx.coroutines.delay

@Composable
internal fun rememberBrowserControllers(
    repository: MediacenterRepository,
    media: MediaSessionController,
    audio: AudioSettingsController,
): Pair<BrowserInputController, StreamingController> =
    remember(repository, media, audio) {
        val browser =
            BrowserInputController(
                media.scope,
                media.send,
                inputVisible = { target ->
                    val snapshot = repository.snapshot.value
                    val matches = snapshot?.serverEpoch == target.epoch && snapshot.activity.sessionId == target.session
                    matches && media.state.value.browserInputVisible() && audio.state.value.preview == null
                },
            )
        val streaming =
            StreamingController(media.send) { next ->
                browser.observe(next.target, next.browser?.capabilities, next.capabilities?.methods.orEmpty())
                media.observeBrowserApproval(next, repository.pairedDeviceId)
            }
        browser to streaming
    }

@Composable
internal fun ObserveStreaming(
    repository: MediacenterRepository,
    media: MediaSessionController,
    browser: BrowserInputController,
    streaming: StreamingController,
) {
    LaunchedEffect(repository, streaming) {
        repository.replies.collect {
            streaming.receive(it)
            browser.receive(it)
        }
    }
    LaunchedEffect(streaming, media) {
        media.state.collect { state ->
            if (state.destination == MediaDestination.Remote) {
                streaming.observe(state.snapshot)
                streaming.refresh()
            } else {
                streaming.observe(null)
            }
        }
    }
    LaunchedEffect(streaming) {
        while (true) {
            if (media.state.value.destination == MediaDestination.Remote) {
                streaming.refresh()
                media.observeBrowserApproval(streaming.state.value, repository.pairedDeviceId)
            }
            delay(STREAMING_REFRESH_MS)
        }
    }
    DisposableEffect(streaming) {
        onDispose {
            streaming.observe(null)
            browser.endGesture()
        }
    }
}
