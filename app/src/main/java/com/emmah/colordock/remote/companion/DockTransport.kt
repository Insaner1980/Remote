package com.emmah.colordock.remote.companion

import com.emmah.colordock.remote.tv.TvState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

interface DockTransport {
    val state:StateFlow<TvState>
    val dockMessages:SharedFlow<JSONObject>
    val dockAvailable:StateFlow<Boolean>
    suspend fun joinDockChannel()
    fun sendDock(payload:JSONObject)
}
