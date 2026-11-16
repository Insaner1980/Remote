package com.emmah.colordock.remote.ui.mediacenter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.MediacenterRepository
import com.emmah.colordock.remote.mediacenter.protocol.ActivityMode
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationsCancelParams
import com.emmah.colordock.remote.mediacenter.protocol.DrivesMountParams
import com.emmah.colordock.remote.mediacenter.protocol.DrivesPrepareEjectParams
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.FoldersPrepareAddParams
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.MediaItem
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OperationsCancelParams
import com.emmah.colordock.remote.mediacenter.protocol.PlaybackStart
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.SourcesCancelScanParams
import com.emmah.colordock.remote.mediacenter.protocol.SourcesPrepareRemoveParams
import com.emmah.colordock.remote.mediacenter.protocol.SourcesScanParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

internal data class MediaTargetControllers(
    val media: MediaSessionController,
    val library: LibraryViewModel,
    val renderer: RendererController,
    val audio: AudioSettingsController,
    val folders: FolderController? = null,
    val sources: SourcesController? = null,
    val drives: DrivesController? = null,
    val browser: BrowserInputController? = null,
) {
    /** A retired input lease cannot block explicit MC preview cancellation. */
    fun cancelPreviews(repository: MediacenterRepository) {
        val media = this.media
        val audio = this.audio
        val previews =
            listOfNotNull(
                media.state.value.preview
                    .takeUnless { media.state.value.busy },
                audio.state.value.preview
                    .takeUnless { audio.state.value.busy },
            )
        previews.forEach { preview ->
            repository.send(
                McRequest(
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                    RemoteCommand.ConfirmationsCancel(ConfirmationsCancelParams(preview.confirmationId)),
                    preview.expected,
                ),
            )
        }
    }
}

internal data class MediaTargetState(
    val media: MediaSessionState,
    val library: LibraryState,
    val sound: AudioSettingsState,
    val folders: FolderState = FolderState(),
    val sources: SourcesState = SourcesState(),
    val drives: DrivesState = DrivesState(),
    val streaming: StreamingState = StreamingState(),
    val feedback: MediaFeedbackState = MediaFeedbackState(),
)

private class LibraryNavigation {
    var playlists by mutableStateOf(false)
    var append by mutableStateOf<MediaItem?>(null)
}

/** Navigation survives input-context retirement; confirmations, QR and input controllers do not. */
class MediacenterNavigation {
    internal var destination: MediaDestination = MediaDestination.Remote
}

/**
 * One target page under the existing device selector. Explicit hoisted slots preserve existing callers
 * and one canonical settings/navigation owner.
 */
@Suppress("LongParameterList")
@Composable
fun MediacenterTargetPage(
    repository: MediacenterRepository,
    modifier: Modifier = Modifier,
    settings: @Composable (SettingsSection) -> Unit = {},
    header: @Composable () -> Unit = {},
    send: (McRequest) -> Boolean = repository::send,
    navigation: MediacenterNavigation? = null,
) {
    val scope = rememberCoroutineScope()
    val media =
        remember(repository, send) {
            MediaSessionController(
                scope,
                send,
            ).apply {
                destination(navigation?.destination ?: MediaDestination.Remote)
            }
        }
    val library = remember(repository, send) { LibraryViewModel(scope, send) }
    val renderer = remember(repository, send) { RendererController(scope, send) }
    val audio = remember(repository, send) { AudioSettingsController(send) }
    val folders = remember(repository, send) { FolderController(send) }
    val sources = remember(repository, send) { SourcesController(send) }
    val drives = remember(repository, send) { DrivesController(send) }
    val (browser, streaming) = rememberBrowserControllers(repository, media, audio)
    val streamState by streaming.state.collectAsState()
    val driveState by drives.state.collectAsState()
    val recoveredDrives by drives.recovery.state.collectAsState()
    val folderState by folders.state.collectAsState()
    val sourceState by sources.state.collectAsState()
    val recoveredScans by sources.recovered.state.collectAsState()
    val state by media.state.collectAsState()
    SideEffect { navigation?.destination = state.destination }
    ObserveRoutineSettings(media)
    val libraryState by library.state.collectAsState()
    val sound by audio.state.collectAsState()
    val uncertain by repository.inputOutcomeUncertain.collectAsState()
    val recoveryAvailable by repository.recoveryAvailable.collectAsState()
    val pending by repository.pending.collectAsState()
    val controllers = MediaTargetControllers(media, library, renderer, audio, folders, sources, drives, browser)
    ObserveMediaTarget(repository, controllers)
    ObserveStreaming(repository, media, browser, streaming)
    ObserveSourceRecovery(repository, sources)
    ObserveDriveRecovery(repository, drives)
    val thumbnail = rememberMediacenterThumbnails(repository, state.snapshot)
    MediacenterTargetContent(
        MediaTargetState(
            state,
            libraryState,
            sound,
            folderState,
            sourceState.copy(recoveredScans = recoveredScans),
            driveState.copy(recovered = recoveredDrives),
            streamState,
            MediaFeedbackState(pending, uncertain, recoveryAvailable),
        ),
        controllers,
        thumbnail,
        modifier,
        settings = settings,
        header = header,
    )
}

