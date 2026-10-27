package com.emmah.colordock.remote.tv

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.images.AppIconCache
import com.emmah.colordock.remote.ui.RemoteViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import okio.ByteString
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TvAppsTransportTest {
    private val target = requireNotNull(TvTarget.parse("192.168.1.10"))
    private val icon = "https://192.168.1.10:3001/resources/logo.png"

    @Test fun remoteCommandsUseExpectedEndpointsAndPowerOffDisconnects() =
        scenario { client, _, _, socket ->
            val commands =
                listOf(
                    TvCommand.VolumeUp to "audio/volumeUp",
                    TvCommand.VolumeDown to "audio/volumeDown",
                    TvCommand.Mute to "audio/setMute",
                    TvCommand.Unmute to "audio/setMute",
                    TvCommand.ChannelUp to "tv/channelUp",
                    TvCommand.ChannelDown to "tv/channelDown",
                    TvCommand.LiveTv to "system.launcher/launch",
                    TvCommand.PowerOff to "system/turnOff",
                )
            for ((command, endpoint) in commands) {
                val sending = async { client.send(command) }
                val request = socket.requests.receive()
                assertEquals("ssap://$endpoint", request.getString("uri"))
                if (command == TvCommand.Mute || command == TvCommand.Unmute) {
                    assertEquals(command == TvCommand.Mute, request.getJSONObject("payload").getBoolean("mute"))
                }
                socket.reply(request, """{"returnValue":true}""")
                assertTrue(sending.await() is TvResult.Success)
            }
            assertEquals(TvConnectionState.Disconnected, client.state.value.connection)
            assertTrue(client.send(TvCommand.VolumeUp) is TvResult.Failure)
            assertTrue(client.launchColorDock() is TvResult.Failure)
            client.refreshStatus()
        }

    @Test fun channelTuningStatusAndDockLaunchReachTransport() =
        scenario { client, _, _, socket ->
            val loading = async { client.channels() }
            socket.reply(
                socket.requests.receive(),
                """{"channelList":[{"channelId":"one","channelNumber":"1","channelName":"First"}]}""",
            )
            assertEquals("one", (loading.await() as TvResult.Success).value.single().id)
            val tuning = async { client.tune("one") }
            val launch = socket.requests.receive()
            assertEquals("com.webos.app.livetv", launch.getJSONObject("payload").getString("id"))
            socket.reply(launch, """{"returnValue":true}""")
            val channel = socket.requests.receive()
            assertEquals("one", channel.getJSONObject("payload").getString("channelId"))
            socket.reply(channel, """{"returnValue":true}""")
            assertTrue(tuning.await() is TvResult.Success)
            val refresh = async { client.refreshStatus() }
            socket.reply(socket.requests.receive(), """{"mute":true}""")
            socket.reply(socket.requests.receive(), """{"channelId":"one","channelNumber":"1","channelName":"First"}""")
            refresh.await()
            assertEquals(true, client.state.value.muted)
            assertEquals(
                "one",
                client.state.value.channel
                    ?.id,
            )
            val dock = async { client.launchColorDock() }
            val request = socket.requests.receive()
            assertEquals("com.emmah.colordock", request.getJSONObject("payload").getString("id"))
            socket.reply(request, """{"returnValue":true}""")
            assertTrue(dock.await() is TvResult.Success)
        }

    @Test fun badControlAndStatusRepliesFailWithoutRetainingStaleStatus() =
        scenario { client, _, _, socket ->
            val pointer = async { client.send(TvCommand.Ok) }
            socket.reply(socket.requests.receive(), """{"socketPath":"wss://example.com:3001/pointer"}""")
            assertTrue(pointer.await() is TvResult.Failure)
            val refresh = async { client.refreshStatus() }
            socket.reply(socket.requests.receive(), """{"returnValue":false}""")
            socket.reply(socket.requests.receive(), """{"returnValue":false}""")
            refresh.await()
            assertNull(client.state.value.muted)
            assertNull(client.state.value.channel)
            val sending = async { client.send(TvCommand.VolumeUp) }
            socket.reply(socket.requests.receive(), """{"returnValue":false}""")
            assertTrue(sending.await() is TvResult.Failure)
        }

    @Test fun viewModelConnectsLoadsChannelsSendsAndForgets() =
        scenario { client, wire, _, _ ->
            val application =
                object : android.app.Application() {
                    init {
                        attachBaseContext(testContext())
                    }

                    override fun getApplicationContext(): Context = baseContext.applicationContext
                }
            val store = androidx.lifecycle.ViewModelStore()
            val model =
                withContext(Dispatchers.Main.immediate) {
                    RemoteViewModel(application, client, AppIconCache(application, fetch = client::appIcon)).also {
                        store.put("remote", it)
                    }
                }
            try {
                withContext(Dispatchers.Main.immediate) {
                    model.connect("not-an-address")
                    model.connect(target.address)
                }
                val socket = wire.opened.receive()
                socket.hello()
                val responder = respondToRemote(socket)
                model.channels.first { it.isNotEmpty() }
                assertEquals(
                    "one",
                    model.channels.value
                        .single()
                        .id,
                )
                withContext(Dispatchers.Main.immediate) { model.send(TvCommand.VolumeUp) }
                model.error.first { it != null }
                withContext(Dispatchers.Main.immediate) {
                    model.clearError()
                    model.foreground()
                    model.background()
                    model.foreground()
                }
                assertNull(model.error.value)
                assertEquals(TvConnectionState.Ready, client.state.value.connection)
                withContext(Dispatchers.Main.immediate) { model.forget() }
                model.state.first { it.connection == TvConnectionState.Disconnected }
                model.savedAddress.first { it == null }
                model.channels.first { it.isEmpty() }
                assertTrue(model.channels.value.isEmpty())
                responder.cancelAndJoin()
            } finally {
                withContext(Dispatchers.Main.immediate) { store.clear() }
            }
        }

    private fun CoroutineScope.respondToRemote(socket: Socket) =
        launch {
            for (request in socket.requests) {
                val reply =
                    when (request.optString("uri")) {
                        "ssap://tv/getChannelList" -> {
                            """{"channelList":[{"channelId":"one","channelNumber":"1","channelName":"First"}]}"""
                        }

                        "ssap://audio/getMute" -> {
                            """{"mute":false}"""
                        }

                        "ssap://tv/getCurrentChannel" -> {
                            """{"channelId":"one","channelNumber":"1","channelName":"First"}"""
                        }

                        "ssap://com.webos.applicationManager/listApps" -> {
                            """{"apps":[]}"""
                        }

                        "ssap://com.webos.applicationManager/listLaunchPoints" -> {
                            """{"launchPoints":[]}"""
                        }

                        "ssap://audio/volumeUp" -> {
                            """{"returnValue":false}"""
                        }

                        else -> {
                            """{"returnValue":true}"""
                        }
                    }
                socket.reply(request, reply)
            }
        }

    private class Wire : WebSocket.Factory {
        val opened = Channel<Socket>(Channel.UNLIMITED)

        override fun newWebSocket(
            request: Request,
            listener: WebSocketListener,
        ): WebSocket =
            Socket(request, listener).also {
                opened.trySend(it)
            }
    }

    private class Socket(
        private val request: Request,
        val listener: WebSocketListener,
    ) : WebSocket {
        val requests = Channel<JSONObject>(Channel.UNLIMITED)
        val sent = mutableListOf<JSONObject>()

        override fun request() = request

        override fun queueSize() = 0L

        override fun send(text: String): Boolean {
            val value = JSONObject(text)
            sent += value
            when (value.optString("type")) {
                "register" -> {
                    listener.onMessage(
                        this,
                        JSONObject()
                            .put("type", "registered")
                            .put("id", value.getString("id"))
                            .put("payload", JSONObject().put("client-key", "test-only-key"))
                            .toString(),
                    )
                }

                "request" -> {
                    requests.trySend(value)
                }
            }
            return true
        }

        override fun send(bytes: ByteString) = false

        override fun close(
            code: Int,
            reason: String?,
        ) = true

        override fun cancel() {
            // This in-memory socket owns no resources or asynchronous transport work.
        }

        fun hello() {
            listener.onOpen(
                this,
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(101)
                    .message("test")
                    .build(),
            )
            listener.onMessage(
                this,
                """{"type":"hello","payload":{"deviceUUID":"test-only-tv","deviceModel":"Fixture TV"}}""",
            )
        }

        fun reply(
            request: JSONObject,
            payload: String,
        ) = listener.onMessage(
            this,
            JSONObject()
                .put("type", "response")
                .put("id", request.getString("id"))
                .put("payload", JSONObject(payload))
                .toString(),
        )
    }

    private class Http : Call.Factory {
        val calls = Channel<PendingCall>(Channel.UNLIMITED)
        var count = 0

        override fun newCall(request: Request): Call =
            PendingCall(request).also {
                count++
                calls.trySend(it)
            }
    }

    private class PendingCall(
        private val request: Request,
    ) : Call by OkHttpClient().newCall(request) {
        val ready = CompletableDeferred<Callback>()
        var cancelled = false

        override fun request() = request

        override fun enqueue(responseCallback: Callback) {
            ready.complete(responseCallback)
        }

        override fun execute(): Response = error("Only async HTTP is allowed")

        override fun cancel() {
            cancelled = true
        }

        override fun isExecuted() = ready.isCompleted

        override fun isCanceled() = cancelled

        override fun timeout() = Timeout.NONE

        override fun clone(): Call = PendingCall(request)

        suspend fun reply(
            body: ResponseBody,
            code: Int = 200,
        ) {
            ready.await().onResponse(
                this,
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("fixture")
                    .header("Location", "https://example.com/logo.png")
                    .body(body)
                    .build(),
            )
        }
    }

    private fun testContext(): Context {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) {
            "Use a disposable emulator"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.ACCESS_LOCAL_NETWORK,
        )
        // Instrumentation runs with the target UID; isolate files within this disposable installation.
        val directory =
            java.io.File(instrumentation.targetContext.cacheDir, "transport-fixture").apply {
                mkdirs()
            }
        return object : ContextWrapper(instrumentation.targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir() =
                java.io.File(directory, "files").apply {
                    mkdirs()
                }

            override fun getNoBackupFilesDir() =
                java.io.File(directory, "no-backup").apply {
                    mkdirs()
                }

            override fun checkSelfPermission(permission: String) =
                instrumentation.targetContext.checkSelfPermission(permission)

            override fun getSystemService(name: String): Any? = instrumentation.targetContext.getSystemService(name)
        }
    }

    private suspend fun CoroutineScope.connected(
        wire: Wire,
        client: LgTvClient,
    ): Socket {
        val connecting =
            async {
                client.connect(target)
            }
        val socket = wire.opened.receive()
        socket.hello()
        assertTrue(connecting.await() is TvResult.Success)
        return socket
    }

    private fun scenario(block: suspend CoroutineScope.(LgTvClient, Wire, Http, Socket) -> Unit) =
        runBlocking {
            withTimeout(20_000) {
                val context = testContext()
                val store = TvCredentialStore(context)
                store.save(TvCredentials(target.address, "test-only-tv", "Fixture TV", "a".repeat(64), "test-only-key"))
                val wire = Wire()
                val http = Http()
                val client = LgTvClient(context, wire, http)
                try {
                    block(client, wire, http, connected(wire, client))
                } finally {
                    client.disconnect()
                    store.forget()
                }
            }
        }

    private suspend fun CoroutineScope.catalog(
        client: LgTvClient,
        socket: Socket,
        installed: String = """{"apps":[{"id":"netflix","title":"Netflix","visible":true}]}""",
        points: String = """{"launchPoints":[{"id":"netflix","title":"Netflix","hidden":false}]}""",
    ): TvResult<List<TvApp>> {
        val loading =
            async {
                client.apps()
            }
        val list = socket.requests.receive()
        assertEquals("ssap://com.webos.applicationManager/listApps", list.getString("uri"))
        socket.reply(list, installed)
        if (JSONObject(installed).opt("returnValue") != false && JSONObject(installed).optJSONArray("apps") != null) {
            val launchPoints = socket.requests.receive()
            assertEquals("ssap://com.webos.applicationManager/listLaunchPoints", launchPoints.getString("uri"))
            socket.reply(launchPoints, points)
        }
        return loading.await()
    }

    @Test fun statusRefreshUpdatesValuesAndClearsFailedFields() =
        scenario { client, _, _, socket ->
            for (succeeds in listOf(true, false)) {
                val refreshing =
                    async {
                        client.refreshStatus()
                    }
                val audio = socket.requests.receive()
                assertEquals("ssap://audio/getMute", audio.getString("uri"))
                socket.reply(audio, if (succeeds) """{"mute":true}""" else """{"returnValue":false}""")
                val channel = socket.requests.receive()
                assertEquals("ssap://tv/getCurrentChannel", channel.getString("uri"))
                socket.reply(
                    channel,
                    if (succeeds) {
                        """{"channelId":"news","channelNumber":"12","channelName":"News"}"""
                    } else {
                        """{"returnValue":false}"""
                    },
                )
                refreshing.await()
                assertEquals(if (succeeds) true else null, client.state.value.muted)
                assertEquals(if (succeeds) TvChannel("news", "12", "News") else null, client.state.value.channel)
                assertEquals(TvConnectionState.Ready, client.state.value.connection)
            }
        }

    @Test fun cancelledOrReplacedStatusRefreshCannotPublishOrReadAnotherChannel() =
        scenario { client, wire, _, socket ->
            val cancelled =
                launch {
                    client.refreshStatus()
                }
            socket.requests.receive()
            cancelled.cancelAndJoin()
            assertTrue(cancelled.isCancelled)
            assertNull(client.state.value.muted)
            assertTrue(socket.requests.tryReceive().isFailure)

            val obsolete =
                launch {
                    client.refreshStatus()
                }
            val audio = socket.requests.receive()
            val replacement = connected(wire, client)
            socket.reply(audio, """{"mute":true}""")
            obsolete.join()
            assertNull(client.state.value.muted)
            assertNull(client.state.value.channel)
            assertTrue(replacement.requests.tryReceive().isFailure)
        }

    @Test fun discoveredAppLaunchesOnceAndRemovedIdsNeverLaunch() =
        scenario { client, _, _, socket ->
            assertTrue(catalog(client, socket) is TvResult.Success)
            assertTrue(
                socket.sent
                    .single {
                        it.optString("type") == "register"
                    }.getJSONObject("payload")
                    .getJSONObject("manifest")
                    .getJSONArray("permissions")
                    .toString()
                    .contains("READ_INSTALLED_APPS"),
            )
            val launch =
                async {
                    client.launchApp("netflix")
                }
            val request = socket.requests.receive()
            assertEquals("ssap://system.launcher/launch", request.getString("uri"))
            assertEquals("""{"id":"netflix"}""", request.getJSONObject("payload").toString())
            socket.reply(request, """{"returnValue":true}""")
            assertTrue(launch.await() is TvResult.Success)
            catalog(client, socket, """{"apps":[]}""", """{"launchPoints":[]}""")
            assertTrue(client.launchApp("netflix") is TvResult.Failure)
            assertTrue(client.launchApp("fallback-only") is TvResult.Failure)
            assertEquals(
                1,
                socket.sent.count {
                    it.optString("uri") == "ssap://system.launcher/launch"
                },
            )
        }

    @Test fun failedDiscoveryAndLaunchStayLocalAndMetadataFallbackIsVisibleOnly() =
        scenario { client, _, _, socket ->
            val fallback =
                catalog(
                    client,
                    socket,
                    """{"apps":[{"id":"netflix","title":"Netflix","visible":true},{"id":"service",""" +
                        """"title":"Service"}]}""",
                    """{"returnValue":false}""",
                )
            assertEquals(
                listOf("netflix"),
                (fallback as TvResult.Success).value.map {
                    it.id
                },
            )
            assertTrue(catalog(client, socket, """{"returnValue":false}""") is TvResult.Failure)
            val launch =
                async {
                    client.launchApp("netflix")
                }
            socket.reply(socket.requests.receive(), """{"returnValue":false}""")
            assertTrue(launch.await() is TvResult.Failure)
            assertEquals(TvConnectionState.Ready, client.state.value.connection)
            val volume =
                async {
                    client.send(TvCommand.VolumeUp)
                }
            socket.reply(socket.requests.receive(), """{"returnValue":true}""")
            assertTrue(volume.await() is TvResult.Success)
        }

    @Test fun queuedLaunchCannotMoveToReplacementSession() =
        scenario { client, wire, _, old ->
            catalog(client, old)
            val volume =
                async {
                    client.send(TvCommand.VolumeUp)
                }
            old.requests.receive()
            val queued =
                async(start = CoroutineStart.UNDISPATCHED) {
                    client.launchApp("netflix")
                }
            val replacement = connected(wire, client)
            assertTrue(volume.await() is TvResult.Failure)
            assertTrue(queued.await() is TvResult.Failure)
            assertFalse(
                replacement.sent.any {
                    it.optString("uri") == "ssap://system.launcher/launch"
                },
            )
        }

    @Test fun iconRejectsForeignOriginRedirectsAndOversizedBodies() =
        scenario { client, _, http, _ ->
            assertTrue(client.appIcon("https://example.com/logo.png") is TvResult.Failure)
            assertEquals(0, http.count)
            for (case in 0..2) {
                val fetching =
                    async {
                        client.appIcon(icon)
                    }
                val call = http.calls.receive()
                var closed = false
                val source =
                    object : ForwardingSource(Buffer().write(ByteArray(if (case == 2) 1_048_577 else 4))) {
                        override fun close() {
                            closed = true
                            super.close()
                        }
                    }.buffer()
                val body =
                    object : ResponseBody() {
                        override fun contentType(): MediaType? = null

                        override fun contentLength() = if (case == 1) 1_048_577L else -1L

                        override fun source() = source
                    }
                call.reply(body, if (case == 0) 302 else 200)
                assertTrue(fetching.await() is TvResult.Failure)
                assertTrue("Rejected responses must be closed", closed)
            }
            assertEquals(3, http.count)
        }

    @Test fun cancellationCancelsHttpAndOldSessionCannotReturnImage() =
        scenario { client, wire, http, _ ->
            val cancelled =
                async {
                    client.appIcon(icon)
                }
            val first = http.calls.receive()
            first.ready.await()
            cancelled.cancelAndJoin()
            assertTrue(first.cancelled)
            val fetching =
                async {
                    client.appIcon(icon)
                }
            val old = http.calls.receive()
            old.ready.await()
            connected(wire, client)
            old.reply(byteArrayOf(1, 2, 3).toResponseBody())
            assertTrue(fetching.await() is TvResult.Failure)
            val valid =
                async {
                    client.appIcon(icon)
                }
            http.calls.receive().reply(byteArrayOf(4, 5).toResponseBody())
            assertArrayEquals(byteArrayOf(4, 5), (valid.await() as TvResult.Success).value)
        }

    private class IconReplies {
        val started = CompletableDeferred<Unit>()
        val oldReply = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var fetches = 0
            private set

        suspend fun fetch(): TvResult<ByteArray> {
            val number = ++fetches
            if (number == 1) {
                withContext(NonCancellable) {
                    started.complete(Unit)
                    oldReply.await()
                    finished.complete(Unit)
                }
            }
            val bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if (number == 2) android.graphics.Color.BLUE else android.graphics.Color.RED)
            val data =
                java.io.ByteArrayOutputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    it.toByteArray()
                }
            bitmap.recycle()
            return TvResult.Success(data)
        }
    }

    private suspend fun answerCatalog(on: Socket) {
        on.reply(
            on.requests.receive(),
            """{"apps":[{"id":"netflix","title":"Netflix","visible":true,"icon":"$icon"}]}""",
        )
        on.reply(on.requests.receive(), """{"launchPoints":[{"id":"netflix","title":"Netflix"}]}""")
    }

    private suspend fun refreshIconCatalog(
        model: RemoteViewModel,
        socket: Socket,
        current: java.io.File,
    ) {
        val revision = model.apps.value.catalogRevision
        withContext(Dispatchers.Main) {
            model.refreshApps()
        }
        answerCatalog(socket)
        model.apps.first {
            it.catalogRevision > revision
        }
        withContext(Dispatchers.Main) {
            model.loadAppIcon("netflix")
        }
        model.appIcons.first {
            it["netflix"] != current
        }
    }

    private suspend fun CoroutineScope.replaceIconSession(
        client: LgTvClient,
        wire: Wire,
        socket: Socket,
        model: RemoteViewModel,
        replies: IconReplies,
    ): Pair<Socket, java.io.File> {
        answerCatalog(socket)
        val initial =
            model.apps.first {
                it.hasLoaded
            }
        withContext(Dispatchers.Main) {
            model.loadAppIcon("netflix")
        }
        replies.started.await()
        val replacement = connected(wire, client)
        answerCatalog(replacement)
        model.apps.first {
            it.hasLoaded && it.sessionGeneration != initial.sessionGeneration
        }
        assertTrue(model.appIcons.value.isEmpty())
        withContext(Dispatchers.Main) {
            model.loadAppIcon("netflix")
        }
        val current =
            model.appIcons
                .first {
                    it.isNotEmpty()
                }.getValue("netflix")
        replies.oldReply.complete(Unit)
        replies.finished.await()
        withContext(Dispatchers.Main) {
            assertEquals(current, model.appIcons.value["netflix"])
        }
        return replacement to current
    }

    @Test fun viewModelDiscardsOldIconsAndRefreshesAnUnchangedUrl() =
        scenario { client, wire, _, socket ->
            val application =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                    as android.app.Application
            val directory =
                java.io.File(application.cacheDir, "icon-ownership-${System.nanoTime()}").apply {
                    mkdirs()
                }
            val context =
                object : ContextWrapper(application) {
                    override fun getCacheDir() = directory
                }
            val replies = IconReplies()
            val cache = AppIconCache(context) { replies.fetch() }
            val store = androidx.lifecycle.ViewModelStore()
            val model =
                withContext(Dispatchers.Main) {
                    RemoteViewModel(application, client, cache).also {
                        store.put("fixture", it)
                    }
                }

            try {
                val (replacement, current) = replaceIconSession(client, wire, socket, model, replies)
                refreshIconCatalog(model, replacement, current)
                assertEquals(3, replies.fetches)
                withContext(Dispatchers.Main) {
                    client.disconnect()
                }
                model.apps.first {
                    !it.connected
                }
                assertTrue(model.appIcons.value.isEmpty())
            } finally {
                replies.oldReply.complete(Unit)
                withContext(Dispatchers.Main) {
                    store.clear()
                }
                directory.deleteRecursively()
            }
        }
}
