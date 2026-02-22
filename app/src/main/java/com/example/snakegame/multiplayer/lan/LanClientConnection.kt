package com.example.snakegame.multiplayer.lan

import com.example.snakegame.multiplayer.net.GamePacket
import com.example.snakegame.multiplayer.net.GameSyncChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class LanClientConnection(
    private val socket: Socket,
    private val scope: CoroutineScope,
) : GameSyncChannel {
    private val incoming = MutableSharedFlow<GamePacket>(extraBufferCapacity = 16)
    private val writer = PrintWriter(socket.getOutputStream(), true)
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    private var listenJob: Job? = null

    val remoteAddress: String = socket.inetAddress.hostAddress ?: "unknown"

    init {
        listenJob =
            scope.launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        GamePacket.fromJsonString(line)?.let { incoming.emit(it) }
                    }
                } catch (_: Exception) {
                } finally {
                    close()
                }
            }
    }

    override suspend fun send(packet: GamePacket) {
        val message = packet.toJsonString()
        withContext(Dispatchers.IO) { kotlin.runCatching { writer.println(message) } }
    }

    override fun incomingPackets(): Flow<GamePacket> = incoming.asSharedFlow()

    override suspend fun close() {
        listenJob?.cancel()
        listenJob = null
        kotlin.runCatching { reader.close() }
        kotlin.runCatching { writer.close() }
        kotlin.runCatching { socket.close() }
    }
}
