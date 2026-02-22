package com.example.snakegame.multiplayer.lan

import android.app.Application
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.snakegame.multiplayer.net.GamePacket
import com.example.snakegame.multiplayer.net.PacketType
import com.example.snakegame.multiplayer.room.RoomCreationResult
import com.example.snakegame.multiplayer.room.RoomInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

data class LanLobbyUiState(
    val isHosting: Boolean = false,
    val isDiscovering: Boolean = false,
    val hostedRoom: RoomInfo? = null,
    val availableRooms: List<RoomInfo> = emptyList(),
    val connectedClients: List<String> = emptyList(),
    val connectedRoom: RoomInfo? = null,
    val errorMessage: String? = null,
)

private const val ROOM_ATTR_KEY = "meta"
private val HEARTBEAT_INTERVAL = 3.seconds

class LanLobbyViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val nsdHelper = NsdHelper(application.applicationContext, viewModelScope)
    private var socketServer: LanSocketServer? = null
    private val hostConnections = mutableListOf<LanClientConnection>()
    private var clientConnection: LanClientConnection? = null
    private var hostedServiceName: String? = null

    private val _uiState = MutableStateFlow(LanLobbyUiState())
    val uiState: StateFlow<LanLobbyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            nsdHelper.services.collectLatest { services ->
                val rooms =
                    services
                        .mapNotNull { info -> info.toRoomInfo() }
                        .filterNot { hostedServiceName != null && it.id == hostedServiceName }
                _uiState.update { it.copy(availableRooms = rooms) }
            }
        }
        viewModelScope.launch {
            nsdHelper.errors.collect { message ->
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
        // 作为 Host 定时发送心跳，便于客户端检测房间存活
        viewModelScope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL)
                val room = _uiState.value.hostedRoom ?: continue
                val packet =
                    GamePacket(
                        type = PacketType.HEARTBEAT,
                        payload = JSONObject().apply { put("roomId", room.id) },
                    )
                // 在协程中发送心跳并移除失败的连接
                val failedConnections = mutableListOf<LanClientConnection>()
                hostConnections.forEach { connection ->
                    try {
                        connection.send(packet)
                    } catch (e: Exception) {
                        failedConnections.add(connection)
                    }
                }
                hostConnections.removeAll(failedConnections)
                _uiState.update { it.copy(connectedClients = hostConnections.map { c -> c.remoteAddress }) }
            }
        }
    }

    fun startDiscovery() {
        nsdHelper.startDiscovery()
        _uiState.update { it.copy(isDiscovering = true) }
    }

    fun stopDiscovery() {
        nsdHelper.stopDiscovery()
        _uiState.update { it.copy(isDiscovering = false) }
    }

    fun hostRoom(
        hostName: String,
        creation: RoomCreationResult,
    ) {
        if (socketServer != null) return

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val roomId = "lan-${UUID.randomUUID()}"
                val localHostAddress = resolveLocalHostName()

                if (localHostAddress.isBlank() || localHostAddress == "127.0.0.1") {
                    _uiState.update { it.copy(errorMessage = "无法获取本地网络地址，请确保设备已连接到WiFi") }
                    return@launch
                }

                val roomInfo =
                    RoomInfo(
                        id = roomId,
                        name = creation.name,
                        connectionType = RoomInfo.ConnectionType.LAN,
                        hostAddress = localHostAddress,
                        hostName = hostName,
                        maxPlayers = creation.maxPlayers,
                        currentPlayers = 1,
                        allowWallPass = creation.allowWallPass,
                        password = creation.password,
                        hasPassword = !creation.password.isNullOrBlank(),
                        port = 0,
                    )

                val server =
                    LanSocketServer(
                        scope = viewModelScope,
                        port = 0,
                        onClientConnected = { connection ->
                            hostConnections.add(connection)
                            _uiState.update {
                                it.copy(
                                    connectedClients = hostConnections.map { conn -> conn.remoteAddress },
                                    hostedRoom = it.hostedRoom?.copy(currentPlayers = hostConnections.size + 1),
                                )
                            }
                            // 初始快照
                            val snapshot = roomInfoSnapshot(roomInfo.copy(currentPlayers = hostConnections.size + 1))
                            viewModelScope.launch { connection.send(snapshot) }
                        },
                    )
                server.start()

                // 检查服务器是否启动成功
                if (server.hasError()) {
                    val error = server.getError()
                    _uiState.update {
                        it.copy(errorMessage = "启动服务器失败: ${error?.message ?: "未知错误"}")
                    }
                    return@launch
                }

                val actualPort = server.localPort
                if (actualPort <= 0) {
                    _uiState.update { it.copy(errorMessage = "服务器端口获取失败") }
                    return@launch
                }

                val finalRoom = roomInfo.copy(port = actualPort)
                val attributes = mapOf(ROOM_ATTR_KEY to finalRoom.toAttributeBytes())

                nsdHelper.registerService(
                    port = actualPort,
                    serviceName = finalRoom.name.take(20) + "#" + roomId.takeLast(4),
                    attributes = attributes,
                ) { registered ->
                    hostedServiceName = registered.serviceName
                }

                socketServer = server
                _uiState.update { it.copy(isHosting = true, hostedRoom = finalRoom, errorMessage = null) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(errorMessage = "创建房间失败: ${e.message ?: "未知错误"}")
                }
            }
        }
    }

    fun stopHosting() {
        socketServer?.stop()
        hostConnections.forEach { connection -> viewModelScope.launch { connection.close() } }
        hostConnections.clear()
        socketServer = null
        hostedServiceName = null
        nsdHelper.unregisterService()
        _uiState.update {
            it.copy(
                isHosting = false,
                hostedRoom = null,
                connectedClients = emptyList(),
            )
        }
    }

    fun connectTo(
        room: RoomInfo,
        password: String? = null,
    ) {
        val host = room.hostAddress
        val port = room.port ?: return
        if (room.hasPassword && room.password != password && !room.password.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "密码错误") }
            return
        }

        disconnectClient()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                Socket(InetAddress.getByName(host), port)
            }.onSuccess { socket ->
                val connection = LanClientConnection(socket, viewModelScope)
                connection.incomingPackets().collectLatest { packet ->
                    when (packet.type) {
                        PacketType.ROOM_INFO -> {
                            val updated = packet.payload.toRoomInfo(room)
                            _uiState.update { it.copy(connectedRoom = updated, errorMessage = null) }
                        }
                        PacketType.HEARTBEAT -> Unit
                        else -> Unit
                    }
                }
                clientConnection = connection
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message ?: "连接失败") }
            }
        }
    }

    fun disconnectClient() {
        viewModelScope.launch {
            clientConnection?.close()
            clientConnection = null
            _uiState.update { it.copy(connectedRoom = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopHosting()
        disconnectClient()
        nsdHelper.tearDown()
    }

    private fun resolveLocalHostName(): String =
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.asSequence() ?: return@runCatching null
            interfaces
                .flatMap { network -> network.inetAddresses.asSequence() }
                .firstOrNull { address -> !address.isLoopbackAddress && address is Inet4Address }
                ?.hostAddress
        }.getOrNull() ?: InetAddress.getLoopbackAddress().hostAddress ?: "127.0.0.1"

    private fun roomInfoSnapshot(info: RoomInfo): GamePacket =
        GamePacket(
            type = PacketType.ROOM_INFO,
            payload =
                JSONObject().apply {
                    put("id", info.id)
                    put("name", info.name)
                    put("hostName", info.hostName)
                    put("maxPlayers", info.maxPlayers)
                    put("currentPlayers", info.currentPlayers)
                    put("allowWallPass", info.allowWallPass)
                    put("hasPassword", info.hasPassword)
                    put("port", info.port ?: 0)
                    put("hostAddress", info.hostAddress)
                },
        )

    private fun RoomInfo.toAttributeBytes(): ByteArray {
        // NSD 属性限制：键名 + 值必须 < 255 字节
        // 只存储最必要的信息，其他信息可以通过连接后获取
        val json = JSONObject().apply {
            put("id", id)
            put("port", port ?: 0)
            put("hostAddress", hostAddress)
            // 可选：存储一些关键信息，但要确保总长度不超过限制
            put("maxP", maxPlayers) // 缩短键名
            put("pwd", if (hasPassword) 1 else 0) // 只存储是否有密码，不存储密码本身
        }
        val data = json.toString().toByteArray(Charsets.UTF_8)
        
        // 检查长度（键名 "meta" 约 4 字节 + Base64 编码会增加约 33% 长度）
        // 实际限制：原始数据应该 < 190 字节（Base64 编码后约 253 字节）
        val maxDataSize = 190
        return if (data.size > maxDataSize) {
            // 如果还是太大，只保留最核心的信息
            JSONObject().apply {
                put("id", id)
                put("port", port ?: 0)
                put("hostAddress", hostAddress)
            }.toString().toByteArray(Charsets.UTF_8)
        } else {
            data
        }
    }

    private fun NsdServiceInfo.toRoomInfo(): RoomInfo? {
        val attrString = attributes?.get(ROOM_ATTR_KEY) ?: return null
        return runCatching {
            // NsdHelper 中已经将 ByteArray Base64 编码为 String，所以这里需要解码
            val decodedBytes = android.util.Base64.decode(attrString, android.util.Base64.NO_WRAP)
            val json = JSONObject(String(decodedBytes, Charsets.UTF_8))
            val hostAddressResolved = host?.hostAddress ?: json.optString("hostAddress")
            
            // 从 serviceName 提取房间名称（格式：房间名#ID后4位）
            val roomName = serviceName.substringBeforeLast("#").takeIf { it.isNotBlank() } ?: serviceName
            
            RoomInfo(
                id = json.optString("id", serviceName),
                name = roomName,
                connectionType = RoomInfo.ConnectionType.LAN,
                hostAddress = hostAddressResolved,
                hostName = serviceName, // 使用 serviceName 作为 hostName
                maxPlayers = json.optInt("maxP", 4), // 使用缩短的键名
                currentPlayers = 1, // 默认值，实际值需要连接后获取
                allowWallPass = false, // 默认值，实际值需要连接后获取
                password = null,
                hasPassword = json.optInt("pwd", 0) == 1, // 使用缩短的键名
                port = json.optInt("port", port),
            )
        }.getOrNull()
    }

    private fun JSONObject.toRoomInfo(base: RoomInfo): RoomInfo =
        base.copy(
            currentPlayers = optInt("currentPlayers", base.currentPlayers),
            allowWallPass = optBoolean("allowWallPass", base.allowWallPass),
        )
}
