package com.example.snakegame.multiplayer.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.snakegame.multiplayer.room.RoomInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

class BluetoothController(
    private val context: Context,
) {
    data class ConnectionState(
        val connected: Boolean = false,
        val isHost: Boolean? = null,
        val deviceName: String? = null,
        val error: String? = null,
        val roomInfo: RoomInfo? = null, // 房间信息
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val adapter: BluetoothAdapter? =
        run {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            mgr.adapter
        }

    private val p2pManager = BluetoothP2PManager(adapter, scope)
    private val syncBridge = BluetoothSyncBridge(BluetoothRole.Host, scope)

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var writer: PrintWriter? = null

    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val discoveryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            val list = _discoveredDevices.value.toMutableList()
                            if (list.none { exist -> exist.address == device.address }) {
                                list.add(device)
                                _discoveredDevices.value = list
                            }
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _isDiscovering.value = false
                        unregisterReceiverSafe()
                    }
                }
            }
        }

    private val uuid: UUID = UUID.fromString("8b2b3d0e-7df9-4a79-9b8a-4d85fdafc3b7")
    private val serviceName = "SnakeGameBT"

    // 当前房间信息
    private var currentRoomInfo: RoomInfo? = null

    // 设置房间信息
    fun setRoomInfo(roomInfo: RoomInfo) {
        currentRoomInfo = roomInfo
        _state.value = _state.value.copy(roomInfo = roomInfo)
    }

    // 获取当前房间信息
    fun getRoomInfo(): RoomInfo? = currentRoomInfo

    // 从设备创建房间信息（用于加入房间时）
    fun createRoomInfoFromDevice(
        device: BluetoothDevice,
        roomName: String,
        password: String? = null,
    ): RoomInfo {
        val deviceName = try {
            device.name ?: "未知设备"
        } catch (e: SecurityException) {
            "未知设备"
        }
        return RoomInfo(
            id = device.address,
            name = roomName,
            connectionType = RoomInfo.ConnectionType.BLUETOOTH,
            hostAddress = device.address,
            hostName = deviceName,
            maxPlayers = 4,
            currentPlayers = 1,
            allowWallPass = false,
            password = password,
            hasPassword = !password.isNullOrBlank(),
            port = null,
        )
    }

    fun startDiscovery(): Boolean {
        val bt = adapter ?: return false
        try {
            if (!bt.isEnabled) {
                _state.value = _state.value.copy(error = "蓝牙未开启")
                return false
            }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(error = "需要蓝牙权限")
            return false
        }
        try {
            unregisterReceiverSafe()
            _discoveredDevices.value = emptyList()
            val filter =
                IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
            context.registerReceiver(discoveryReceiver, filter)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message)
        }
        try {
            if (bt.isDiscovering) {
                bt.cancelDiscovery()
            }
            val started = bt.startDiscovery()
            _isDiscovering.value = started
            if (!started) {
                unregisterReceiverSafe()
            }
            return started
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(error = "需要蓝牙权限")
            unregisterReceiverSafe()
            return false
        }
    }

    fun stopDiscovery() {
        unregisterReceiverSafe()
        try {
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            // 忽略权限错误
        }
        _isDiscovering.value = false
    }

    fun startHost(roomInfo: RoomInfo? = null) {
        stop()
        currentRoomInfo = roomInfo
        _state.value = ConnectionState(connected = false, isHost = true, roomInfo = roomInfo)
        scope.launch {
            try {
                val tmp = adapter?.listenUsingInsecureRfcommWithServiceRecord(serviceName, uuid)
                serverSocket = tmp
                val client = tmp?.accept() // 阻塞到有客户端连接
                if (client != null) {
                    socket = client
                    initIoAndListen(client, isHost = true)
                } else {
                    _state.value =
                        _state.value.copy(error = "连接被取消", isHost = true, roomInfo = roomInfo)
                }
            } catch (e: SecurityException) {
                _state.value =
                    _state.value.copy(error = "需要蓝牙权限", roomInfo = roomInfo)
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(error = e.message ?: "Host failed", roomInfo = roomInfo)
            }
        }
        scope.launch {
            try {
                val hostAddress = try {
                    adapter?.address ?: "unknown"
                } catch (e: SecurityException) {
                    "unknown"
                }
                val hostName = try {
                    adapter?.name ?: "Host"
                } catch (e: SecurityException) {
                    "Host"
                }
                p2pManager.startHost(
                    roomInfo ?: RoomInfo(
                        id = UUID.randomUUID().toString(),
                        name = "蓝牙房间",
                        connectionType = RoomInfo.ConnectionType.BLUETOOTH,
                        hostAddress = hostAddress,
                        hostName = hostName,
                        maxPlayers = roomInfo?.maxPlayers ?: 4,
                        currentPlayers = 1,
                        allowWallPass = roomInfo?.allowWallPass ?: false,
                    ),
                )
            } catch (e: Exception) {
                // 忽略错误
            }
        }
    }

    fun connectTo(device: BluetoothDevice) {
        stop()
        val deviceName = try {
            device.name
        } catch (e: SecurityException) {
            null
        }
        _state.value = ConnectionState(connected = false, isHost = false, deviceName = deviceName)
        scope.launch {
            try {
                val client = device.createInsecureRfcommSocketToServiceRecord(uuid)
                try {
                    adapter?.cancelDiscovery()
                } catch (e: SecurityException) {
                    // 忽略权限错误
                }
                client.connect()
                socket = client
                initIoAndListen(client, isHost = false)
            } catch (e: SecurityException) {
                _state.value = _state.value.copy(error = "需要蓝牙权限")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Connect failed")
            }
        }
    }

    private fun initIoAndListen(
        sock: BluetoothSocket,
        isHost: Boolean,
    ) {
        try {
            val remoteDevice = sock.remoteDevice
            scope.launch {
                try {
                    p2pManager.connectAsClient(remoteDevice)
                } catch (e: Exception) {
                    // 忽略连接错误，继续使用 socket
                }
            }
            writer = PrintWriter(sock.outputStream, true)
            val reader = BufferedReader(InputStreamReader(sock.inputStream))
            val remoteName = try {
                remoteDevice?.name
            } catch (e: SecurityException) {
                null
            }
            _state.value =
                _state.value.copy(connected = true, isHost = isHost, deviceName = remoteName)

            scope.launch {
                while (true) {
                    val line = reader.readLine() ?: break
                    onMessage(line)
                }
                _state.value = _state.value.copy(connected = false)
            }
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(error = "需要蓝牙权限")
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "IO failed")
        }
    }

    fun send(line: String) {
        writer?.println(line)
    }

    private fun onMessage(line: String) {
        scope.launch {
            _incomingMessages.emit(line)
        }
    }

    fun bondedDevices(): Set<BluetoothDevice> {
        return try {
            adapter?.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            emptySet()
        }
    }

    fun stop() {
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        writer = null
        socket = null
        serverSocket = null
        currentRoomInfo = null
        _state.value = ConnectionState()
        stopDiscovery()
        _incomingMessages.resetReplayCache()
    }

    private fun unregisterReceiverSafe() {
        runCatching { context.unregisterReceiver(discoveryReceiver) }
    }
}
