package com.example.snakegame.multiplayer.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BluetoothP2PManager(
    private val adapter: BluetoothAdapter?,
    private val scope: CoroutineScope,
) {
    private val serviceUuid: UUID = UUID.fromString("8b2b3d0e-7df9-4a79-9b8a-4d85fdafc3b7")
    private val serviceName = "SnakeGameP2P"

    private var serverSocket: BluetoothServerSocket? = null
    private var listenJob: Job? = null

    private val _events = MutableSharedFlow<BluetoothEvent>(extraBufferCapacity = 8)
    val events: Flow<BluetoothEvent> = _events

    private val connections = mutableMapOf<String, BluetoothSocket>()

    suspend fun startHost(roomInfo: RoomInfoSnapshot) {
        stop()
        listenJob =
            scope.launch(Dispatchers.IO) {
                try {
                    serverSocket = adapter?.listenUsingInsecureRfcommWithServiceRecord(serviceName, serviceUuid)
                    _events.emit(BluetoothEvent.RoleChanged(BluetoothRole.Host, roomInfo))
                    while (true) {
                        val socket = serverSocket?.accept() ?: break
                        connections[socket.remoteDevice.address] = socket
                        _events.emit(BluetoothEvent.ClientConnected(socket.remoteDevice))
                    }
                } catch (e: Exception) {
                    _events.emit(BluetoothEvent.Error(e.message ?: "蓝牙监听失败"))
                }
            }
    }

    suspend fun connectAsClient(device: BluetoothDevice) {
        stop()
        scope.launch(Dispatchers.IO) {
            runCatching {
                val socket = device.createInsecureRfcommSocketToServiceRecord(serviceUuid)
                adapter?.cancelDiscovery()
                socket.connect()
                connections[device.address] = socket
                _events.emit(BluetoothEvent.RoleChanged(BluetoothRole.Client(device.address), null))
                _events.emit(BluetoothEvent.ConnectedToHost(device))
            }.onFailure { e ->
                _events.emit(BluetoothEvent.Error(e.message ?: "连接 Host 失败"))
            }
        }
    }

    fun activeSockets(): List<BluetoothSocket> = connections.values.toList()

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        connections.values.forEach { runCatching { it.close() } }
        connections.clear()
    }
}

sealed interface BluetoothEvent {
    data class RoleChanged(
        val role: BluetoothRole,
        val snapshot: RoomInfoSnapshot?,
    ) : BluetoothEvent

    data class ClientConnected(
        val device: BluetoothDevice,
    ) : BluetoothEvent

    data class ConnectedToHost(
        val host: BluetoothDevice,
    ) : BluetoothEvent

    data class Error(
        val message: String,
    ) : BluetoothEvent
}

typealias RoomInfoSnapshot = com.example.snakegame.multiplayer.room.RoomInfo
