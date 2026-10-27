package com.emmah.colordock.remote.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emmah.colordock.remote.ColorDockApplication
import com.emmah.colordock.remote.companion.CompanionClient
import com.emmah.colordock.remote.companion.DockCodec
import com.emmah.colordock.remote.companion.DockConnectionState
import com.emmah.colordock.remote.companion.DockMethod
import com.emmah.colordock.remote.companion.DockPatch
import com.emmah.colordock.remote.companion.DockResult
import com.emmah.colordock.remote.companion.PendingUploadStore
import com.emmah.colordock.remote.companion.UploadController
import com.emmah.colordock.remote.companion.UploadProgress
import com.emmah.colordock.remote.companion.WallpaperMetadata
import com.emmah.colordock.remote.images.ImagePreparer
import com.emmah.colordock.remote.images.PreparedImage
import com.emmah.colordock.remote.images.ThumbnailCache
import com.emmah.colordock.remote.tv.TvState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

private const val GALLERY_PAGE_SIZE = 24
private const val MAX_SELECTED_IMAGES = 10

data class WallpaperUiState(
    val source: String = "personal",
    val items: List<WallpaperMetadata> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val detail: WallpaperMetadata? = null,
    val prepared: List<PreparedImage> = emptyList(),
    val preparing: Boolean = false,
    val upload: UploadProgress? = null,
    val outcome: String? = null,
)

private const val PICTURE_LIST_ERROR = "The picture list could not be read."

