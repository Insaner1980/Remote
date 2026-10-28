package com.emmah.colordock.remote.ui

import android.app.Application
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.colordock.remote.companion.CompanionClient
import com.emmah.colordock.remote.companion.DockTestFixtures
import com.emmah.colordock.remote.companion.DockTransport
import com.emmah.colordock.remote.tv.TvConnectionState
import com.emmah.colordock.remote.tv.TvState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import java.io.File
import java.util.UUID

internal class ViewModelDockFixture {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.cacheDir, "viewmodel-${UUID.randomUUID()}").apply { mkdirs() }
    val application =
        object : Application() {
            init {
                attachBaseContext(
                    object : ContextWrapper(context) {
                        override fun getCacheDir() = directory

                        override fun getNoBackupFilesDir() = directory
                    },
                )
            }
        }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = ViewModelStore()
    var identity: String? = "fixture-tv"
    var token: Any? = Any()
    var snapshot = DockTestFixtures.snapshot()
    val requests = mutableListOf<JSONObject>()
    var failure: String? = null
    var response: (String, JSONObject) -> JSONObject? = { method, _ ->
        when (method) {
            "gallery.list" -> page(listOf("user:one"))
            "upload.status" -> JSONObject().put("status", "receiving")
            "settings.patch" -> snapshot
            else -> JSONObject()
        }
    }
    val tvState = MutableStateFlow(TvState(TvConnectionState.Ready))
    val messages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 32)
    val transport =
        object : DockTransport {
            override val state = tvState
            override val dockAvailable = MutableStateFlow(true)
            override val dockMessages = messages
            override val connectionToken get() = token

            override suspend fun joinDockChannel() = Unit

            override fun sendDock(payload: JSONObject) {
                requests.add(JSONObject(payload.toString()))
                val method = payload.getString("method")
                val value =
                    when (method) {
                        "hello" -> DockTestFixtures.hello()
                        "state.get" -> snapshot
                        else -> response(method, payload.getJSONObject("params"))
                    } ?: return
                val reply = DockTestFixtures.reply(payload, value)
                if (failure != null && method !in listOf("hello", "state.get")) {
                    reply.put("ok", false).put("error", JSONObject().put("code", failure))
                }
                scope.launch { dockMessages.emit(reply) }
            }
        }
    val dock = CompanionClient(transport, scope)

    fun wallpaper() =
        WallpaperViewModel(application, dock, transport.state) { identity }.also {
            store.put("wallpaper", it)
        }

    fun close() {
        store.clear()
        dock.disconnect()
        scope.cancel()
        assertTrue(directory.deleteRecursively())
    }

    companion object {
        fun page(
            ids: List<String>,
            cursor: String? = null,
            revision: Int = 1,
        ): JSONObject =
            JSONObject()
                .put("galleryRevision", revision)
                .put("nextCursor", cursor ?: JSONObject.NULL)
                .put("items", JSONArray(ids.map { JSONObject().put("id", it).put("title", it).put("fit", "cover") }))
    }
}

internal fun dockScenario(block: suspend ViewModelDockFixture.() -> Unit) =
    runBlocking {
        withTimeout(20_000) {
            withContext(Dispatchers.Main.immediate) {
                val fixture = ViewModelDockFixture()
                try {
                    fixture.block()
                } finally {
                    fixture.close()
                }
            }
        }
    }
