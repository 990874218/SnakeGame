package com.example.snakegame.multiplayer.net

import kotlinx.coroutines.flow.Flow

/**
 * 联机同步通道抽象。无论是蓝牙还是局域网，均可实现此接口与游戏逻辑层解耦。
 */
interface GameSyncChannel {
    suspend fun send(packet: GamePacket)
    fun incomingPackets(): Flow<GamePacket>
    suspend fun close()
}