// Public methods correspond to the existing gallery and upload screen actions.
@Suppress("TooManyFunctions")
class WallpaperViewModel internal constructor(
    application: Application,
    val dock: CompanionClient,
    private val tvState: StateFlow<TvState>,
    private val tvIdentity: () -> String?,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        (application as ColorDockApplication).container.dock,
        application.container.tv.state,
        { application.container.tv.identity },
    )

    private val mutableUi = MutableStateFlow(WallpaperUiState())
    val ui = mutableUi.asStateFlow()
    private val mutableThumbnails = MutableStateFlow<Map<String, File>>(emptyMap())
    val thumbnails = mutableThumbnails.asStateFlow()
    private val cache = ThumbnailCache(application, dock)
    private val preparing = ImagePreparer(application)
    private val pending = PendingUploadStore(application)
    private val uploading = UploadController(dock, pending)
    private val requested = mutableMapOf<String, String?>()
    private val thumbnailJobs = mutableMapOf<String, Job>()
    private var tvGeneration = 0L
    private val selectedUris = mutableListOf<Uri>()
    private var listing: Job? = null
    private var transfer: Job? = null
    private var recovery: Job? = null
    private var galleryRevision: Long? = null
    private var snapshotRevision: Long? = null
    private val galleryCursors = mutableSetOf<String>()
    private var batchToken = 0

    init {

        viewModelScope.launch {

            tvState.collect {

                if (it.connection == com.emmah.colordock.remote.tv.TvConnectionState.Disconnected) {

                    tvGeneration++
                    if (ui.value.upload != null) transfer?.cancel()
                    listing?.cancel()
                    thumbnailJobs.values.toList().forEach {
                        it.cancel()
                    }
                    thumbnailJobs.clear()
                    galleryRevision = null
                    snapshotRevision = null
                    galleryCursors.clear()
                    requested.clear()
                    mutableThumbnails.value = emptyMap()
                    mutableUi.value =
                        ui.value.copy(
                            items = emptyList(),
                            cursor = null,
                            detail = null,
                            loading = false,
                            saving = false,
                            upload = null,
                        )
                }
            }
        }
        viewModelScope.launch {

            dock.state.collect { state ->
                val snapshot = state.snapshot
                if (state.connection == DockConnectionState.Ready && snapshot != null &&
                    snapshot.revision != snapshotRevision
                ) {

                    snapshotRevision = snapshot.revision
                    load(false)
                }
            }
        }
    }

    fun join() {
        recovery?.cancel()
        mutableUi.value = ui.value.copy(error = null)
        val origin = tvGeneration
        val identity = tvIdentity()
        val token = batchToken
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val owner = coroutineContext.job

                fun current() =
                    owner.isActive && recovery === owner && origin == tvGeneration &&
                        identity == tvIdentity() && token == batchToken &&
                        ui.value.upload == null
                recoverPendingUpload(::current)
            }
        recovery = job
        job.start()
    }

    private suspend fun recoverPendingUpload(current: () -> Boolean) {
        try {
            if (dock.join() !is DockResult.Success || !current()) return
            val (id, name) = pending.read() ?: return
            if (current()) readUploadStatus(id, name, current)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            if (current()) {
                mutableUi.value =
                    ui.value.copy(
                        error = "The previous upload could not be checked. Check storage and try again.",
                    )
            }
        }
    }

    private suspend fun readUploadStatus(
        id: String,
        name: String,
        current: () -> Boolean,
    ) {
        val session = dock.sessionGeneration
        val status = dock.call(DockMethod.UploadStatus, JSONObject().put("uploadId", id), session)
        if (status is DockResult.Success && current() && session == dock.sessionGeneration) {
            val message = recoveryMessage(status.value, name) ?: return
            pending.clear(id)
            if (current()) mutableUi.value = ui.value.copy(outcome = message)
        }
    }

    private fun recoveryMessage(
        status: JSONObject,
        name: String,
    ): String? =
        when (status.opt("status")) {
            "committed" -> {
                if ((status.opt("wallpaperId") as? String).isNullOrBlank()) {
                    mutableUi.value = ui.value.copy(error = "The previous upload status could not be read. Try again.")
                    null
                } else {
                    "$name was saved on the TV."
                }
            }

            "receiving" -> {
                "The previous upload did not finish. Select the picture again."
            }

            else -> {
                mutableUi.value =
                    ui.value.copy(
                        error = "The previous upload status is not known. Reconnect and try again.",
                    )
                null
            }
        }

    fun source(value: String) {
        if (value !=
            ui.value.source
        ) {
            mutableUi.value = ui.value.copy(source = value, items = emptyList(), cursor = null)
            load(false)
        }
    }

    fun load(more: Boolean) {
        listing?.cancel()
        val source = ui.value.source
        val cursor = if (more) ui.value.cursor else null
        if (!more) galleryCursors.clear()
        listing = viewModelScope.launch { loadGallery(source, cursor, more) }
    }

    private suspend fun loadGallery(
        source: String,
        cursor: String?,
        more: Boolean,
    ) {
        mutableUi.value = ui.value.copy(loading = true, error = null)
        var pageCursor = cursor
        val collected = mutableListOf<WallpaperMetadata>()
        val visited = galleryCursors.toMutableSet()
        do {
            val params =
                JSONObject()
                    .put("source", source)
                    .put("cursor", pageCursor ?: JSONObject.NULL)
                    .put("limit", GALLERY_PAGE_SIZE)
            val reply = dock.call(DockMethod.Gallery, params)
            if (reply is DockResult.Failure) {
                mutableUi.value = ui.value.copy(loading = false, error = reply.message)
                return
            }
            val page = readGalleryPage((reply as DockResult.Success).value, more || collected.isNotEmpty()) ?: return
            collected.addAll(page.items)
            pageCursor?.let(visited::add)
            pageCursor = page.nextCursor
            if (pageCursor != null && pageCursor in visited) {
                mutableUi.value = ui.value.copy(loading = false, error = PICTURE_LIST_ERROR)
                break
            }
            if (source != "builtin" || pageCursor == null) publishGallery(collected, pageCursor, visited, more)
            // Read built-in metadata for collection filters; thumbnails stay lazy.
        } while (source == "builtin" && pageCursor != null)
    }

    private fun readGalleryPage(
        json: JSONObject,
        continuing: Boolean,
    ): GalleryPage? =
        try {
            val revision = json.getLong("galleryRevision")
            if (continuing && revision != galleryRevision) {
                load(false)
                null
            } else {
                galleryRevision = revision
                val array = json.getJSONArray("items")
                val items = (0 until array.length()).map { DockCodec.image(array.getJSONObject(it)) }
                val cursor = json.optString("nextCursor").takeUnless { it.isEmpty() || it == "null" }
                GalleryPage(items, cursor)
            }
        } catch (_: org.json.JSONException) {
            mutableUi.value = ui.value.copy(loading = false, error = PICTURE_LIST_ERROR)
            null
        } catch (_: IllegalArgumentException) {
            mutableUi.value = ui.value.copy(loading = false, error = PICTURE_LIST_ERROR)
            null
        }

    private fun publishGallery(
        collected: List<WallpaperMetadata>,
        cursor: String?,
        visited: Set<String>,
        more: Boolean,
    ) {
        galleryCursors.clear()
        galleryCursors.addAll(visited)
        mutableUi.value =
            ui.value.copy(
                items = (if (more) ui.value.items + collected else collected.toList()).distinctBy { it.id },
                cursor = cursor,
                loading = false,
            )
    }

    fun thumbnail(item: WallpaperMetadata) {
        val identity = tvIdentity() ?: return
        val origin = tvGeneration
        val available = thumbnailJobs[item.id]?.isActive == true || thumbnails.value[item.id]?.isFile == true
        if (requested.containsKey(item.id) && requested[item.id] == item.thumbnailHash && available) return
        thumbnailJobs.remove(item.id)?.cancel()
        requested[item.id] = item.thumbnailHash
        mutableThumbnails.value = thumbnails.value - item.id
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                downloadThumbnail(item, identity, origin)
            }
        thumbnailJobs[item.id] = job
        job.start()
    }

    // Decode and storage failures leave the tile retryable; cancellation still propagates.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun downloadThumbnail(
        item: WallpaperMetadata,
        identity: String,
        origin: Long,
    ) {
        val owner = coroutineContext.job
        try {
            val file = cache.get(identity, item)
            val ownsRequest = origin == tvGeneration && thumbnailJobs[item.id] === owner
            if (ownsRequest && tvIdentity() == identity && requested[item.id] == item.thumbnailHash) {
                mutableThumbnails.value = thumbnails.value + (item.id to file)
            }
        } catch (cancelled: CancellationException) {
            if (origin == tvGeneration && thumbnailJobs[item.id] === owner) requested.remove(item.id)
            throw cancelled
        } catch (_: Exception) {
            if (origin == tvGeneration && thumbnailJobs[item.id] === owner) requested.remove(item.id)
        } finally {
            if (thumbnailJobs[item.id] === owner) thumbnailJobs.remove(item.id)
        }
    }

    fun detail(item: WallpaperMetadata?) {
        mutableUi.value = ui.value.copy(detail = item, error = null)
    }

    fun clearError() {
        mutableUi.value = ui.value.copy(error = null, outcome = null)
    }

    fun patch(patch: DockPatch) {
        val revision =
            dock.state.value.snapshot
                ?.revision ?: return
        mutate {
            dock.patch(revision, patch)
        }
    }

    fun imageAction(
        method: DockMethod,
        item: WallpaperMetadata,
        fit: String? = null,
    ) {
        val revision =
            dock.state.value.snapshot
                ?.revision ?: return
        mutate(action = {
            val params = JSONObject().put("expectedRevision", revision).put("wallpaperId", item.id)
            fit?.let {
                params.put("fit", it)
            }
            dock.call(method, params)
        }, onSuccess = {
            if (ui.value.detail?.id == item.id) {
                if (method == DockMethod.Delete) detail(null)
                if (method == DockMethod.Fit &&
                    fit != null
                ) {
                    mutableUi.value = ui.value.copy(detail = ui.value.detail?.copy(fit = fit))
                }
            }
        })
    }

    fun next() {
        val revision =
            dock.state.value.snapshot
                ?.revision ?: return
        mutate {
            dock.call(DockMethod.Next, JSONObject().put("expectedRevision", revision))
        }
    }

    private fun mutate(
        onSuccess: () -> Unit = {
        },
        action: suspend () -> DockResult<*>,
    ) {
        if (ui.value.saving) return
        val origin = tvGeneration
        val identity = tvIdentity()
        viewModelScope.launch {
            mutableUi.value = ui.value.copy(saving = true, error = null)
            try {
                val result = action()
                if (origin != tvGeneration || tvIdentity() != identity) return@launch
                when (result) {
                    is DockResult.Failure -> {
                        mutableUi.value = ui.value.copy(error = result.message)
                    }

                    is DockResult.Success -> {
                        onSuccess()
                        dock.snapshot()
                    }
                }
            } finally {
                if (origin == tvGeneration &&
                    tvIdentity() == identity
                ) {
                    mutableUi.value = ui.value.copy(saving = false)
                }
            }
        }
    }

    fun select(uris: List<Uri>) {
        if (uris.isEmpty()) return
        abandon()
        val token = ++batchToken
        selectedUris.addAll(uris.take(MAX_SELECTED_IMAGES))
        mutableUi.value =
            ui.value.copy(preparing = true, error = null, outcome = null)
        transfer =
            viewModelScope.launch {
                val ready = mutableListOf<PreparedImage>()
                val errors = mutableListOf<String>()
                try {
                    for ((index, uri)in selectedUris.toList().withIndex()) {
                        prepareSelection(uri, index, ready, errors)
                        if (token == batchToken) mutableUi.value = ui.value.copy(prepared = ready.toList())
                    }
                    if (token ==
                        batchToken
                    ) {
                        mutableUi.value =
                            ui.value.copy(
                                preparing = false,
                                error =
                                    errors
                                        .takeIf {
                                            it.isNotEmpty()
                                        }?.joinToString("\n"),
                            )
                    }
                } catch (
                    cancelled: CancellationException,
                ) {
                    ready.forEach {
                        it.delete()
                    }
                    throw cancelled
                } finally {
                    if (token == batchToken) mutableUi.value = ui.value.copy(preparing = false)
                }
            }
    }

    private suspend fun prepareSelection(
        uri: Uri,
        index: Int,
        ready: MutableList<PreparedImage>,
        errors: MutableList<String>,
    ) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // The current picker grant remains usable.
        }
        try {
            ready.add(preparing.prepare(uri, "Picture ${index + 1}"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: java.io.IOException) {
            errors.add("Picture ${index + 1}: ${error.message}")
        }
    }

    fun preparedFit(
        id: String,
        fit: String,
    ) {
        mutableUi.value =
            ui.value.copy(
                prepared =
                    ui.value.prepared.map {
                        if (it.uploadId == id) it.copy(fit = fit) else it
                    },
            )
    }

    fun send() {
        val busy = ui.value.preparing || transfer?.isCompleted == false
        if (ui.value.prepared.isEmpty() || busy ||
            ui.value.upload?.stage in listOf("Preparing", "Sending", "Committing")
        ) {
            return
        }
        val token = batchToken
        val batch = ui.value.prepared.toList()
        val origin = tvGeneration
        val identity = tvIdentity()
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val owner = coroutineContext.job

                fun current(): Boolean {
                    val sameBatch = token == batchToken && transfer === owner
                    return sameBatch && origin == tvGeneration && identity == tvIdentity()
                }
                sendBatch(batch, ::current)
            }
        transfer = job
        job.start()
    }

    private suspend fun sendBatch(
        batch: List<PreparedImage>,
        current: () -> Boolean,
    ) {
        val failed = mutableListOf<PreparedImage>()
        val complete = mutableListOf<String>()
        try {
            mutableUi.value = ui.value.copy(upload = UploadProgress("Preparing"))
            recovery?.cancelAndJoin()
            for (image in batch) {
                uploading.upload(image).collect { progress ->
                    if (!current()) throw CancellationException()
                    recordUploadProgress(image, progress, complete, failed)
                }
            }
            if (!current()) return
            mutableUi.value = ui.value.copy(prepared = failed, upload = null, outcome = uploadOutcome(complete, failed))
            if (failed.isEmpty()) releaseGrants()
        } catch (cancelled: CancellationException) {
            if (current()) {
                mutableUi.value =
                    ui.value.copy(
                        upload = null,
                        outcome = "Upload cancelled. Already saved pictures remain on the TV.",
                    )
            }
            throw cancelled
        }
    }

    private fun recordUploadProgress(
        image: PreparedImage,
        progress: UploadProgress,
        complete: MutableList<String>,
        failed: MutableList<PreparedImage>,
    ) {
        if (progress.stage == "Complete") {
            complete.add(image.name)
            mutableUi.value = ui.value.copy(prepared = ui.value.prepared.filterNot { it.uploadId == image.uploadId })
            image.delete()
        }
        mutableUi.value = ui.value.copy(upload = progress, outcome = "${complete.size} sent: ${image.name}")
        if (progress.stage == "Failed" && ui.value.prepared.any { it.uploadId == image.uploadId }) failed.add(image)
    }

    fun cancelUpload() {
        transfer?.cancel()
    }

    fun background() {
        if (ui.value.upload != null) cancelUpload()
    }

    fun abandon() {
        batchToken++
        transfer?.cancel()
        ui.value.prepared.forEach {
            it.delete()
        }
        releaseGrants()
        mutableUi.value =
            ui.value.copy(prepared = emptyList(), upload = null, preparing = false)
    }

    private fun releaseGrants() {
        for (uri in selectedUris) {
            try {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Only grants owned by this batch are released.
            }
        }
        selectedUris.clear()
    }

    override fun onCleared() {
        abandon()
    }
}

private fun uploadOutcome(
    complete: List<String>,
    failed: List<PreparedImage>,
) = buildString {
    append("Sent: ")
    append(complete.ifEmpty { listOf("None") }.joinToString())
    if (failed.isNotEmpty()) {
        append("\nFailed: ")
        append(failed.joinToString { it.name })
    }
}

private data class GalleryPage(
    val items: List<WallpaperMetadata>,
    val nextCursor: String?,
)
