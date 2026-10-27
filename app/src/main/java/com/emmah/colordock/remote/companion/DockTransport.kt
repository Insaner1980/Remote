package com.emmah.colordock.remote.companion

import com.emmah.colordock.remote.tv.TvState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

interface DockTransport {
    val state: StateFlow<TvState>
    val dockMessages: SharedFlow<JSONObject>
    val dockAvailable: StateFlow<Boolean>
    val connectionToken: Any? get() = state.value

    suspend fun joinDockChannel()

    fun sendDock(payload: JSONObject)

    fun sendDock(
        payload: JSONObject,
        origin: Any?,
    ) {
        check(connectionToken === origin)
        sendDock(payload)
    }
}
