package com.emmah.colordock.remote.mediacenter

import android.content.Context
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.atomic.AtomicBoolean

/** Discovery supplies locations only for an existing pinned identity. It never issues trust. */
class MediacenterNsdDiscovery internal constructor(
    private val backend: MediacenterNsdBackend,
    private val network: Network,
    private val available: () -> Boolean,
    private val expectedDeviceId: String,
    private val onCandidates: (List<MediacenterEndpoint>) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    constructor(
        context: Context,
        route: AndroidMediacenterNetwork,
        expectedDeviceId: String,
        onCandidates: (List<MediacenterEndpoint>) -> Unit,
        onError: (String) -> Unit,
    ) :
        this(
            AndroidMediacenterNsdBackend(context.applicationContext),
            route.network,
            route::available,
            expectedDeviceId,
            onCandidates,
            onError,
        )

    private val closed = AtomicBoolean(false)
    private val services = linkedMapOf<String, NsdManager.ServiceInfoCallback>()
    private val candidates = linkedMapOf<String, List<MediacenterEndpoint>>()
    private var started = false
    private val listener =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit

            override fun onDiscoveryStopped(type: String) = Unit

            override fun onStartDiscoveryFailed(
                type: String,
                code: Int,
            ) {
                if (!closed.get()) onError(message(code))
            }

            override fun onStopDiscoveryFailed(
                type: String,
                code: Int,
            ) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                observe(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                remove(info.serviceName)
            }
        }

    fun start() {
        check(!closed.get())
        if (!available()) {
            onError("Allow local network access and select Wi-Fi.")
            return
        }
        if (!started) {
            started = true
            try {
                backend.discover(SERVICE_TYPE, network, listener)
            } catch (
                _: SecurityException,
            ) {
                onError("Allow local network access to find Mediacenter.")
                close()
            }
        }
    }

    private fun observe(info: NsdServiceInfo) {
        if (closed.get() || services.containsKey(info.serviceName) || services.size >= MAX_SERVICES) return
        val name = info.serviceName
        val callback =
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(code: Int) {
                    if (!owns(name, this)) return
                    services.remove(name)
                    candidates.remove(name)
                    publish()
                    onError(message(code))
                }

                override fun onServiceInfoCallbackUnregistered() = Unit

                override fun onServiceLost() {
                    if (!owns(name, this)) return
                    candidates.remove(name)
                    publish()
                }

                override fun onServiceUpdated(value: NsdServiceInfo) {
                    if (!owns(name, this) || !available() || value.network != network) return
                    candidates[name] =
                        MediacenterDiscovery.candidates(
                            value.serviceType,
                            value.attributes,
                            value.hostAddresses.mapNotNull { it.hostAddress },
                            value.port,
                            expectedDeviceId,
                        )
                    publish()
                }
            }
        services[name] = callback
        try {
            backend.observe(info, callback)
        } catch (
            _: SecurityException,
        ) {
            remove(name)
            onError("Allow local network access to find Mediacenter.")
        }
    }

    private fun owns(
        name: String,
        callback: NsdManager.ServiceInfoCallback,
    ): Boolean = !closed.get() && services[name] === callback

    private fun publish() {
        if (!closed.get()) {
            onCandidates(
                candidates.values.flatten().distinctBy {
                    it.address to
                        it.port
                },
            )
        }
    }

    private fun remove(name: String) {
        services.remove(name)?.let { runCatching { backend.remove(it) } }
        candidates.remove(name)
        publish()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            if (started) runCatching { backend.stop(listener) }
            services.values.forEach { runCatching { backend.remove(it) } }
            services.clear()
            candidates.clear()
        }
    }

    private fun message(code: Int): String =
        if (code ==
            NsdManager.FAILURE_PERMISSION_DENIED
        ) {
            "Allow local network access to find Mediacenter."
        } else {
            "Discovery unavailable. Use the QR code or enter the computer address."
        }

    private companion object {
        const val SERVICE_TYPE = "_emma-mc._tcp."
        const val MAX_SERVICES = 32
    }
}
