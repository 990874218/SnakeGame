package com.example.snakegame.multiplayer.bluetooth

import com.example.snakegame.multiplayer.net.GamePacket
import com.example.snakegame.multiplayer.net.GameSyncChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap

class BluetoothSyncBridge(
    private val role: BluetoothRole,
    private val scope: CoroutineScope,
) : GameSyncChannel {
    private val incoming = MutableSharedFlow<GamePacket>(extraBufferCapacity = 16)
    private val writers = ConcurrentHashMap<String, PrintWriter>()

    fun attachSocket(
        address: String,
        socket: android.bluetooth.BluetoothSocket,
    ) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream))
        val writer = PrintWriter(socket.outputStream, true)
        writers[address] = writer
        scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    GamePacket.fromJsonString(line)?.let { incoming.emit(it) }
                }
            } catch (_: Exception) {
            } finally {
                writers.remove(address)
                kotlin.runCatching { socket.close() }
            }
        }
    }

    override suspend fun send(packet: GamePacket) {
        val data = packet.toJsonString()
        withContext(Dispatchers.IO) {
            if (role is BluetoothRole.Host) {
                writers.values.forEach { writer -> kotlin.runCatching { writer.println(data) } }
            } else {
                writers.values.firstOrNull()?.let { writer -> kotlin.runCatching { writer.println(data) } }
            }
        }
    }

    override fun incomingPackets(): Flow<GamePacket> = incoming.asSharedFlow()

    override suspend fun close() {
        writers.values.forEach { writer -> kotlin.runCatching { writer.close() } }
        writers.clear()
    }
}
