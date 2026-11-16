package com.emmah.colordock.remote.mediacenter

import android.util.Log

/** Opt-in log contains only controlled stages, exception classes and source frames, never payloads. */
internal fun protocolTrace(
    stage: String,
    failure: Throwable? = null,
) {
    val enabled = runCatching { Log.isLoggable("RemoteProtocol", Log.DEBUG) }.getOrDefault(false)
    if (!enabled) return
    val detail =
        failure
            ?.let {
                ": ${it.javaClass.simpleName} at ${it.stackTrace.take(TRACE_FRAMES).joinToString()}"
            }.orEmpty()
    Log.d("RemoteProtocol", stage + detail)
}

private const val TRACE_FRAMES = 3
