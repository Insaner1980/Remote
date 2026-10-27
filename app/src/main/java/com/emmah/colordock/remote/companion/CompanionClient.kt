package com.emmah.colordock.remote.companion

import com.emmah.colordock.remote.tv.TvConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val MAX_MESSAGE_BYTES = 32768
private const val REQUEST_TIMEOUT_MILLIS = 5000L
private const val MIN_CHUNK_BYTES = 1024L

class CompanionClient(
    private val tv: DockTransport,
    private val scope: CoroutineScope,
) : DockControl {
    private val mutableState = MutableStateFlow(DockState())
    override val state = mutableState.asStateFlow()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val joining = Mutex()

    @Volatile private var sessionId: String? = null

    @Volatile private var generation = 0L
    private var transportOrigin: Any? = null
    internal val sessionGeneration get() = generation

    internal fun isCurrent(origin: Long) =
        synchronized(this) {
            origin == generation && sessionId != null && transportOrigin === tv.connectionToken
        }

    private var sequence = 0L
    private var published = 0L

    @Volatile var limits = DockLimits()
        private set

    init {

        scope.launch(start = CoroutineStart.UNDISPATCHED) {

            tv.dockMessages.collect { message ->
                if (message.opt(
                        "protocol",
                    ) != "color-dock" || runCatching {
                        DockCodec.integer(message.opt("version"))
                    }.getOrNull() != 1L ||
                    message.toString().toByteArray().size > MAX_MESSAGE_BYTES
                ) {

                    return@collect
                }
                if (message.has("event")) {

                    when (message.optString("event")) {

                        "state.changed" -> {

                            if (sessionId != null) {
                                scope.launch {
                                    snapshot()
                                }
                            }
                        }

                        "clock.changed" -> {

                            synchronized(this@CompanionClient) {

                                if (sessionId !=
                                    null
                                ) {

                                    (message.opt("active") as? Boolean)?.let { active ->
                                        state.value.snapshot?.let { snapshot ->
                                            published = ++sequence
                                            mutableState.value =
                                                state.value.copy(snapshot = snapshot.copy(clockActive = active))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {

                    pending.remove(message.optString("id"))?.complete(message)
                }
            }
        }
        scope.launch {
            tv.state.collect {
                if (it.connection != TvConnectionState.Ready) disconnect()
            }
        }
        scope.launch {
            tv.dockAvailable.collect {
                if (!it) disconnect()
            }
        }
    }

    // The transport and protocol boundary converts external failures to the existing connection state.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun join(): DockResult<DockSnapshot> {
        fun reset(
            origin: Long,
            updated: DockState = DockState(),
        ) {
            synchronized(this) {
                if (origin != generation) return
                disconnect()
                mutableState.value = updated
            }
        }
        return joining.withLock {
            if (tv.state.value.connection != TvConnectionState.Ready) return@withLock unavailable()
            if (sessionId != null) return@withLock snapshot()
            val origin = generation
            val transport = tv.connectionToken

            try {
                synchronized(this) {
                    check(origin == generation)
                    transportOrigin = transport
                    mutableState.value =
                        state.value.copy(connection = DockConnectionState.Connecting, message = null)
                }
                tv.joinDockChannel()
                check(origin == generation && tv.connectionToken === transport)
                val hello =
                    exchange("hello", JSONObject().put("clientName", "Remote").put("supportedVersion", 1), null, origin)
                val accepted = acceptedLimits(hello)
                val session =
                    hello.getString("sessionId").also {
                        UUID.fromString(it)
                    }
                synchronized(this) {
                    check(origin == generation && tv.connectionToken === transport)
                    sessionId = session
                    limits = accepted
                }
                val result = snapshot(origin)
                if (result is DockResult.Failure) reset(origin, DockState(message = result.message))
                result
            } catch (
                cancelled: CancellationException,
            ) {
                reset(origin)
                throw cancelled
            } catch (error: Exception) {
                reset(origin, failedJoinState(error))
                unavailable()
            }
        }
    }

    override suspend fun snapshot(): DockResult<DockSnapshot> = snapshot(generation)

    internal suspend fun snapshot(origin: Long): DockResult<DockSnapshot> {
        val order =
            synchronized(this) {
                if (origin !=
                    generation
                ) {
                    return unavailable()
                }
                ++sequence
            }
        return when (val result = call(DockMethod.State, JSONObject(), origin)) {
            is DockResult.Success -> publish(result.value, order, origin)
            is DockResult.Failure -> result
        }
    }

    override suspend fun patch(
        expectedRevision: Long,
        patch: DockPatch,
    ): DockResult<DockSnapshot> {
        val (origin, order) =
            synchronized(this) {
                generation to
                    ++sequence
            }
        return when (
            val result =
                call(
                    DockMethod.Patch,
                    JSONObject().put("expectedRevision", expectedRevision).put("patch", patch.json()),
                    origin,
                )
        ) {
            is DockResult.Success -> publish(result.value, order, origin)
            is DockResult.Failure -> result
        }
    }

    private fun publish(
        value: JSONObject,
        order: Long,
        origin: Long,
    ): DockResult<DockSnapshot> =
        try {
            val snapshot = DockCodec.snapshot(value)
            synchronized(this) {
                if (origin != generation || transportOrigin !== tv.connectionToken) return unavailable()
                if (order >= published) {
                    published = order
                    mutableState.value = DockState(DockConnectionState.Ready, snapshot)
                }
                DockResult.Success(requireNotNull(state.value.snapshot))
            }
        } catch (_: Exception) {
            DockResult.Failure("INVALID_REQUEST", "Color Dock returned an unreadable response.")
        }

    override suspend fun call(
        method: DockMethod,
        params: JSONObject,
    ): DockResult<JSONObject> = call(method, params, generation)

    // DockTransport implementations can fail with platform-specific exceptions; callers receive DockResult.
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun call(
        method: DockMethod,
        params: JSONObject,
        origin: Long,
    ): DockResult<JSONObject> {
        return try {
            val session =
                synchronized(this) {
                    if (origin == generation) sessionId else null
                } ?: return unavailable()
            DockResult.Success(exchange(method.wire, params, session, origin))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val code = (error as? DockException)?.code ?: "NOT_READY"
            synchronized(this) {
                if (origin != generation) return@synchronized
                when (code) {
                    "REVISION_CONFLICT" -> scope.launch { snapshot(origin) }
                    "SESSION_EXPIRED", "NOT_READY" -> disconnect()
                }
            }
            DockResult.Failure(code, message(code))
        }
    }

    private suspend fun exchange(
        method: String,
        params: JSONObject,
        session: String?,
        origin: Long,
    ): JSONObject {
        currentCoroutineContext().ensureActive()
        val id = UUID.randomUUID().toString()
        val reply = CompletableDeferred<JSONObject>()
        try {
            val request =
                JSONObject()
                    .put(
                        "protocol",
                        "color-dock",
                    ).put("version", 1)
                    .put("id", id)
                    .put("method", method)
                    .put("params", params)
            session?.let {
                request.put("sessionId", it)
            }
            require(request.toString().toByteArray().size <= MAX_MESSAGE_BYTES)
            val transport =
                synchronized(this) {
                    if (origin != generation ||
                        (session != null && sessionId != session)
                    ) {
                        throw DockException("NOT_READY")
                    }
                    pending[id] = reply
                    transportOrigin
                }
            tv.sendDock(request, transport)
            val value =
                withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
                    reply.await()
                } ?: throw DockException("TIMEOUT")
            requireCurrentTransport(origin == generation && transport === tv.connectionToken)
            return DockCodec.reply(value, id)
        } finally {
            pending.remove(id)
            reply.cancel()
        }
    }

    @Synchronized override fun disconnect() {
        generation++
        sequence = 0
        published = 0
        sessionId = null
        transportOrigin = null
        limits = DockLimits()
        pending.values.forEach {
            it.completeExceptionally(DockException("NOT_READY"))
        }
        pending.clear()
        mutableState.value =
            DockState()
    }
}

private fun unavailable() = DockResult.Failure("NOT_READY", "Open Color Dock to continue.")

private val failureMessages =
    mapOf(
        "TIMEOUT" to "Color Dock did not reply. Reconnect and check the saved state.",
        "REVISION_CONFLICT" to "Settings changed on the TV. Review your change and try again.",
        "EMPTY_SELECTION" to "Keep at least one picture selected.",
        "STORAGE_FULL" to "The TV has no space for this picture. Delete a personal picture and try again.",
        "STORAGE_UNAVAILABLE" to "TV storage is unavailable. Your saved pictures and choices have been kept.",
        "UPLOAD_BUSY" to "Another picture transfer is still active. Wait a moment and try again.",
        "UPLOAD_TOO_LARGE" to "This picture exceeds the TV image limits. Choose another picture.",
        "BUILTIN_READ_ONLY" to "Built-in pictures cannot be deleted or edited.",
        "NOT_FOUND" to "This picture is no longer on the TV. Refresh the gallery and try again.",
        "INVALID_REQUEST" to "This change could not be applied. Review it and try again.",
        "SESSION_EXPIRED" to "The Color Dock connection expired. Reconnect and try again.",
        "UNSUPPORTED_VERSION" to "Update Color Dock on your TV to continue.",
        "HASH_MISMATCH" to "The picture transfer was interrupted. Please try again.",
        "OUT_OF_ORDER" to "The picture transfer was interrupted. Please try again.",
        "INVALID_IMAGE" to "This picture could not be decoded.",
    )

private fun message(code: String?) = failureMessages[code] ?: "Open Color Dock to continue."

private fun acceptedLimits(hello: JSONObject): DockLimits {
    if (DockCodec.integer(hello.opt("protocolVersion")) != 1L) throw DockException("UNSUPPORTED_VERSION")
    val offered = hello.getJSONObject("limits")
    val maximum = DockLimits()

    fun limit(
        name: String,
        max: Int,
    ) = DockCodec
        .integer(offered.opt(name))
        .also {
            require(it > 0)
        }.coerceAtMost(max.toLong())
        .toInt()
    return DockLimits(
        DockCodec
            .integer(offered.opt("chunkBytes"))
            .also {
                require(it in MIN_CHUNK_BYTES..maximum.chunkBytes.toLong())
            }.toInt(),
        limit("imageBytes", maximum.imageBytes),
        limit("thumbnailBytes", maximum.thumbnailBytes),
    )
}

private fun requireCurrentTransport(current: Boolean) {
    if (!current) throw DockException("NOT_READY")
}

private fun failedJoinState(error: Exception): DockState {
    val code = (error as? DockException)?.code
    val connection =
        if (code == "UNSUPPORTED_VERSION") {
            DockConnectionState.Incompatible
        } else {
            DockConnectionState.Unavailable
        }
    return DockState(connection, message = message(code))
}
