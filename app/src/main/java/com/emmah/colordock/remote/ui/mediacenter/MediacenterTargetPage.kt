package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.emmah.colordock.remote.mediacenter.protocol.DrivesMountParams
import com.emmah.colordock.remote.mediacenter.protocol.DrivesPrepareEjectParams
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.FoldersPrepareAddParams
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
)

internal data class MediaTargetState(
    val media: MediaSessionState,
    val library: LibraryState,
    val sound: AudioSettingsState,
    val folders: FolderState = FolderState(),
    val sources: SourcesState = SourcesState(),
    val drives: DrivesState = DrivesState(),
)

private class LibraryNavigation {
    var playlists by mutableStateOf(false)
    var append by mutableStateOf<MediaItem?>(null)
}

/** Task 16 places this single target page under the existing target selector. */
@Composable
fun MediacenterTargetPage(
    repository: MediacenterRepository,
    modifier: Modifier = Modifier,
    settings: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val media = remember(repository) { MediaSessionController(scope, repository::send) }
    val library = remember(repository) { LibraryViewModel(scope, repository::send) }
    val renderer = remember(repository) { RendererController(scope, repository::send) }
    val audio = remember(repository) { AudioSettingsController(repository::send) }
    val folders = remember(repository) { FolderController(repository::send) }
    val sources = remember(repository) { SourcesController(repository::send) }
    val drives = remember(repository) { DrivesController(repository::send) }
    val driveState by drives.state.collectAsState()
    val folderState by folders.state.collectAsState()
    val sourceState by sources.state.collectAsState()
    val recoveredScans by sources.recovered.state.collectAsState()
    val state by media.state.collectAsState()
    val libraryState by library.state.collectAsState()
    val sound by audio.state.collectAsState()
    val uncertain by repository.inputOutcomeUncertain.collectAsState()
    val recoveryAvailable by repository.recoveryAvailable.collectAsState()
    val pending by repository.pending.collectAsState()
    val controllers = MediaTargetControllers(media, library, renderer, audio, folders, sources, drives)
    ObserveMediaTarget(repository, controllers)
    ObserveSourceRecovery(repository, sources)
    val thumbnail: suspend (
        String,
    ) -> ByteArray? =
        remember(repository, state.snapshot?.connection?.serverDeviceId, state.snapshot?.serverEpoch) {
            val load: suspend (
                String,
            ) -> ByteArray? = { id -> repository.thumbnail(id) }
            load
        }
    Column(modifier.fillMaxSize()) {
        val unresolved = pending.count { !it.resolved }
        if (unresolved > 0) Text("Mediacenter is reconciling $unresolved action(s).")
        if (pending.any { it.resolved && it.status == OperationStatus.OUTCOME_UNKNOWN }) {
            Text("An earlier action has an uncertain outcome. Inspect playback before issuing it again.")
        }
        if (uncertain) Text("Input outcome is uncertain. Inspect the computer before sending again.")
        if (!recoveryAvailable) Text("Command recovery is unavailable. Reconnect after freeing phone storage.")
        MediacenterTargetContent(
            MediaTargetState(
                state,
                libraryState,
                sound,
                folderState,
                sourceState.copy(recoveredScans = recoveredScans),
                driveState,
            ),
            controllers,
            thumbnail,
            settings = settings,
        )
    }
}

@Composable
internal fun MediacenterTargetContent(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
    thumbnail: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
    settings: @Composable () -> Unit = {},
) {
    val (state, libraryState, sound) = model
    val media = controllers.media
    val renderer = controllers.renderer
    val audio = controllers.audio
    val contentScroll = rememberScrollState()
    LaunchedEffect(state.destination, libraryState.items.firstOrNull()?.id) { contentScroll.scrollTo(0) }
    val navigation = remember { LibraryNavigation() }
    Column(modifier.fillMaxSize()) {
        Row {
            MediaDestination.entries.forEach { destination ->
                TextButton(onClick = {
                    renderer.stop()
                    media.destination(destination)
                }) { Text(destination.name) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(contentScroll).padding(16.dp)) {
            if (state.message.isNotEmpty()) Text(state.message)
            if (state.snapshot == null) Text("Reconnect using Mediacenter Connection settings, then refresh.")
            when (state.destination) {
                MediaDestination.Remote -> {
                    TargetRemote(model, controllers)
                }

                MediaDestination.Library -> {
                    TargetLibrary(model, controllers, thumbnail, navigation)
                }

                MediaDestination.Settings -> {
                    SoundOptions(
                        sound,
                        SoundActions({
                            audio.retry(true)
                        }, {
                            audio.retry()
                        }, {
                            audio.choose(
                                it,
                            )
                        }, { audio.test(it) }, { audio.confirm() }, { audio.cancel() }),
                        showConfirmation = false,
                    )
                    TargetSources(model, controllers)
                    TargetDrives(model, controllers)
                    settings()
                }
            }
        }
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
    LaunchedEffect(repository) {
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
    LaunchedEffect(repository) {
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
    LaunchedEffect(repository) { repository.pending.collect { it.forEach(media::recovered) } }
    LaunchedEffect(media, audio) {
        while (true) {
            delay(MEDIA_CONFIRMATION_REFRESH_MS)
            media.confirmationStatus.refresh()
            audio.confirmationStatus.refresh()
        }
    }
    DisposableEffect(media, library, renderer) {
        onDispose {
            media.observe(null)
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
    Row {
        TextButton(
            onClick = { media.command(RemoteCommand.ActivityShowMenu(Empty)) },
            enabled =
                state.snapshot != null && !state.busy,
        ) { Text("Show menus on computer") }
        TextButton(
            onClick = { media.command(RemoteCommand.ActivityShowPlayback(Empty)) },
            enabled =
                state.snapshot != null && !state.busy,
        ) { Text("Show current playback") }
    }
    RendererControls(state.snapshot, renderer)
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
private fun TargetSources(
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
private fun TargetDrives(
    model: MediaTargetState,
    controllers: MediaTargetControllers,
) {
    val drives = controllers.drives ?: return
    val media = controllers.media
    val operation =
        media.observedOperation?.takeIf {
            it.status in listOf(OperationStatus.ACCEPTED, OperationStatus.RUNNING) &&
                (
                    media.operationCommand is RemoteCommand.DrivesMount ||
                        media.operationCommand is RemoteCommand.DrivesConfirmEject
                )
        }
    DrivesScreen(
        model.drives,
        model.media.busy,
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
        ),
    )
}