// Keep both hoisted UI slots explicit at the target boundary, as on MediacenterTargetPage.
@Suppress("LongParameterList")
@Composable
internal fun MediacenterTargetContent(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
    thumbnail: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
    settings: @Composable (SettingsSection) -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    val state = model.media
    val libraryState = model.library
    val media = controllers.media
    val renderer = controllers.renderer
    val contentScroll = rememberScrollState()
    val settingsNavigation = remember { SettingsNavigation() }
    LaunchedEffect(state.destination, libraryState.items.firstOrNull()?.id, settingsNavigation.section) {
        contentScroll.scrollTo(0)
        if (state.destination != MediaDestination.Settings) settingsNavigation.section = null
    }
    val navigation = remember { LibraryNavigation() }
    val backToSettings = {
        media.dismissPairing()
        settingsNavigation.section = null
    }
    BackHandler(state.destination == MediaDestination.Settings && settingsNavigation.section != null) {
        backToSettings()
    }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediacenterPageHeading(state.destination, settingsNavigation.section, backToSettings) {
                MediaRecoveryStatus(
                    model.feedback.pending,
                    model.feedback.uncertain,
                    model.feedback.recoveryAvailable,
                    notice = state.notice,
                )
            }
            header()
            InlineMediaStatus(model, settingsNavigation.section)
        }
        Column(
            Modifier.weight(1f).verticalScroll(contentScroll).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.destination) {
                MediaDestination.Remote -> TargetRemote(model, controllers)
                MediaDestination.Library -> TargetLibrary(model, controllers, thumbnail, navigation)
                MediaDestination.Settings -> TargetSettings(model, controllers, settingsNavigation, settings)
            }
        }
        MediacenterBottomNavigation(state.destination, { destination ->
            renderer.stop()
            controllers.browser?.endGesture()
            media.destination(destination)
        })
    }
    TargetDialogs(model, controllers)
}

@Composable
private fun ObserveMediaTarget(
    repository: MediacenterRepository,
    controllers: MediaTargetControllers,
) {
    val media = controllers.media
    val library = controllers.library
    val renderer = controllers.renderer
    val audio = controllers.audio
    LaunchedEffect(repository, media) {
        combine(repository.snapshot, repository.state) { snapshot, connection ->
            snapshot.takeIf {
                connection ==
                    MediacenterConnectionState.Connected
            }
        }.collect {
            media.observe(it)
            library.observe(it?.serverEpoch, it?.connection?.serverDeviceId)
            renderer.observe(it)
            audio.observe(it?.serverEpoch, it?.outputs, it?.operations?.items.orEmpty())
            controllers.folders?.observe(it?.serverEpoch, it?.connection?.serverDeviceId)
            controllers.sources?.observe(it?.serverEpoch, it?.connection?.serverDeviceId, it?.sourcesSummary?.revision)
            controllers.drives?.observe(it?.serverEpoch, it?.connection?.serverDeviceId, it?.sourcesSummary?.revision)
        }
    }
    LaunchedEffect(repository, media) {
        repository.replies.collect {
            media.receive(it)
            library.receive(it)
            renderer.receive(it)
            audio.receive(it)
            controllers.folders?.receive(it)
            controllers.sources?.receive(it)
            controllers.drives?.receive(it)
        }
    }
    LaunchedEffect(repository, media) { repository.pending.collect { it.forEach(media::recovered) } }
    LaunchedEffect(media, audio) {
        while (true) {
            delay(MEDIA_CONFIRMATION_REFRESH_MS)
            media.confirmationStatus.refresh()
            audio.confirmationStatus.refresh()
        }
    }
    DisposableEffect(media, library, renderer) {
        onDispose {
            controllers.cancelPreviews(repository)
            media.observe(null)
            audio.observe(null, null)
            library.observe(null)
            controllers.folders?.observe(null, null)
            controllers.sources?.observe(null, null, null)
            controllers.drives?.observe(null, null, null)
            renderer.stop()
        }
    }
}

