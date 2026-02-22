package com.example.snakegame.multiplayer.lan

import com.example.snakegame.multiplayer.net.GamePacket
import com.example.snakegame.multiplayer.net.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.ServerSocket

class LanSocketServer(
    private val scope: CoroutineScope,
    private val port: Int = 0,
    private val onClientConnected: suspend (LanClientConnection) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var startError: Throwable? = null

    val localPort: Int
        get() = serverSocket?.localPort ?: -1

    fun start() {
        stop()
        startError = null
        runCatching {
            ServerSocket(port).also { socket ->
                socket.reuseAddress = true
                serverSocket = socket
                acceptJob =
                    scope.launch(Dispatchers.IO) {
                        try {
                            while (true) {
                                val client = socket.accept()
                                val connection = LanClientConnection(client, scope)
                                onClientConnected(connection)
                            }
                        } catch (_: Exception) {
                        } finally {
                            stop()
                        }
                    }
            }
        }.onFailure { e ->
            startError = e
            serverSocket = null
        }
    }

    fun hasError(): Boolean = startError != null

    fun getError(): Throwable? = startError

    @Suppress("UNUSED")
    suspend fun broadcastRoomSnapshot(
        roomJson: JSONObject,
        connections: List<LanClientConnection>,
    ) {
        val packet =
            GamePacket(
                type = PacketType.ROOM_INFO,
                payload = roomJson,
            )
        connections.forEach { connection -> connection.send(packet) }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
