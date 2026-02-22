package com.example.snakegame.multiplayer.room

/**
 * 房间信息
 * 通用的房间数据结构，可用于蓝牙和局域网模式
 */
data class RoomInfo(
    val id: String,
    val name: String,
    val connectionType: ConnectionType,
    val hostAddress: String,
    val hostName: String,
    val maxPlayers: Int = 4,
    val currentPlayers: Int = 1,
    val allowWallPass: Boolean = false,
    val password: String? = null,
    val hasPassword: Boolean = false,
    val port: Int? = null,
) {
    val isPasswordProtected: Boolean = hasPassword || !password.isNullOrBlank()

    enum class ConnectionType {
        BLUETOOTH,
        LAN,
    }
}