@Composable
private fun ColumnScope.TargetRemote(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
) {
    val state = model.media
    val libraryState = model.library
    val media = controllers.media
    val renderer = controllers.renderer

    val browserActive = state.snapshot?.activity?.mode == ActivityMode.BROWSER
    if (!browserActive) {
        Column(Modifier.testTag("transport-controls")) {
            if (state.snapshot?.activity?.mode == ActivityMode.PHOTOS) {
                Text(state.snapshot.activity.title ?: "Photos")
                PhotoControls(state, libraryState.selectionId, { media.command(it) }, media::slideshow)
                TextButton(
                    onClick = { media.queue(true) },
                ) { Text("Queue") }
            } else {
                NowPlayingControls(state, { media.command(it) }, { media.queue(true) })
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                controllers.browser?.endGesture()
                media.command(RemoteCommand.ActivityShowMenu(Empty))
            },
            enabled =
                state.snapshot != null && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Menu") }
        if (state.snapshot?.activity?.sessionId != null) {
            OutlinedButton(
                onClick = { media.command(RemoteCommand.ActivityShowPlayback(Empty)) },
                enabled =
                    !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Playback") }
        }
    }
    StreamingServices(
        model.streaming,
        !state.busy && state.preview == null && model.sound.preview == null,
        { media.openBrowser(model.streaming, it) },
    )
    val browser = controllers.browser
    if (browserActive && !state.showingMenus && browser != null) {
        val enabled = state.browserInputVisible() && model.sound.preview == null
        BrowserControls(model.streaming, browser, enabled = enabled)
    } else {
        RendererControls(state.snapshot, renderer)
    }
}

@Composable
private fun TargetLibrary(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
    thumbnail: suspend (String) -> ByteArray?,
    navigation: LibraryNavigation,
) {
    val state = model.media
    val libraryState = model.library
    val media = controllers.media
    val library = controllers.library

    if (navigation.playlists) {
        PlaylistScreen(
            state,
            navigation.append,
            PlaylistActions(
                { media.command(it) },
                media::playlist,
                media::playlists,
                media::searchPlaylists,
                media::playPlaylist,
                {
                    media.closePlaylist()
                    navigation.playlists = false
                    navigation.append = null
                },
                { navigation.append = null },
            ),
        )
    } else {
        LibraryScreen(
            libraryState,
            LibraryActions(
                library::search,
                { media.choose(it, libraryState.selectionId) },
                { media.enqueue(listOf(it.id)) },
                {
                    navigation.append = it
                    navigation.playlists = true
                    media.closePlaylist()
                    media.playlists()
                },
                {
                    navigation.playlists = true
                    media.closePlaylist()
                    media.playlists()
                },
                { media.destination(MediaDestination.Remote) },
                library::next,
                library::previous,
                library::refresh,
            ),
            thumbnail,
        )
    }
}

@Composable
private fun TargetDialogs(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
) {
    val state = model.media
    val sound = model.sound
    val media = controllers.media
    val audio = controllers.audio
    if (state.queueOpen) {
        QueueSheet(
            state,
            QueueActions(
                {
                    media.command(it)
                },
                { media.choose(it.item, entry = it.entryId) },
                media::refreshQueue,
                { media.queue(false) },
            ),
        )
    }
    state.resume?.let { item ->
        AlertDialog(onDismissRequest = media::dismissResume, title = {
            Text(item.name)
        }, text = {
            Text("Resume from ${item.resumePositionSeconds?.toInt()} seconds?")
        }, confirmButton = {
            TextButton(onClick = {
                media.resume(PlaybackStart.RESUME)
            }) {
                Text("Resume")
            }
        }, dismissButton = { TextButton(onClick = { media.resume(PlaybackStart.BEGINNING) }) { Text("Start over") } })
    }
    val preview = state.preview ?: sound.preview
    if (preview != null) {
        ConfirmationSheet(
            preview,
            state.busy || sound.busy,
            ConfirmationActions(
                { if (state.preview != null) media.confirm() else audio.confirm() },
                { if (state.preview != null) media.cancelConfirmation() else audio.cancel() },
            ),
        )
    }
}

@Composable
internal fun TargetSources(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
) {
    val sources = controllers.sources ?: return
    val folders = controllers.folders ?: return
    val media = controllers.media
    val operation =
        media.observedOperation?.takeIf {
            it.status in listOf(OperationStatus.ACCEPTED, OperationStatus.RUNNING) &&
                media.operationCommand.let { command ->
                    command is RemoteCommand.SourcesScan ||
                        command is RemoteCommand.FoldersConfirmAdd
                }
        }
    SourcesScreen(
        model.sources.copy(
            recoveredScans =
                model.sources.recoveredScans.filterNot {
                    it.operationId ==
                        operation?.operationId
                },
        ),
        model.folders,
        model.media.busy,
        SourceActions(
            sources::refresh,
            sources::next,
            { media.command(RemoteCommand.SourcesScan(SourcesScanParams(it))) },
            { media.command(RemoteCommand.SourcesPrepareRemove(SourcesPrepareRemoveParams(it))) },
            operation?.let { scan ->
                {
                    media.command(RemoteCommand.SourcesCancelScan(SourcesCancelScanParams(scan.operationId)))
                }
            },
            FolderActions(folders::browse, folders::next, folders::refresh, { folder, kinds ->
                media.command(RemoteCommand.FoldersPrepareAdd(FoldersPrepareAddParams(folder.nodeId, kinds)))
            }),
            { media.command(RemoteCommand.SourcesCancelScan(SourcesCancelScanParams(it))) },
        ),
    )
}

@Composable
private fun ObserveSourceRecovery(
    repository: MediacenterRepository,
    sources: SourcesController,
) {
    LaunchedEffect(repository, sources) {
        combine(repository.snapshot, repository.state, repository.pending) { snapshot, connection, pending ->
            snapshot.takeIf { connection == MediacenterConnectionState.Connected } to pending
        }.collect { (snapshot, pending) ->
            sources.recovered.observe(snapshot, pending, repository.pairedDeviceId)
        }
    }
    DisposableEffect(sources) {
        onDispose { sources.recovered.observe(null, emptyList(), null) }
    }
}

@Composable
internal fun TargetDrives(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
) {
    val drives = controllers.drives ?: return
    val media = controllers.media
    val operation =
        media.observedOperation?.takeIf {
            it.status in listOf(OperationStatus.ACCEPTED, OperationStatus.RUNNING) &&
                model.drives.recovered.none { recovered -> recovered.operation.operationId == it.operationId } &&
                (
                    media.operationCommand is RemoteCommand.DrivesMount ||
                        media.operationCommand is RemoteCommand.DrivesConfirmEject
                )
        }
    DrivesScreen(
        model.drives,
        model.media.busy || model.drives.recovered.any { it.running },
        DriveActions(
            drives::refresh,
            { media.command(RemoteCommand.DrivesMount(DrivesMountParams(it))) },
            { media.command(RemoteCommand.DrivesPrepareEject(DrivesPrepareEjectParams(it))) },
            operation?.let { current ->
                {
                    media.command(
                        RemoteCommand.OperationsCancel(OperationsCancelParams(operationId = current.operationId)),
                    )
                }
            },
            { media.command(RemoteCommand.OperationsCancel(OperationsCancelParams(operationId = it))) },
        ),
    )
}

@Composable
private fun ObserveDriveRecovery(
    repository: MediacenterRepository,
    drives: DrivesController,
) {
    LaunchedEffect(repository, drives) {
        combine(repository.snapshot, repository.state, repository.pending) { snapshot, connection, pending ->
            snapshot.takeIf { connection == MediacenterConnectionState.Connected } to pending
        }.collect { (snapshot, pending) ->
            drives.recovery.observe(snapshot, pending, repository.pairedDeviceId)
        }
    }
    DisposableEffect(drives) {
        onDispose { drives.recovery.observe(null, emptyList(), null) }
    }
}
