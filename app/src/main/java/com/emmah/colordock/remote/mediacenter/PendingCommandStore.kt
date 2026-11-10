package com.emmah.colordock.remote.mediacenter

import com.emmah.colordock.remote.mediacenter.protocol.OperationStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class PendingCommand(
    val serverId: String,
    val requestId: String,
    val serverEpoch: String,
    val operationId: String? = null,
    val status: OperationStatus = OperationStatus.OUTCOME_UNKNOWN,
    val resolved: Boolean = false,
    val pairedDeviceId: String = "",
    val cancellationRequested: Boolean = false,
)

interface PendingCommandStore {
    fun read(): List<PendingCommand>

    fun write(commands: List<PendingCommand>)
}

/** Bounded no-backup metadata only. Never stores commands, parameter hashes, text, pins or credentials. */
class FilePendingCommandStore(
    private val file: File,
) : PendingCommandStore {
    override fun read(): List<PendingCommand> {
        if (!file.exists()) return emptyList()
        require(file.length() <= MAX_BYTES)
        val array = JSONArray(file.readText(Charsets.UTF_8))
        require(array.length() <= MAX_COMMANDS)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            PendingCommand(
                item.getString("serverId"),
                item.getString("requestId"),
                item.getString("serverEpoch"),
                item.optString("operationId").takeIf { it.isNotEmpty() },
                OperationStatus.entries.first { it.wire == item.getString("status") },
                item.getBoolean("resolved"),
                item.getString("pairedDeviceId"),
                item.optBoolean("cancellationRequested", false),
            )
        }
    }

    override fun write(commands: List<PendingCommand>) {
        require(commands.size <= MAX_COMMANDS)
        val array =
            JSONArray(
                commands.map { command ->
                    JSONObject()
                        .put("serverId", command.serverId)
                        .put("requestId", command.requestId)
                        .put("serverEpoch", command.serverEpoch)
                        .put("operationId", command.operationId)
                        .put("pairedDeviceId", command.pairedDeviceId)
                        .put("cancellationRequested", command.cancellationRequested)
                        .put("status", command.status.wire)
                        .put("resolved", command.resolved)
                },
            )
        val bytes = array.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".new")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    companion object {
        const val MAX_COMMANDS = 100
        private const val MAX_BYTES = 65536
    }
}
