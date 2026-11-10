package com.emmah.colordock.remote.ui.mediacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emmah.colordock.remote.mediacenter.MediacenterConnectionState
import com.emmah.colordock.remote.mediacenter.MediacenterRepository
import com.emmah.colordock.remote.mediacenter.protocol.ActivityPreparation
import com.emmah.colordock.remote.mediacenter.protocol.AudioConfirmParams
import com.emmah.colordock.remote.mediacenter.protocol.AudioPrepareParams
import com.emmah.colordock.remote.mediacenter.protocol.AudioTestParams
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationPreview
import com.emmah.colordock.remote.mediacenter.protocol.ConfirmationsCancelParams
import com.emmah.colordock.remote.mediacenter.protocol.Delivery
import com.emmah.colordock.remote.mediacenter.protocol.Empty
import com.emmah.colordock.remote.mediacenter.protocol.Expected
import com.emmah.colordock.remote.mediacenter.protocol.McReply
import com.emmah.colordock.remote.mediacenter.protocol.McRequest
import com.emmah.colordock.remote.mediacenter.protocol.OperationState
import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import com.emmah.colordock.remote.mediacenter.protocol.OutputsTopic
import com.emmah.colordock.remote.mediacenter.protocol.RemoteCommand
import com.emmah.colordock.remote.mediacenter.protocol.RemoteResult
import com.emmah.colordock.remote.mediacenter.protocol.WireErrorCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.util.UUID

internal data class AudioSettingsState(
    val outputs: OutputsTopic? = null,
    val preview: ConfirmationPreview? = null,
    val busy: Boolean = false,
    val message: String = "Connect to Mediacenter to choose a sound output.",
)

/** Correlates the server's sheet; confirmed sound comes only from native output observations. */
internal class AudioSettingsController(
    private val send: (McRequest) -> Boolean,
) {
    private val mutable = MutableStateFlow(AudioSettingsState())
    val state = mutable.asStateFlow()
    val outputs: OutputsTopic? get() = mutable.value.outputs
    private var epoch: String? = null
    private var requestId: String? = null
    private var operationId: String? = null
    private var observedOutcome: OperationStatus? = null

    fun observe(
        currentEpoch: String?,
        value: OutputsTopic?,
        operations: List<OperationState> = emptyList(),
    ) {
        if (epoch != currentEpoch || value == null) {
            requestId = null
            operationId = null
            observedOutcome = null
            mutable.value = AudioSettingsState(outputs = value)
        }
        epoch = currentEpoch
        mutable.update {
            it.copy(
                outputs = value,
                message =
                    if (!it.busy &&
                        it.preview == null
                    ) {
                        outputStatus(value)
                    } else {
                        it.message
                    },
            )
        }
        val operation =
            operations.firstOrNull {
                (operationId != null && it.operationId == operationId) ||
                    (requestId != null && it.requestId == requestId)
            }
                ?: return
        when (operation.status) {
            OperationStatus.SUCCEEDED -> {
                operationId = null
                observedOutcome = operation.status
                mutable.update { it.copy(busy = false, message = outputStatus(value)) }
            }

            OperationStatus.FAILED, OperationStatus.CANCELLED, OperationStatus.OUTCOME_UNKNOWN -> {
                operationId = null
                observedOutcome = operation.status
                mutable.update {
                    it.copy(
                        busy = false,
                        message = "Sound change was not confirmed. Retry or choose a sound output.",
                    )
                }
            }

            else -> {}
        }
    }

    fun choose(id: String): Boolean {
        if (outputs?.outputs?.none { it.id == id && it.available } != false) return false
        return submit(RemoteCommand.AudioPrepare(AudioPrepareParams(id)))
    }

    fun confirm(): Boolean {
        val preview = mutable.value.preview ?: return false
        return submit(RemoteCommand.AudioConfirm(AudioConfirmParams(preview.confirmationId)))
    }

    fun cancel(): Boolean {
        val preview = mutable.value.preview ?: return false
        return submit(RemoteCommand.ConfirmationsCancel(ConfirmationsCancelParams(preview.confirmationId)))
    }

    fun test(id: String): Boolean {
        if (outputs?.outputs?.none { it.id == id && it.available } != false) return false
        return submit(RemoteCommand.AudioTest(AudioTestParams(id)))
    }

    fun retry(refreshOnly: Boolean = false): Boolean {
        val desired = outputs?.requestedOutputId
        val available = outputs?.outputs?.firstOrNull { it.id == desired && it.available }
        return if (!refreshOnly && available != null) {
            choose(available.id)
        } else {
            submit(RemoteCommand.AudioOutputs(Empty))
        }
    }

    private fun submit(command: RemoteCommand): Boolean {
        val current = epoch
        val request = current?.let { McRequest(UUID.randomUUID().toString(), command, Expected(serverEpoch = it)) }
        val idle = !mutable.value.busy
        val submitted = request != null && idle && send(request)
        if (submitted) {
            requestId = request.id
            observedOutcome = null
            mutable.update { it.copy(busy = command.delivery == Delivery.DURABLE) }
        } else if (current != null && idle) {
            mutable.update { it.copy(message = "Mediacenter is not ready. Reconnect and retry.") }
        }
        return submitted
    }

    fun receive(reply: McReply) {
        if (reply.id != requestId) return
        requestId = null
        mutable.update { it.copy(busy = false) }
        when (reply) {
            is McReply.Failure -> {
                mutable.update {
                    it.copy(
                        preview = reply.error.replacementPreview ?: it.preview,
                        message =
                            if (reply.error.code ==
                                WireErrorCode.AUDIO_OUTPUT_UNAVAILABLE
                            ) {
                                "Audio output unavailable"
                            } else {
                                reply.error.message
                            },
                    )
                }
            }

            is McReply.Success -> {
                success(reply.result)
            }
        }
    }

    private fun success(result: RemoteResult) {
        when (result) {
            is RemoteResult.AudioPrepare -> {
                when (val prepared = result.value) {
                    is ActivityPreparation.ConfirmationRequired -> {
                        mutable.update {
                            it.copy(preview = prepared.preview, message = "Review the sound output change.")
                        }
                    }

                    is ActivityPreparation.Accepted -> {
                        operationId = prepared.operation.operationId
                    }
                }
            }

            is RemoteResult.AudioConfirm -> {
                accepted(result.value.operationId)
            }

            is RemoteResult.AudioTest -> {
                accepted(result.value.operationId)
            }

            is RemoteResult.AudioOutputs -> {
                mutable.update { it.copy(outputs = result.value, message = outputStatus(result.value)) }
            }

            is RemoteResult.ConfirmationsCancel -> {
                mutable.update { it.copy(preview = null) }
            }

            else -> {}
        }
    }

    private fun accepted(id: String) {
        operationId = id.takeIf { observedOutcome == null }
        mutable.update {
            it.copy(
                preview = null,
                busy = observedOutcome == null,
                message =
                    if (observedOutcome ==
                        null
                    ) {
                        "Waiting for Mediacenter to confirm sound output…"
                    } else {
                        it.message
                    },
            )
        }
    }
}

