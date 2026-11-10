package com.emmah.colordock.remote.mediacenter

/** Handles asynchronous camera callbacks arriving after their route has already closed. */
class MediacenterScannerResources : AutoCloseable {
    private val resources = mutableListOf<AutoCloseable>()
    private var closed = false

    fun own(resource: AutoCloseable): Boolean {
        val accepted =
            synchronized(resources) {
                if (closed) {
                    false
                } else {
                    resources.add(resource)
                    true
                }
            }
        if (!accepted) resource.close()
        return accepted
    }

    override fun close() {
        val owned =
            synchronized(resources) {
                if (closed) {
                    emptyList()
                } else {
                    closed = true
                    resources.asReversed().toList().also { resources.clear() }
                }
            }
        owned.forEach { runCatching { it.close() } }
    }
}
