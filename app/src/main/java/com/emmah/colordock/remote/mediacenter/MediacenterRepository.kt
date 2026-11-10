package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.Delivery
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.PairedMediacenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

/** The sole transport consumer. Connected means native snapshots are ready, not merely authenticated. */
class MediacenterRepository(
    control: MediacenterControl,
    scope: CoroutineScope,
    journal: PendingCommandStore,
    private val thumbnails: MediacenterThumbnails? = null,
    jitter: () -> Double = { Random.nextDouble() },
) : MediacenterControl {
    private val lock = Any()
    private val recovery = PendingRecovery(journal)
    val recoveryAvailable = recovery.available
    val pairedDeviceId: String? get() = selectedPairing.value?.deviceId
    private val selectedPairing = MutableStateFlow<PairedMediacenter?>(null)
    private val requests: MediacenterRequests =
        MediacenterRequests(control, scope, recovery, lock, { selectedPairing.value }, {
            connection.inForeground && connection.reducer?.ready == true
        })
    private val connection: MediacenterConnection =
        MediacenterConnection(
            control,
            scope,
            jitter,
            requests,
            lock,
        )
    override val state = connection.state
    override val messages = emptyFlow<String>()
    val snapshot = connection.snapshot
    val replies = requests.replies
    val pending =
        combine(recovery.pending, selectedPairing) { commands, pairing ->
            if (pairing == null) emptyList() else commands.filter { it.belongsTo(pairing) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val inputOutcomeUncertain = requests.inputOutcomeUncertain
    val uncertainInput: Boolean get() = inputOutcomeUncertain.value

    override fun connect(
        value: StoredMediacenter,
        network: MediacenterNetwork,
    ) = synchronized(lock) {
        thumbnails?.select(value, network)
        selectedPairing.value = value.paired
        connection.connect(value, network)
    }

    override fun disconnect() =
        synchronized(lock) {
            thumbnails?.retire()
            connection.disconnect()
            selectedPairing.value = null
        }

    override fun foreground() = connection.foreground()

    override fun background() = connection.background()

    fun retry() = connection.retry()

    override fun forget(serverId: String?): Boolean =
        synchronized(lock) {
            val server = serverId ?: selectedPairing.value?.serverDeviceId
            disconnect()
            thumbnails?.forget()
            server == null || requests.forget(server)
        }

    suspend fun thumbnail(id: String): ByteArray? {
        val owner =
            synchronized(lock) {
                selectedPairing.value.takeIf { state.value == MediacenterConnectionState.Connected }
            } ?: return null
        val bytes = thumbnails?.load(id)
        return synchronized(lock) {
            bytes.takeIf { selectedPairing.value === owner && state.value == MediacenterConnectionState.Connected }
        }
    }

    override fun send(request: McRequest): Boolean =
        synchronized(lock) {
            val target = selectedPairing.value ?: return@synchronized false
            val reducer = connection.reducer ?: return@synchronized false
            val greeting = reducer.hello ?: return@synchronized false
            val mutation = request.command.delivery != Delivery.READ
            if (!connection.inForeground || (mutation && !reducer.ready)) return@synchronized false
            if (mutation &&
                (request.command.method !in greeting.capabilities.methods || request.command.scope !in target.scopes)
            ) {
                return@synchronized false
            }
            requests.submit(request, target, greeting.serverEpoch)
        }
}