private fun outputStatus(value: OutputsTopic?): String =
    when {
        value == null -> "Connect to Mediacenter to choose a sound output."

        value.requestedOutputId != null && value.effectiveOutputId == null &&
            value.outputs.any {
                it.id == value.requestedOutputId && it.available
            } -> "Sound preference saved; no playback stream has been confirmed."

        value.effectiveOutputId == null || value.unavailable -> "Audio output unavailable"

        else -> "Sound output confirmed by Mediacenter."
    }

@Composable
fun MediacenterSoundSettings(
    repository: MediacenterRepository,
    modifier: Modifier = Modifier,
) {
    val controller = remember(repository) { AudioSettingsController(repository::send) }
    val state by controller.state.collectAsState()
    LaunchedEffect(repository) {
        combine(repository.snapshot, repository.state) { snapshot, connection ->
            snapshot.takeIf { connection == MediacenterConnectionState.Connected }
        }.collect { snapshot ->
            controller.observe(snapshot?.serverEpoch, snapshot?.outputs, snapshot?.operations?.items.orEmpty())
        }
    }
    LaunchedEffect(repository) { repository.replies.collect(controller::receive) }
    SoundOptions(
        state,
        SoundActions(
            refresh = { controller.retry(refreshOnly = true) },
            retry = { controller.retry() },
            choose = { controller.choose(it) },
            test = { controller.test(it) },
            confirm = { controller.confirm() },
            cancel = { controller.cancel() },
        ),
        modifier,
    )
}

internal data class SoundActions(
    val refresh: () -> Unit,
    val retry: () -> Unit,
    val choose: (String) -> Unit,
    val test: (String) -> Unit,
    val confirm: () -> Unit,
    val cancel: () -> Unit,
)

@Composable
internal fun SoundOptions(
    state: AudioSettingsState,
    actions: SoundActions,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            open = true
            actions.refresh()
        }) { Text("Sound") }
        if (open) {
            Text("Mediacenter sound", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text("Output selection is optional. Existing sound continues without setup changes.")
            val outputs = state.outputs

            fun name(id: String?): String = outputs?.outputs?.firstOrNull { it.id == id }?.name ?: id ?: "Not confirmed"
            Text("Requested: ${name(outputs?.requestedOutputId)}")
            Text("Confirmed: ${name(outputs?.effectiveOutputId)}")
            Text(state.message)
            if (outputs?.unavailable == true) Text("Audio output unavailable. Retry or choose a sound output.")
            TextButton(onClick = { actions.retry() }, enabled = outputs != null && !state.busy) { Text("Retry") }
            outputs?.outputs?.forEach { output ->
                Row {
                    TextButton(
                        onClick = { actions.choose(output.id) },
                        enabled = output.available && !state.busy,
                    ) { Text(output.name) }
                    TextButton(
                        onClick = { actions.test(output.id) },
                        enabled = output.available && !state.busy,
                    ) { Text("Test sound") }
                }
            }
        }
    }
    state.preview?.let { preview ->
        AlertDialog(onDismissRequest = { actions.cancel() }, title = { Text("Change sound output?") }, text = {
            Column { preview.affectedLabels.forEach { Text(it) } }
        }, confirmButton = {
            TextButton(onClick = {
                actions.confirm()
            }, enabled = state.outputs != null && !state.busy) {
                Text("Confirm")
            }
        }, dismissButton = {
            TextButton(
                onClick = { actions.cancel() },
                enabled = state.outputs != null && !state.busy,
            ) { Text("Cancel") }
        })
    }
}
