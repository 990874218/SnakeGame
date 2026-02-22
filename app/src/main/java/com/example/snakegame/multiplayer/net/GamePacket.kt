package com.example.snakegame.multiplayer.net

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class PacketType {
    ROOM_INFO,
    PLAYER_JOIN,
    PLAYER_STATE,
    FOOD_STATE,
    PLAYER_ELIMINATED,
    HEARTBEAT,
    CUSTOM,
}

/**
    * 统一的联机数据包定义。
    *
    * 为了保持实现简单，这里基于 org.json.JSONObject 进行序列化/反序列化，
    * 在后续阶段如果需要接入 Protobuf 或 kotlinx.serialization，仅需替换 toJsonString / fromJsonString 即可。
    */
data class GamePacket(
    val type: PacketType,
    val payload: JSONObject,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toJsonString(): String =
        JSONObject().apply {
            put("type", type.name)
            put("payload", payload)
            put("timestamp", timestamp)
        }.toString()

    companion object {
        fun fromJsonString(json: String): GamePacket? {
            return runCatching {
                val obj = JSONObject(json)
                val type = PacketType.valueOf(obj.getString("type"))
                val payloadRaw = obj.get("payload")
                val payload =
                    when (payloadRaw) {
                        is JSONObject -> payloadRaw
                        is JSONArray -> JSONObject().put("data", payloadRaw)
                        else -> JSONObject(payloadRaw.toString())
                    }
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                GamePacket(type, payload, timestamp)
            }.getOrNull()
        }
    }
}

